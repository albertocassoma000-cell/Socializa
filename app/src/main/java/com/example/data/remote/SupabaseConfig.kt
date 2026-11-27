package com.example.data.remote

import com.example.BuildConfig

object SupabaseConfig {
    private fun sanitize(raw: String): String =
        raw.trim().removeSurrounding("\"").removeSurrounding("'").trim()

    val supabaseUrl: String
        get() = sanitize(BuildConfig.SUPABASE_URL).removeSuffix("/")

    val supabaseAnonKey: String
        get() = sanitize(BuildConfig.SUPABASE_ANON_KEY)

    val liveKitWsUrl: String
        get() = sanitize(BuildConfig.LIVEKIT_WS_URL)

    val streamProviderBaseUrl: String
        get() = sanitize(BuildConfig.STREAM_PROVIDER_BASE_URL)

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") &&
            !supabaseUrl.contains("your-project-ref") &&
            supabaseAnonKey.isNotBlank() &&
            !supabaseAnonKey.startsWith("YOUR_")

    val isLiveKitConfigured: Boolean
        get() = liveKitWsUrl.startsWith("wss://") && !liveKitWsUrl.contains("your-livekit-host")

    val isStreamConfigured: Boolean
        get() = streamProviderBaseUrl.startsWith("https://") && !streamProviderBaseUrl.contains("customer-stream")

    val supabaseHost: String
        get() = if (isSupabaseConfigured) {
            supabaseUrl.removePrefix("https://").substringBefore("/")
        } else {
            "não configurado"
        }
}
