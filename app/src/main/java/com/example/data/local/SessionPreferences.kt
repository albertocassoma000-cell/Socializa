package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "meu_pais_session_prefs")

class SessionPreferences(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USER_BIO = stringPreferencesKey("user_bio")
        val USER_PROVINCE = stringPreferencesKey("user_province")
        val USER_COUNTRY = stringPreferencesKey("user_country")
        val USER_WEBSITE = stringPreferencesKey("user_website")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        fun verifiedUntilMs(uid: String) = longPreferencesKey("verified_until_ms_$uid")
        fun adsWatched(uid: String) = intPreferencesKey("verification_ads_watched_$uid")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }

    val fcmTokenFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.FCM_TOKEN]
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun saveProfileMetadata(bio: String, province: String, country: String, website: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_BIO] = bio
            prefs[Keys.USER_PROVINCE] = province
            prefs[Keys.USER_COUNTRY] = country
            prefs[Keys.USER_WEBSITE] = website
        }
    }

    suspend fun getSavedProvince(): String = context.dataStore.data.first()[Keys.USER_PROVINCE] ?: "Luanda"
    suspend fun getSavedBio(): String? = context.dataStore.data.first()[Keys.USER_BIO]

    suspend fun getVerifiedUntilMs(uid: String): Long =
        context.dataStore.data.first()[Keys.verifiedUntilMs(uid)] ?: 0L

    suspend fun saveVerifiedUntilMs(uid: String, untilMs: Long) {
        context.dataStore.edit { it[Keys.verifiedUntilMs(uid)] = untilMs }
    }

    suspend fun getAdsWatchedCount(uid: String): Int =
        context.dataStore.data.first()[Keys.adsWatched(uid)] ?: 0

    suspend fun saveAdsWatchedCount(uid: String, count: Int) {
        context.dataStore.edit { it[Keys.adsWatched(uid)] = count }
    }

    suspend fun getOrCreateDeviceId(): String {
        val current = context.dataStore.data.first()[Keys.DEVICE_ID]
        if (!current.isNullOrBlank()) return current
        val created = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_ID] = created }
        return created
    }

    suspend fun saveFcmToken(token: String) {
        context.dataStore.edit { it[Keys.FCM_TOKEN] = token }
    }
}
