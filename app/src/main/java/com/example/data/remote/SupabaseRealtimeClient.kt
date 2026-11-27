package com.example.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseRealtimeClient(
    private val onRealtimeEvent: (table: String, eventType: String, payloadJson: String) -> Unit
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    fun connect(accessToken: String?) {
        if (!SupabaseConfig.isSupabaseConfigured || accessToken.isNullOrBlank()) return
        val wssBase = SupabaseConfig.supabaseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        val url = "$wssBase/realtime/v1/websocket?apikey=${SupabaseConfig.supabaseAnonKey}&vsn=1.0.0"

        val request = Request.Builder().url(url).build()
        webSocket?.close(1000, "Reconnect")
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val tables = listOf("posts", "comments", "likes", "stories", "messages", "notifications", "follows", "videos")
                val changesArray = JSONArray().apply {
                    tables.forEach { tbl ->
                        put(JSONObject().put("event", "*").put("schema", "public").put("table", tbl))
                    }
                }
                val joinMsg = JSONObject().apply {
                    put("topic", "realtime:public")
                    put("event", "phx_join")
                    put("ref", "1")
                    put("payload", JSONObject().apply {
                        put("config", JSONObject().apply {
                            put("postgres_changes", changesArray)
                        })
                        put("access_token", accessToken)
                    })
                }
                webSocket.send(joinMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val json = JSONObject(text)
                    if (json.optString("event") == "postgres_changes") {
                        val payload = json.optJSONObject("payload")
                        val data = payload?.optJSONObject("data")
                        val table = data?.optString("table") ?: return
                        val eventType = data.optString("type", "UPDATE")
                        onRealtimeEvent(table, eventType, data.toString())
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w("SupabaseRealtime", "Realtime connection closed: ${t.message}")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "SignOut")
        webSocket = null
    }
}
