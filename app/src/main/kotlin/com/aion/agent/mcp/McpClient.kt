package com.aion.agent.mcp

import com.aion.agent.util.AionLogger
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for connecting to external MCP servers.
 * External skills appear alongside local skills in the BM25 router.
 *
 * Per AION_PLAN §15 (Phase 5 Week 24):
 *  - User adds custom MCP server URL + token
 *  - External skills exposed as MINIMAL capability default
 */
@Singleton
class McpClient @Inject constructor(
    private val json: Json,
    private val logger: AionLogger,
) {
    private val client = HttpClient { install(WebSockets) }
    private val connections = mutableMapOf<String, DefaultWebSocketSession>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class ExternalServer(
        val id: String,
        val url: String,
        val token: String,
    )

    /**
     * Connect to an external MCP server.
     * Sends initialize handshake on connect.
     */
    suspend fun connect(server: ExternalServer): Boolean {
        return try {
            client.webSocket(server.url) {
                val initMsg = """{"jsonrpc":"2.0","id":"1","method":"initialize","params":{}}"""
                send(Frame.Text(initMsg))
                connections[server.id] = this
                logger.i(TAG) { "Connected to external MCP: ${server.id}" }
            }
            true
        } catch (t: Throwable) {
            logger.e(TAG, t) { "Failed to connect to ${server.id}" }
            false
        }
    }

    /** Disconnect from an external server. */
    fun disconnect(serverId: String) {
        connections[serverId]?.let { session ->
            scope.launch {
                try { session.close() } catch (_: Exception) {}
            }
        }
        connections.remove(serverId)
    }

    /** Disconnect from all external servers. */
    fun disconnectAll() {
        connections.keys.toList().forEach { disconnect(it) }
    }

    companion object {
        private const val TAG = "McpClient"
    }
}
