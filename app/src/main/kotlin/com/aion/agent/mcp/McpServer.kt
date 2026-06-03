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
import java.util.concurrent.ConcurrentLinkedQueue
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
 *  - Port fallback: 8765 -> 8766 -> 8767 -> 8768
 *  - Max 3 concurrent clients
 *  - Audit log of all MCP actions
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
    private val auditLog = ConcurrentLinkedQueue<AuditEntry>()

    data class ConnectedClient(
        val id: String,
        val ip: String,
        val connectedAt: Long,
    )

    data class AuditEntry(
        val clientId: String,
        val toolName: String,
        val params: String,
        val timestamp: Long,
        val result: String,
    )

    /**
     * Start the MCP server with port fallback.
     * Tries [port] first, then 8766, 8767, 8768 if unavailable.
     */
    fun start(port: Int = 8765, bindAddress: String = "127.0.0.1"): Boolean {
        if (server != null) return true

        val portsToTry = (listOf(port) + listOf(8765, 8766, 8767, 8768)).distinct()

        for (tryPort in portsToTry) {
            try {
                server = embeddedServer(Netty, port = tryPort, host = bindAddress) {
                    install(io.ktor.server.websocket.WebSockets)
                    routing {
                        webSocket("/mcp") {
                            // --- Max connections check ---
                            if (connectedClients.size >= MAX_CONNECTIONS) {
                                close(CloseReason(
                                    CloseReason.Codes.TRY_AGAIN_LATER,
                                    "Server busy (max $MAX_CONNECTIONS clients)"
                                ))
                                return@webSocket
                            }

                            // --- Auth ---
                            val ip = call.request.local.remoteHost
                            val authHeader = call.request.headers["Authorization"] ?: ""
                            val token = authHeader.removePrefix("Bearer ").trim()
                                .ifEmpty { call.request.headers["X-Auth-Token"] ?: "" }
                            if (token.isBlank() || !authManager.validateToken(token, ip)) {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                                return@webSocket
                            }

                            // --- Register client ---
                            val clientId = authManager.generateClientId()
                            connectedClients[clientId] = ConnectedClient(
                                clientId, ip, System.currentTimeMillis()
                            )
                            logger.i(TAG) {
                                "MCP client connected: $clientId from $ip " +
                                    "(${connectedClients.size}/$MAX_CONNECTIONS)"
                            }

                            // --- Message loop ---
                            try {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val requestText = frame.readText()
                                        val response = protocolHandler.handle(
                                            message = requestText,
                                            clientId = clientId,
                                            auditCallback = { toolName, params, result ->
                                                auditLog.add(AuditEntry(
                                                    clientId = clientId,
                                                    toolName = toolName,
                                                    params = params,
                                                    timestamp = System.currentTimeMillis(),
                                                    result = result,
                                                ))
                                            },
                                        )
                                        send(Frame.Text(response))
                                    }
                                }
                            } finally {
                                connectedClients.remove(clientId)
                                logger.i(TAG) {
                                    "MCP client disconnected: $clientId " +
                                        "(${connectedClients.size}/$MAX_CONNECTIONS)"
                                }
                            }
                        }
                    }
                }.start(wait = false)
                logger.i(TAG) { "MCP server started on $bindAddress:$tryPort" }
                return true
            } catch (e: Exception) {
                if (tryPort == portsToTry.last()) {
                    logger.e(TAG, e) { "All MCP ports unavailable (tried $portsToTry)" }
                    return false
                }
                logger.w(TAG) { "Port $tryPort unavailable, trying next port..." }
            }
        }
        return false
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        connectedClients.clear()
        auditLog.clear()
    }

    fun getConnectedClients(): Map<String, ConnectedClient> = connectedClients.toMap()

    /** Return last 100 audit entries. */
    fun getAuditLog(): List<AuditEntry> = auditLog.toList().takeLast(100)

    companion object {
        private const val TAG = "McpServer"
        private const val MAX_CONNECTIONS = 3
    }
}
