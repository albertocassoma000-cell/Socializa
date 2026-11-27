package com.example.services

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.data.local.SessionPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FirebaseTelemetryManager {
    private const val TAG = "FirebaseTelemetry"

    fun isFirebaseInitialized(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    fun initializeComplementaryServices(context: Context, prefs: SessionPreferences) {
        if (!isFirebaseInitialized(context)) {
            Log.i(TAG, "google-services.json não configurado; serviços complementares Firebase em standby.")
            return
        }

        runCatching {
            FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        }

        runCatching {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "reels_autoplay_default" to true,
                    "marketplace_enabled" to true,
                    "max_story_hours" to 24L
                )
            )
            remoteConfig.fetchAndActivate()
        }

        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        prefs.saveFcmToken(token)
                    }
                }
            }
        }
    }

    fun logEvent(context: Context, eventName: String, params: Map<String, String> = emptyMap()) {
        if (!isFirebaseInitialized(context)) return
        runCatching {
            val bundle = Bundle().apply {
                params.forEach { (k, v) -> putString(k, v) }
            }
            FirebaseAnalytics.getInstance(context).logEvent(eventName, bundle)
        }
    }
}
