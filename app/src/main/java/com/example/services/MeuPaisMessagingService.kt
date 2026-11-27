package com.example.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.SessionPreferences
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.SupabaseApiService
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseDeviceTokenUpsertDto
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MeuPaisMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("MeuPaisFCM", "Novo token FCM recebido")
        val prefs = SessionPreferences(applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            prefs.saveFcmToken(token)
            val uid = Firebase.auth.currentUser?.uid
            val firebaseToken = GoogleAuthHelper.getFreshFirebaseIdToken()
            val api = SupabaseApiService.create()
            if (api != null && !uid.isNullOrBlank() && !firebaseToken.isNullOrBlank()) {
                val deviceId = prefs.getOrCreateDeviceId()
                runCatching {
                    api.upsertDevicePushToken(
                        apiKey = SupabaseConfig.supabaseAnonKey,
                        firebaseBearer = "Bearer $firebaseToken",
                        body = SupabaseDeviceTokenUpsertDto(
                            userId = uid,
                            token = token,
                            platform = "android",
                            deviceId = deviceId
                        )
                    )
                }
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: "Nova atividade no Socializa"
        showLocalNotification(title, body)
    }

    private fun showLocalNotification(title: String, body: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "meu_pais_social_events"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Atividade Socializa",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
