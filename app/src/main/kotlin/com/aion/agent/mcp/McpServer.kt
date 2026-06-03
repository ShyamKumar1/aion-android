package com.aion.agent.mcp

import com.aion.agent.util.AionLogger
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device MCP server running via Ktor embedded Netty.
 *
 * Per AION_PLAN §15 (Phase 5):
 *  - Default bind: 127.0.0.1:8765 (localhost only)
 *  - LAN mode: user opt-in to 0.0.0.0
 *  - Token auth via [McpAuthManager]
 *  - Protocol v2025-03-26 via [McpProtocolHandler]
 */
@Singleton
class McpServer @Inject constructor(
    private val protocolHandler: McpProtocolHandler,
    private val authManager: McpAuthManager,
    private val logger: AionLogger,
) {
    private var server: ApplicationEngine? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connectedClients = ConcurrentHashMap<String, ConnectedClient>()

    data class ConnectedClient(
        val id: String,
        val ip: String,
        val connectedAt: Long,
    )

    /**
     * Start the MCP server on [port] and [bindAddress].
     * Default is localhost:8765. Pass 0.0.0.0 for LAN mode.
     */
    fun start(port: Int = 8765, bindAddress: String = "127.0.0.1"): Boolean {
        if (server != null) return true
        return try {
            server = embeddedServer(Netty, port = port, host = bindAddress) {
                install(io.ktor.server.websocket.WebSockets)
                routing {
                    webSocket("/mcp") {
                        val ip = call.request.local.remoteHost
                        val authHeader = call.request.headers["Authorization"] ?: ""
                        val token = authHeader.removePrefix("Bearer ").trim()
                            .ifEmpty { call.request.headers["X-Auth-Token"] ?: "" }
                        if (token.isBlank() || !authManager.validateToken(token, ip)) {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                            return@webSocket
                        }
                        val clientId = authManager.generateClientId()
                        connectedClients[clientId] = ConnectedClient(
                            clientId, ip, System.currentTimeMillis()
                        )
                        logger.i(TAG) { "MCP client connected: $clientId from $ip" }
                        try {
                            for (frame in incoming) {
                                if (frame is Frame.Text) {
                                    val response = protocolHandler.handle(frame.readText(), clientId)
                                    send(Frame.Text(response))
                                }
                            }
                        } finally {
                            connectedClients.remove(clientId)
                            logger.i(TAG) { "MCP client disconnected: $clientId" }
                        }
                    }
                }
            }.start(wait = false)
            logger.i(TAG) { "MCP server started on $bindAddress:$port" }
            true
        } catch (t: Throwable) {
            logger.e(TAG, t) { "Failed to start MCP server" }
            false
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        connectedClients.clear()
    }

    fun getConnectedClients(): Map<String, ConnectedClient> = connectedClients.toMap()

    companion object {
        private const val TAG = "McpServer"
    }
}
