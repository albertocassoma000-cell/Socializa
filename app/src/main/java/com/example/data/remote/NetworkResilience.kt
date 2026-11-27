package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.services.FirebaseTelemetryManager
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class RateLimitExceededException(message: String) : IllegalStateException(message)

class TokenBucketRateLimiter {
    private val history = ConcurrentHashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun checkAndRecord(actionKey: String, maxPerMinute: Int, nowMs: Long = System.currentTimeMillis()) {
        val queue = history.getOrPut(actionKey) { ArrayDeque() }
        val cutoff = nowMs - 60_000L
        while (queue.isNotEmpty() && queue.first() < cutoff) {
            queue.removeFirst()
        }
        if (queue.size >= maxPerMinute) {
            throw RateLimitExceededException(
                "Limite de atividade atingido para $actionKey. Aguarda alguns segundos."
            )
        }
        queue.addLast(nowMs)
    }

    fun resetForTest() {
        history.clear()
    }
}

object NetworkRetry {
    suspend fun <T> withBackoff(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 350L,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastError: Throwable? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                lastError = e
                if (attempt < maxAttempts - 1) {
                    delay(currentDelay)
                    currentDelay *= 2
                }
            }
        }
        throw lastError ?: IOException("Falha de rede após tentativas.")
    }
}

object ErrorMonitor {
    private const val TAG = "MeuPaisErrorMonitor"
    private val recentErrors = ArrayDeque<String>()

    @Synchronized
    fun record(context: Context, screen: String, throwable: Throwable) {
        val entry = "[$screen] ${throwable::class.simpleName}: ${throwable.message}"
        Log.e(TAG, entry, throwable)
        recentErrors.addFirst(entry)
        while (recentErrors.size > 25) {
            recentErrors.removeLast()
        }
        FirebaseTelemetryManager.logEvent(
            context,
            "app_error",
            mapOf("screen" to screen, "message" to (throwable.message ?: "unknown").take(90))
        )
    }

    @Synchronized
    fun recentCount(): Int = recentErrors.size
}
