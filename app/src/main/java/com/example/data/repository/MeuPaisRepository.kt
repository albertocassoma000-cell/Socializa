package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.data.local.CachedPostEntity
import com.example.data.local.CachedProfileEntity
import com.example.data.local.MeuPaisCacheDatabase
import com.example.data.local.SessionPreferences
import com.example.data.model.AppNotification
import com.example.data.model.AuditLogEntry
import com.example.data.model.BackendStatus
import com.example.data.model.ChatMessage
import com.example.data.model.CommunityGroup
import com.example.data.model.ConversationItem
import com.example.data.model.CreatorStats
import com.example.data.model.DeliveryStatus
import com.example.data.model.FeedMode
import com.example.data.model.FeedPost
import com.example.data.model.MarketplaceProduct
import com.example.data.model.MessageType
import com.example.data.model.ModerationReport
import com.example.data.model.NotificationEventType
import com.example.data.model.PostComment
import com.example.data.model.PostType
import com.example.data.model.PostVisibility
import com.example.data.model.StoryItem
import com.example.data.model.TrendingTopic
import com.example.data.model.UserProfile
import com.example.data.model.VideoReel
import com.example.data.model.VideoTelemetry
import com.example.data.remote.ClaimDailyVerifiedBadgeRequest
import com.example.data.remote.ConversationIdRpcRequest
import com.example.data.remote.DirectConversationRpcRequest
import com.example.data.remote.EmailOtpRequestDto
import com.example.data.remote.EnsureFirebaseProfileRequest
import com.example.data.remote.ErrorMonitor
import com.example.data.remote.FirebaseGoogleAuthOutcome
import com.example.data.remote.GoogleAuthHelper
import com.example.data.remote.InterestSignalUpsertDto
import com.example.data.remote.MediaUploadHelper
import com.example.data.remote.NetworkRetry
import com.example.data.remote.PostShareRpcRequest
import com.example.data.remote.RankedFeedRequestDto
import com.example.data.remote.RecommendedVideosRequestDto
import com.example.data.remote.SupabaseAdminModerationDto
import com.example.data.remote.SupabaseApiService
import com.example.data.remote.SupabaseBlockDto
import com.example.data.remote.SupabaseCommentDto
import com.example.data.remote.SupabaseCommentInsertDto
import com.example.data.remote.SupabaseCommentLikeDto
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.SupabaseDeviceTokenUpsertDto
import com.example.data.remote.SupabaseErrorLogInsertDto
import com.example.data.remote.SupabaseFollowDto
import com.example.data.remote.SupabaseGroupMemberDto
import com.example.data.remote.SupabaseLikeRecordDto
import com.example.data.remote.SupabaseMediaInsertDto
import com.example.data.remote.SupabaseMessageDto
import com.example.data.remote.SupabaseMessageInsertDto
import com.example.data.remote.SupabasePostDto
import com.example.data.remote.SupabasePostInsertDto
import com.example.data.remote.SupabasePostViewDto
import com.example.data.remote.SupabaseProductInsertDto
import com.example.data.remote.SupabaseProfileDto
import com.example.data.remote.SupabasePushDispatchDto
import com.example.data.remote.SupabaseRealtimeClient
import com.example.data.remote.SupabaseReportInsertDto
import com.example.data.remote.SupabaseSavedPostRecordDto
import com.example.data.remote.SupabaseStoryInsertDto
import com.example.data.remote.SupabaseStoryViewDto
import com.example.data.remote.SupabaseVideoInsertDto
import com.example.data.remote.SupabaseVideoLikeDto
import com.example.data.remote.SupabaseVideoViewDto
import com.example.data.remote.TokenBucketRateLimiter
import com.example.data.remote.TrendingTopicsRequestDto
import com.example.data.remote.VideoShareRpcRequest
import com.example.domain.algorithm.SocializaAlgorithmEngine
import com.example.services.FirebaseTelemetryManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import android.util.Log
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

sealed class AuthResult {
    data class Success(val profile: UserProfile, val message: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
    data object Cancelled : AuthResult()
}

class MeuPaisRepository(
    private val context: Context,
    val preferences: SessionPreferences,
    private val apiOverride: SupabaseApiService? = null,
    private val tokenProviderOverride: (suspend () -> String?)? = null,
    private val uidOverride: (() -> String?)? = null,
    private val emailSignInOverride: (suspend (email: String, password: String) -> AuthResult)? = null
) {
    private val api: SupabaseApiService? by lazy { apiOverride ?: SupabaseApiService.create() }
    private val cacheDao by lazy { MeuPaisCacheDatabase.getInstance(context).cacheDao() }
    private val rateLimiter = TokenBucketRateLimiter()
    private val interestWeights = ConcurrentHashMap<String, Double>()
    private val recentSubmittedTexts = Collections.synchronizedList(mutableListOf<String>())
    private var realtimeClient: SupabaseRealtimeClient? = null

    private val authRecoveryMutex = Mutex()
    private val cachedErrorBodies = Collections.synchronizedMap(WeakHashMap<Response<*>, String>())
    @Volatile private var cachedExchangedJwt: String? = null
    @Volatile private var exchangedJwtExpiresAtMs: Long = 0L
    @Volatile private var exchangedForUid: String? = null
    @Volatile private var exchangeEndpointUnavailable: Boolean = false
    @Volatile private var lastExchangeErrorSummary: String? = null
    @Volatile private var lastSupabaseAuthDiagnostic: String? = null

    private fun apiKey(): String = SupabaseConfig.supabaseAnonKey.ifBlank { "test-anon-key" }

    fun currentAuthenticatedUid(): String? =
        uidOverride?.invoke() ?: runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

    fun hasAuthRejection(): Boolean = !lastSupabaseAuthDiagnostic.isNullOrBlank()

    fun resetAuthRecoveryForRetry() {
        cachedExchangedJwt = null
        exchangedJwtExpiresAtMs = 0L
        exchangedForUid = null
        exchangeEndpointUnavailable = false
        lastExchangeErrorSummary = null
        lastSupabaseAuthDiagnostic = null
    }

    private suspend fun getToken(forceRefresh: Boolean = false): String? {
        tokenProviderOverride?.let { return it.invoke() }
        return GoogleAuthHelper.getFreshFirebaseIdToken(forceRefresh)
    }

    private suspend fun getEffectiveBearerToken(forceRefreshFirebase: Boolean = false): String? {
        val uid = currentAuthenticatedUid()
        val cached = cachedExchangedJwt
        if (!forceRefreshFirebase &&
            uid != null &&
            uid == exchangedForUid &&
            !cached.isNullOrBlank() &&
            System.currentTimeMillis() < exchangedJwtExpiresAtMs - 60_000L
        ) {
            return cached
        }
        return getToken(forceRefreshFirebase)
    }

    private fun clearAuthRecoveryState() {
        cachedExchangedJwt = null
        exchangedJwtExpiresAtMs = 0L
        exchangedForUid = null
        exchangeEndpointUnavailable = false
        lastExchangeErrorSummary = null
        lastSupabaseAuthDiagnostic = null
    }

    private suspend fun exchangeFirebaseSessionWithMutex(
        service: SupabaseApiService,
        firebaseToken: String
    ): String? = authRecoveryMutex.withLock {
        val uid = currentAuthenticatedUid() ?: return@withLock null
        val alreadyCached = cachedExchangedJwt
        if (uid == exchangedForUid &&
            !alreadyCached.isNullOrBlank() &&
            System.currentTimeMillis() < exchangedJwtExpiresAtMs - 60_000L
        ) {
            return@withLock alreadyCached
        }
        if (exchangeEndpointUnavailable) return@withLock null

        val key = apiKey()
        val gatewayBearer = if (key.startsWith("eyJ")) "Bearer $key" else "Bearer $firebaseToken"
        val exchangeRes = runCatching {
            service.exchangeFirebaseSession(
                apiKey = key,
                firebaseBearer = gatewayBearer,
                firebaseToken = firebaseToken,
                body = com.example.data.remote.FirebaseSessionExchangeRequest(firebaseToken)
            )
        }.onFailure { err ->
            lastExchangeErrorSummary = "firebase-session falhou: ${err.message}"
            Log.w("MeuPaisAuth", "exchangeFirebaseSession network failure", err)
        }.getOrNull() ?: return@withLock null

        if (exchangeRes.isSuccessful) {
            val dto = exchangeRes.body()
            if (dto != null && dto.accessToken.isNotBlank()) {
                cachedExchangedJwt = dto.accessToken
                exchangedJwtExpiresAtMs = System.currentTimeMillis() + (dto.expiresIn.coerceAtLeast(120L) * 1000L)
                exchangedForUid = uid
                lastExchangeErrorSummary = null
                lastSupabaseAuthDiagnostic = null
                return@withLock dto.accessToken
            }
        } else {
            val exDetail = extractHttpErrorDetail(exchangeRes)
            lastExchangeErrorSummary = "firebase-session HTTP ${exchangeRes.code()}${if (exDetail.isNotBlank()) ": $exDetail" else ""}"
            Log.w("MeuPaisAuth", "exchangeFirebaseSession rejected: $lastExchangeErrorSummary")
            if (exchangeRes.code() == 404) {
                exchangeEndpointUnavailable = true
            }
        }
        null
    }

    private suspend fun <T> executeWithAuthRetry(
        service: SupabaseApiService,
        initialTokenOverride: String? = null,
        operation: suspend (bearerToken: String) -> Response<T>
    ): Response<T> {
        val firstToken = getEffectiveBearerToken(false)
            ?: initialTokenOverride
            ?: throw IllegalStateException("Sessão Firebase inválida.")

        var response = NetworkRetry.withBackoff { operation(firstToken) }
        if (response.code() != 401) {
            if (response.isSuccessful) {
                lastSupabaseAuthDiagnostic = null
            }
            return response
        }

        val firstErrBody = readErrorBodyOnce(response)
        val isKeyTypeRejection = firstErrBody.contains("No suitable key", ignoreCase = true) ||
            firstErrBody.contains("wrong key type", ignoreCase = true) ||
            firstErrBody.contains("JWSError", ignoreCase = true)

        // Token rejeitado com 401: invalidar JWT em cache e tentar renovar Firebase ID Token (exceto rejeição de tipo de chave RS256)
        if (firstToken == cachedExchangedJwt) {
            cachedExchangedJwt = null
            exchangedJwtExpiresAtMs = 0L
        }

        val refreshedFb = if (!isKeyTypeRejection) {
            getToken(forceRefresh = true)?.also { candidate ->
                if (candidate != firstToken) {
                    response = NetworkRetry.withBackoff { operation(candidate) }
                    if (response.code() != 401) {
                        if (response.isSuccessful) {
                            lastSupabaseAuthDiagnostic = null
                        }
                        return response
                    }
                }
            }
        } else {
            null
        }

        // Se ainda devolve 401 (ex.: JWT Firebase sem claim role='authenticated' ou chave RS256 não configurada), tentar troca via Edge Function
        val rawFirebaseToken = refreshedFb ?: initialTokenOverride ?: getToken(forceRefresh = false) ?: firstToken
        if (rawFirebaseToken.isNotBlank()) {
            val exchangedJwt = exchangeFirebaseSessionWithMutex(service, rawFirebaseToken)
            if (!exchangedJwt.isNullOrBlank() && exchangedJwt != firstToken && exchangedJwt != refreshedFb) {
                response = NetworkRetry.withBackoff { operation(exchangedJwt) }
                if (response.code() != 401) {
                    if (response.isSuccessful) {
                        lastSupabaseAuthDiagnostic = null
                    }
                    return response
                }
            }
        }

        if (response.code() == 401) {
            val errBody = readErrorBodyOnce(response).ifBlank { firstErrBody }
            recordAuthDiagnostic(401, errBody, rawFirebaseToken)
        }
        return response
    }

    private suspend fun <T> withMediaAuthRetry(
        service: SupabaseApiService,
        uploadBlock: suspend (bearerToken: String) -> Result<T>
    ): T {
        val firstToken = getEffectiveBearerToken(false)
            ?: throw IllegalStateException("Sessão Firebase inválida.")
        val firstAttempt = uploadBlock(firstToken)
        if (firstAttempt.isSuccess) return firstAttempt.getOrThrow()

        val firstMsg = firstAttempt.exceptionOrNull()?.message.orEmpty()
        if (!firstMsg.contains("HTTP 401")) {
            throw firstAttempt.exceptionOrNull() ?: IllegalStateException("Falha no envio de mídia.")
        }

        if (firstToken == cachedExchangedJwt) {
            cachedExchangedJwt = null
            exchangedJwtExpiresAtMs = 0L
        }
        val refreshedFb = getToken(forceRefresh = true)
        if (!refreshedFb.isNullOrBlank() && refreshedFb != firstToken) {
            val secondAttempt = uploadBlock(refreshedFb)
            if (secondAttempt.isSuccess) return secondAttempt.getOrThrow()
        }

        val rawFb = refreshedFb ?: getToken(forceRefresh = false)
        if (!rawFb.isNullOrBlank()) {
            val exchanged = exchangeFirebaseSessionWithMutex(service, rawFb)
            if (!exchanged.isNullOrBlank()) {
                val thirdAttempt = uploadBlock(exchanged)
                if (thirdAttempt.isSuccess) return thirdAttempt.getOrThrow()
            }
        }

        recordAuthDiagnostic(401, firstMsg, rawFb ?: firstToken)
        throw firstAttempt.exceptionOrNull() ?: IllegalStateException("Falha no envio de mídia (HTTP 401).")
    }

    private fun readErrorBodyOnce(res: Response<*>): String {
        cachedErrorBodies[res]?.let { return it }
        val raw = runCatching { res.errorBody()?.string() }.getOrNull().orEmpty().trim()
        cachedErrorBodies[res] = raw
        return raw
    }

    private fun extractHttpErrorDetail(res: Response<*>): String {
        val raw = readErrorBodyOnce(res)
        if (raw.isBlank()) return ""
        val parsed = runCatching {
            val obj = JSONObject(raw)
            listOf("message", "error_description", "error", "hint", "details")
                .firstNotNullOfOrNull { key -> obj.optString(key).takeIf { it.isNotBlank() } }
        }.getOrNull() ?: raw
        return parsed.replace("\n", " ").take(140)
    }

    private fun decodeJwtClaims(jwt: String?): JSONObject? {
        if (jwt.isNullOrBlank()) return null
        val parts = jwt.split(".")
        if (parts.size != 3) return null
        return runCatching {
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            JSONObject(String(payloadBytes, Charsets.UTF_8))
        }.getOrNull()
    }

    private fun recordAuthDiagnostic(statusCode: Int, rawErrorBody: String?, token: String?) {
        val claims = decodeJwtClaims(token)
        val roleClaim = claims?.optString("role").orEmpty()
        val audClaim = claims?.optString("aud").orEmpty()
        val expSec = claims?.optLong("exp", 0L) ?: 0L
        val isExpired = expSec > 0L && (expSec * 1000L) < System.currentTimeMillis()
        val snippet = rawErrorBody
            ?.replace("\n", " ")
            ?.trim()
            ?.take(120)
            ?.takeIf { it.isNotBlank() }
        val exchangeNote = lastExchangeErrorSummary?.let { " [$it]" }.orEmpty()

        lastSupabaseAuthDiagnostic = when {
            !SupabaseConfig.isSupabaseConfigured && apiOverride == null ->
                "Configura SUPABASE_URL e SUPABASE_ANON_KEY nos Secrets do AI Studio."
            rawErrorBody?.contains("Invalid API key", ignoreCase = true) == true ->
                "SUPABASE_ANON_KEY inválida ou rejeitada pelo servidor. Verifica os Secrets do AI Studio."
            isExpired ->
                "Token Firebase expirado. Toca em Sincronizar para renovar a sessão."
            rawErrorBody?.contains("No suitable key", ignoreCase = true) == true ||
                rawErrorBody?.contains("wrong key type", ignoreCase = true) == true ->
                "Supabase recusou a assinatura RS256 do token Firebase (HTTP $statusCode${if (snippet != null) ": $snippet" else ""}). Ativa Firebase Third-Party Auth (Project ID gen-lang-client-0990149874) no Supabase ou faz deploy da Edge Function firebase-session.$exchangeNote"
            claims != null && roleClaim != "authenticated" ->
                "Supabase recusou o token Firebase (HTTP $statusCode${if (snippet != null) ": $snippet" else ""}): falta a claim role=\"authenticated\"${if (audClaim.isNotBlank()) " (aud=$audClaim)" else ""}. Ativa Firebase Third-Party Auth no Supabase com custom claim role=\"authenticated\" ou faz deploy da Edge Function firebase-session.$exchangeNote"
            else ->
                "Supabase recusou a autenticação (HTTP $statusCode${if (snippet != null) ": $snippet" else ""}). Verifica se Firebase Third-Party Auth está ativo no Supabase para gen-lang-client-0990149874 ou se a Edge Function firebase-session está publicada.$exchangeNote"
        }
        Log.w("MeuPaisAuth", lastSupabaseAuthDiagnostic.orEmpty())
    }

    private fun requireUserId(): String =
        currentAuthenticatedUid()
            ?: throw IllegalStateException("Utilizador deve autenticar-se com Google via Firebase Auth.")

    fun isOnlineBackendAvailable(): Boolean = api != null

    fun startRealtime(firebaseToken: String?, onEvent: (table: String, eventType: String, payload: String) -> Unit) {
        if (!SupabaseConfig.isSupabaseConfigured || firebaseToken.isNullOrBlank()) return
        realtimeClient?.disconnect()
        realtimeClient = SupabaseRealtimeClient(onEvent).apply {
            connect(firebaseToken)
        }
    }

    fun stopRealtime() {
        realtimeClient?.disconnect()
        realtimeClient = null
    }

    suspend fun getBackendStatus(): BackendStatus {
        val fcmToken = preferences.fcmTokenFlow.first()
        return BackendStatus(
            supabaseConfigured = SupabaseConfig.isSupabaseConfigured || apiOverride != null,
            supabaseUrlHost = SupabaseConfig.supabaseHost,
            firebaseConfigured = FirebaseTelemetryManager.isFirebaseInitialized(context),
            fcmTokenPreview = fcmToken?.let { if (it.length > 18) "${it.take(10)}…${it.takeLast(6)}" else it },
            liveKitConfigured = SupabaseConfig.isLiveKitConfigured,
            streamProviderConfigured = SupabaseConfig.isStreamConfigured,
            supabaseAuthDiagnostic = lastSupabaseAuthDiagnostic
        )
    }

    suspend fun attemptAutoSignIn(
        onRealtime: (String, String, String) -> Unit = { _, _, _ -> }
    ): AuthResult? {
        return when (val outcome = GoogleAuthHelper.attemptAutoSignIn(context)) {
            is FirebaseGoogleAuthOutcome.Success -> {
                val profile = syncProfileWithSupabase(outcome.firebaseUser, outcome.firebaseIdToken)
                if (!hasAuthRejection()) {
                    val effectiveToken = getEffectiveBearerToken() ?: outcome.firebaseIdToken
                    syncDevicePushToken(outcome.firebaseUser.uid, effectiveToken)
                    startRealtime(effectiveToken, onRealtime)
                }
                AuthResult.Success(profile, "Sessão restaurada com segurança.")
            }
            else -> null
        }
    }

    suspend fun signInWithGoogle(
        onRealtime: (String, String, String) -> Unit = { _, _, _ -> }
    ): AuthResult {
        return when (val outcome = GoogleAuthHelper.signInWithGoogle(context)) {
            is FirebaseGoogleAuthOutcome.Success -> {
                clearAuthRecoveryState()
                val profile = syncProfileWithSupabase(outcome.firebaseUser, outcome.firebaseIdToken)
                if (!hasAuthRejection()) {
                    val effectiveToken = getEffectiveBearerToken() ?: outcome.firebaseIdToken
                    syncDevicePushToken(outcome.firebaseUser.uid, effectiveToken)
                    startRealtime(effectiveToken, onRealtime)
                }
                FirebaseTelemetryManager.logEvent(context, "login", mapOf("method" to "firebase_google"))
                val statusMsg = "Bem-vindo ao Socializa, @${profile.username}!"
                AuthResult.Success(profile, statusMsg)
            }
            is FirebaseGoogleAuthOutcome.Cancelled -> AuthResult.Cancelled
            is FirebaseGoogleAuthOutcome.Failure -> AuthResult.Error(outcome.message)
        }
    }

    suspend fun signInWithEmailPassword(
        email: String,
        password: String,
        onRealtime: (String, String, String) -> Unit = { _, _, _ -> }
    ): AuthResult {
        emailSignInOverride?.let { return it.invoke(email, password) }
        return when (val outcome = GoogleAuthHelper.signInWithEmailPassword(email, password)) {
            is FirebaseGoogleAuthOutcome.Success -> {
                clearAuthRecoveryState()
                val profile = syncProfileWithSupabase(outcome.firebaseUser, outcome.firebaseIdToken)
                if (!hasAuthRejection()) {
                    val effectiveToken = getEffectiveBearerToken() ?: outcome.firebaseIdToken
                    syncDevicePushToken(outcome.firebaseUser.uid, effectiveToken)
                    startRealtime(effectiveToken, onRealtime)
                }
                FirebaseTelemetryManager.logEvent(context, "login", mapOf("method" to "firebase_email_password"))
                AuthResult.Success(profile, "Bem-vindo ao Socializa, @${profile.username}!")
            }
            is FirebaseGoogleAuthOutcome.Cancelled -> AuthResult.Cancelled
            is FirebaseGoogleAuthOutcome.Failure -> AuthResult.Error(outcome.message)
        }
    }

    private fun gatewayBearer(): String {
        val key = apiKey()
        return "Bearer $key"
    }

    private suspend fun invokeEmailOtp(request: EmailOtpRequestDto): Result<com.example.data.remote.EmailOtpResponseDto> = runCatching {
        val service = api ?: throw IllegalStateException("Servidor Supabase não configurado nos Secrets do AI Studio.")
        val response = NetworkRetry.withBackoff {
            service.callEmailOtp(
                apiKey = apiKey(),
                gatewayBearer = gatewayBearer(),
                body = request
            )
        }
        val body = response.body()
        if (response.isSuccessful && body != null && body.ok) {
            body
        } else {
            val detail = body?.error ?: extractHttpErrorDetail(response).ifBlank {
                "Erro ao processar código OTP (HTTP ${response.code()})."
            }
            throw IllegalStateException(detail)
        }
    }

    suspend fun sendRegistrationOtp(fullName: String, email: String): Result<Int> {
        return invokeEmailOtp(
            EmailOtpRequestDto(
                action = "send_register_otp",
                email = email.trim(),
                fullName = fullName.trim()
            )
        ).map { it.expiresInSeconds.coerceAtLeast(60) }
    }

    suspend fun verifyRegistrationOtpAndSignIn(
        fullName: String,
        email: String,
        password: String,
        otp: String,
        onRealtime: (String, String, String) -> Unit = { _, _, _ -> }
    ): AuthResult {
        val verifyRes = invokeEmailOtp(
            EmailOtpRequestDto(
                action = "verify_register_otp",
                email = email.trim(),
                fullName = fullName.trim(),
                password = password,
                otp = otp.trim()
            )
        )
        verifyRes.onFailure { err ->
            return AuthResult.Error(err.message ?: "Não foi possível validar o código.")
        }
        return signInWithEmailPassword(email.trim(), password, onRealtime)
    }

    suspend fun sendPasswordResetOtp(email: String): Result<Int> {
        return invokeEmailOtp(
            EmailOtpRequestDto(
                action = "send_reset_otp",
                email = email.trim()
            )
        ).map { it.expiresInSeconds.coerceAtLeast(60) }
    }

    suspend fun confirmPasswordResetWithOtp(
        email: String,
        otp: String,
        newPassword: String
    ): Result<String> {
        return invokeEmailOtp(
            EmailOtpRequestDto(
                action = "verify_reset_otp",
                email = email.trim(),
                newPassword = newPassword,
                otp = otp.trim()
            )
        ).map { it.message ?: "Senha atualizada com sucesso!" }
    }

    suspend fun syncCurrentFirebaseUser(
        onRealtime: (String, String, String) -> Unit = { _, _, _ -> }
    ): UserProfile? {
        val user = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return null
        val token = getToken()
        val profile = syncProfileWithSupabase(user, token)
        if (!hasAuthRejection()) {
            val effectiveToken = getEffectiveBearerToken() ?: token
            if (!effectiveToken.isNullOrBlank()) {
                syncDevicePushToken(user.uid, effectiveToken)
                startRealtime(effectiveToken, onRealtime)
            }
        }
        return profile
    }

    private suspend fun syncProfileWithSupabase(
        firebaseUser: FirebaseUser,
        firebaseIdToken: String?
    ): UserProfile {
        val savedBio = preferences.getSavedBio()
        val savedProvince = preferences.getSavedProvince()
        val fallbackName = firebaseUser.displayName?.takeIf { it.isNotBlank() } ?: "Utilizador Socializa"
        val fallbackUsername = (firebaseUser.email?.substringBefore("@") ?: "socializa")
            .lowercase()
            .replace(Regex("[^a-z0-9._]"), "")
            .ifBlank { "socializa" } + "_" + firebaseUser.uid.take(4).lowercase()

        val service = api
        if (service != null && !firebaseIdToken.isNullOrBlank()) {
            val rpcResponse = runCatching {
                executeWithAuthRetry(service, initialTokenOverride = firebaseIdToken) { bearer ->
                    service.ensureFirebaseProfile(
                        apiKey = apiKey(),
                        firebaseBearer = "Bearer $bearer",
                        body = EnsureFirebaseProfileRequest(
                            fullName = fallbackName,
                            avatarUrl = firebaseUser.photoUrl?.toString(),
                            province = savedProvince,
                            country = "Angola"
                        )
                    )
                }
            }.onFailure { ErrorMonitor.record(context, "ensure_profile", it) }.getOrNull()

            if (rpcResponse != null) {
                if (rpcResponse.isSuccessful) {
                    val dto = rpcResponse.body()
                    if (dto != null) {
                        lastSupabaseAuthDiagnostic = null
                        val localUntil = preferences.getVerifiedUntilMs(firebaseUser.uid)
                        val profile = dto.toDomain(defaultAvatar = firebaseUser.photoUrl?.toString()).let { mapped ->
                            val mergedUntil = maxOf(mapped.verifiedUntilMs, localUntil)
                            mapped.copy(verifiedUntilMs = mergedUntil)
                        }
                        if (profile.verifiedUntilMs > localUntil) {
                            preferences.saveVerifiedUntilMs(firebaseUser.uid, profile.verifiedUntilMs)
                        }
                        runCatching { cacheDao.upsertProfile(profile.toCacheEntity()) }
                        return profile
                    }
                } else {
                    val detail = extractHttpErrorDetail(rpcResponse)
                    if (rpcResponse.code() == 401) {
                        Log.w(
                            "MeuPaisAuth",
                            "Sincronização de perfil aguardando autorização Supabase (HTTP 401${if (detail.isNotBlank()) ": $detail" else ""})."
                        )
                    } else {
                        ErrorMonitor.record(
                            context,
                            "ensure_profile",
                            IllegalStateException("Erro ao sincronizar perfil (HTTP ${rpcResponse.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
                        )
                    }
                }
            }
        }

        // Recuperar perfil em cache local se offline (contadores a zero se primeira vez, nunca falsos!)
        val localUntil = preferences.getVerifiedUntilMs(firebaseUser.uid)
        val cached = runCatching { cacheDao.getCachedProfile(firebaseUser.uid) }.getOrNull()
        if (cached != null) {
            return cached.toDomain().let { p -> p.copy(verifiedUntilMs = maxOf(p.verifiedUntilMs, localUntil)) }
        }

        return UserProfile(
            id = firebaseUser.uid,
            username = fallbackUsername,
            fullName = fallbackName,
            bio = savedBio.orEmpty(),
            avatarUri = firebaseUser.photoUrl?.toString(),
            country = "Angola",
            province = savedProvince,
            website = "",
            isPrivate = false,
            isVerified = false,
            verifiedUntilMs = localUntil,
            role = "user",
            followersCount = 0,
            followingCount = 0,
            postsCount = 0,
            isOnline = true
        )
    }

    suspend fun signOut() {
        clearAuthRecoveryState()
        stopRealtime()
        runCatching { cacheDao.clearAllPosts() }
        if (apiOverride == null && emailSignInOverride == null) {
            GoogleAuthHelper.signOut(context)
        }
    }

    suspend fun fetchUserProfile(targetUid: String): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val res = executeWithAuthRetry(service) { token ->
            service.getProfile(apiKey(), "Bearer $token", "eq.$targetUid")
        }
        if (!res.isSuccessful) {
            val detail = extractHttpErrorDetail(res)
            throw IllegalStateException("Perfil não carregado (HTTP ${res.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
        }
        val dto = res.body()?.firstOrNull() ?: throw IllegalStateException("Perfil não encontrado.")
        val followingIds = fetchFollowingIdsInternal(service, uid)
        val blockedIds = fetchBlockedIdsInternal(service, uid)
        dto.toDomain().copy(
            isFollowedByMe = dto.id in followingIds,
            isBlockedByMe = dto.id in blockedIds
        )
    }

    suspend fun searchUsers(query: String, province: String? = null, offset: Int = 0, limit: Int = 25): Result<List<UserProfile>> = runCatching {
        val uid = requireUserId()
        val service = api ?: return@runCatching emptyList()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val clean = query.trim().replace(",", "")
        val orFilter = if (clean.isNotBlank()) "(username.ilike.*$clean*,full_name.ilike.*$clean*)" else null
        val provFilter = province?.takeIf { it.isNotBlank() && it != "Todas" }?.let { "eq.$it" }

        val res = executeWithAuthRetry(service) { token ->
            service.searchProfiles(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                orFilter = orFilter,
                provinceFilter = provFilter,
                limit = limit,
                offset = offset
            )
        }
        if (!res.isSuccessful) return@runCatching emptyList()
        val followingIds = fetchFollowingIdsInternal(service, uid)
        val blockedIds = fetchBlockedIdsInternal(service, uid)
        (res.body() ?: emptyList())
            .filter { it.id != uid && it.id !in blockedIds }
            .map { it.toDomain().copy(isFollowedByMe = it.id in followingIds) }
    }

    suspend fun updateOwnProfile(
        fullName: String,
        username: String,
        bio: String,
        province: String,
        website: String,
        isPrivate: Boolean,
        newAvatarUri: Uri? = null
    ): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Backend Supabase não configurado.")

        var uploadedAvatarUrl: String? = null
        if (newAvatarUri != null) {
            val uploaded = withMediaAuthRetry(service) { token ->
                MediaUploadHelper.uploadImageUri(context, service, token, uid, "avatars", newAvatarUri)
            }
            uploadedAvatarUrl = uploaded.publicUrl
        }

        preferences.saveProfileMetadata(bio, province, "Angola", website)
        val updates = mutableMapOf<String, Any>(
            "full_name" to fullName.trim(),
            "username" to username.trim().lowercase().replace(Regex("[^a-z0-9._]"), ""),
            "bio" to bio.trim(),
            "province" to province.trim(),
            "website" to website.trim(),
            "is_private" to isPrivate
        )
        if (uploadedAvatarUrl != null) {
            updates["avatar_url"] = uploadedAvatarUrl
        }

        val res = executeWithAuthRetry(service) { token ->
            service.updateProfile(apiKey(), "Bearer $token", "eq.$uid", updates)
        }
        if (!res.isSuccessful) {
            val detail = extractHttpErrorDetail(res)
            throw IllegalStateException("Não foi possível atualizar o perfil (HTTP ${res.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
        }
        val refreshed = executeWithAuthRetry(service) { token ->
            service.getProfile(apiKey(), "Bearer $token", "eq.$uid")
        }.body()?.firstOrNull()
            ?: throw IllegalStateException("Perfil atualizado, mas não foi possível recarregar.")
        val profile = refreshed.toDomain().let { p ->
            p.copy(verifiedUntilMs = maxOf(p.verifiedUntilMs, preferences.getVerifiedUntilMs(uid)))
        }
        cacheDao.upsertProfile(profile.toCacheEntity())
        profile
    }.onFailure { ErrorMonitor.record(context, "update_profile", it) }

    suspend fun claimDailyVerifiedBadge(adsWatched: Int, currentUser: UserProfile): Result<UserProfile> = runCatching {
        val uid = requireUserId()
        if (adsWatched < 5) {
            throw IllegalArgumentException("Assiste a 5 anúncios de texto para ativar o selo por 24 horas.")
        }
        val until24h = System.currentTimeMillis() + 86_400_000L
        preferences.saveVerifiedUntilMs(uid, until24h)
        preferences.saveAdsWatchedCount(uid, 0)

        val service = api
        var finalProfile = currentUser.copy(verifiedUntilMs = until24h)
        if (service != null) {
            val res = runCatching {
                executeWithAuthRetry(service) { token ->
                    service.claimDailyVerifiedBadge(
                        apiKey = apiKey(),
                        firebaseBearer = "Bearer $token",
                        body = ClaimDailyVerifiedBadgeRequest(adsWatched = adsWatched)
                    )
                }
            }.getOrNull()
            if (res != null && res.isSuccessful) {
                res.body()?.let { dto ->
                    val mapped = dto.toDomain(defaultAvatar = currentUser.avatarUri)
                    val mergedUntil = maxOf(mapped.verifiedUntilMs, until24h)
                    preferences.saveVerifiedUntilMs(uid, mergedUntil)
                    finalProfile = mapped.copy(verifiedUntilMs = mergedUntil)
                }
            }
        }
        runCatching { cacheDao.upsertProfile(finalProfile.toCacheEntity()) }
        finalProfile
    }

    // =========================================================================
    // FEED ONLINE PAGINADO, PUBLICAÇÕES, EDIÇÃO, ELIMINAÇÃO & REAÇÕES
    // =========================================================================

    suspend fun fetchFeedPosts(
        mode: FeedMode,
        page: Int,
        pageSize: Int = 15,
        provinceFilter: String? = null,
        categoryFilter: String? = null,
        currentUserProfile: UserProfile? = null
    ): Result<List<FeedPost>> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching loadCachedPosts(uid)
        val service = api ?: return@runCatching emptyList()

        val order = when (mode) {
            FeedMode.FOR_YOU -> "created_at.desc"
            FeedMode.TRENDING -> "likes_count.desc,comments_count.desc,created_at.desc"
            FeedMode.FOLLOWING -> "created_at.desc"
        }
        val locFilter = provinceFilter?.takeIf { it.isNotBlank() && it != "Todas" }?.let { "ilike.*$it*" }
        val catFilter = categoryFilter?.takeIf { it.isNotBlank() }?.let { "eq.$it" }

        // Carrega o feed principal primeiro (garante que 401 dispara recuperação/troca JWT antes dos pedidos auxiliares)
        val res = executeWithAuthRetry(service) { token ->
            service.getPosts(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                order = order,
                limit = pageSize,
                offset = page * pageSize,
                locationFilter = locFilter,
                categoryFilter = catFilter
            )
        }
        if (!res.isSuccessful) {
            val detail = extractHttpErrorDetail(res)
            throw IllegalStateException("Erro ao carregar feed (HTTP ${res.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
        }

        val serverHints = runCatching {
            val modeParam = when (mode) {
                FeedMode.FOR_YOU -> "for_you"
                FeedMode.FOLLOWING -> "following"
                FeedMode.TRENDING -> "trending"
            }
            executeWithAuthRetry(service) { token ->
                service.getRankedFeed(
                    apiKey = apiKey(),
                    firebaseBearer = "Bearer $token",
                    body = RankedFeedRequestDto(
                        mode = modeParam,
                        limit = pageSize,
                        offset = page * pageSize,
                        province = provinceFilter,
                        category = categoryFilter
                    )
                )
            }.body().orEmpty().associate { it.postId to it.score }
        }.getOrDefault(emptyMap())

        val followingIds = fetchFollowingIdsInternal(service, uid)
        val blockedIds = fetchBlockedIdsInternal(service, uid)
        val likedIds = fetchLikedPostIdsInternal(service, uid)
        val savedIds = fetchSavedPostIdsInternal(service, uid)

        val rawPosts = res.body().orEmpty()
        val ownActiveBadge = preferences.getVerifiedUntilMs(uid) > System.currentTimeMillis()
        val mapped = rawPosts
            .filter { it.authorId !in blockedIds }
            .filter { mode != FeedMode.FOLLOWING || it.authorId in followingIds || it.authorId == uid }
            .map { dto ->
                dto.toDomain(
                    isLiked = dto.id in likedIds,
                    isSaved = dto.id in savedIds,
                    isAuthorFollowed = dto.authorId in followingIds
                ).let { post ->
                    if (post.authorId == uid && ownActiveBadge) post.copy(authorVerified = true) else post
                }
            }

        val profileForRanking = currentUserProfile ?: UserProfile(
            id = uid,
            username = "utilizador",
            fullName = "Utilizador",
            province = preferences.getSavedProvince()
        )
        val ranked = SocializaAlgorithmEngine.rankFeedPosts(
            posts = mapped,
            mode = mode,
            currentUser = profileForRanking,
            interestWeights = interestWeights,
            serverScoreHints = serverHints
        )

        if (page == 0 && provinceFilter == null && categoryFilter == null) {
            runCatching {
                cacheDao.clearPostsForOwner(uid)
                cacheDao.upsertPosts(ranked.map { it.toCacheEntity(uid) })
            }
        }
        ranked
    }.recoverCatching { err ->
        if (hasAuthRejection() || err.message?.contains("HTTP 401") == true) {
            Log.w("MeuPaisAuth", err.message ?: "Feed aguardando autorização Supabase (HTTP 401).")
        } else {
            ErrorMonitor.record(context, "fetch_feed", err)
        }
        if (page == 0) {
            val uid = currentAuthenticatedUid() ?: return@recoverCatching emptyList()
            val cached = loadCachedPosts(uid)
            if (cached.isNotEmpty() || lastSupabaseAuthDiagnostic == null) {
                cached
            } else {
                throw err
            }
        } else throw err
    }

    suspend fun loadCachedPostsForUi(uid: String): List<FeedPost> = loadCachedPosts(uid)

    private suspend fun loadCachedPosts(uid: String): List<FeedPost> =
        runCatching { cacheDao.getCachedPosts(uid).map { it.toDomain() } }.getOrDefault(emptyList())

    suspend fun createPost(
        caption: String,
        postType: PostType,
        category: String?,
        location: String,
        hashtags: List<String>,
        visibility: PostVisibility,
        mediaUris: List<Uri>,
        publishAlsoToReels: Boolean,
        currentUser: UserProfile,
        audioUri: Uri? = null,
        audioTitle: String? = null
    ): Result<FeedPost> = runCatching {
        val uid = requireUserId()
        rateLimiter.checkAndRecord("create_post", maxPerMinute = 12)
        val safety = SocializaAlgorithmEngine.evaluateContentSafety(caption, recentSubmittedTexts)
        if (!safety.isAllowed) {
            throw IllegalArgumentException(safety.reason ?: "Publicação bloqueada pela moderação automática.")
        }
        recentSubmittedTexts.add(0, caption.trim())
        if (recentSubmittedTexts.size > 15) recentSubmittedTexts.removeAt(recentSubmittedTexts.lastIndex)
        val service = api ?: throw IllegalStateException("Servidor indisponível.")

        val uploadedMedia = mutableListOf<Pair<String, com.example.data.remote.UploadedMediaResult>>()
        mediaUris.forEach { uri ->
            val mime = context.contentResolver.getType(uri).orEmpty()
            val isVideo = postType == PostType.VIDEO || mime.startsWith("video/")
            val uploadResult = withMediaAuthRetry(service) { token ->
                if (isVideo) {
                    MediaUploadHelper.uploadBinaryUri(context, service, token, uid, "videos", uri, "video/mp4", "mp4")
                } else {
                    MediaUploadHelper.uploadImageUri(context, service, token, uid, "posts", uri)
                }
            }
            uploadedMedia.add((if (isVideo) "video" else "image") to uploadResult)
        }

        val uploadedAudio = if (audioUri != null) {
            withMediaAuthRetry(service) { token ->
                MediaUploadHelper.uploadBinaryUri(context, service, token, uid, "music", audioUri, "audio/mpeg", "mp3")
            }
        } else null

        val resolvedMusicTitle = audioTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: if (uploadedAudio != null) "Música selecionada" else "Som original — @${currentUser.username}"

        val dbType = when (postType) {
            PostType.TEXT -> "text"
            PostType.VIDEO -> "video"
            PostType.CAROUSEL -> "carousel"
            PostType.PHOTO, PostType.STORY -> if (uploadedMedia.size > 1) "carousel" else if (uploadedMedia.isEmpty()) "text" else "photo"
        }

        val postRes = executeWithAuthRetry(service) { token ->
            service.createPost(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                body = SupabasePostInsertDto(
                    authorId = uid,
                    postType = dbType,
                    caption = caption.trim(),
                    hashtags = hashtags,
                    category = category,
                    locationLabel = location.ifBlank { null },
                    visibility = visibility.name.lowercase()
                )
            )
        }
        if (!postRes.isSuccessful) {
            val detail = extractHttpErrorDetail(postRes)
            throw IllegalStateException("Erro ao criar publicação (HTTP ${postRes.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
        }
        val createdDto = postRes.body()?.firstOrNull()
            ?: throw IllegalStateException("Erro ao criar publicação (resposta vazia do servidor).")

        uploadedMedia.forEachIndexed { idx, (mediaType, uploaded) ->
            executeWithAuthRetry(service) { token ->
                service.createMediaRecord(
                    apiKey = apiKey(),
                    firebaseBearer = "Bearer $token",
                    body = SupabaseMediaInsertDto(
                        postId = createdDto.id,
                        ownerId = uid,
                        mediaType = mediaType,
                        storagePath = uploaded.storagePath,
                        publicUrl = uploaded.publicUrl,
                        width = uploaded.width,
                        height = uploaded.height,
                        sortOrder = idx
                    )
                )
            }
        }

        if (uploadedAudio != null) {
            executeWithAuthRetry(service) { token ->
                service.createMediaRecord(
                    apiKey = apiKey(),
                    firebaseBearer = "Bearer $token",
                    body = SupabaseMediaInsertDto(
                        postId = createdDto.id,
                        ownerId = uid,
                        mediaType = "audio",
                        storagePath = uploadedAudio.storagePath,
                        publicUrl = uploadedAudio.publicUrl,
                        sortOrder = 99
                    )
                )
            }
        }

        // Se for vídeo ou publicar nos Reels, cria também registo na tabela videos
        val videoUpload = uploadedMedia.firstOrNull { it.first == "video" }?.second
        if ((publishAlsoToReels || postType == PostType.VIDEO) && videoUpload != null) {
            val videoRes = executeWithAuthRetry(service) { token ->
                service.createVideo(
                    apiKey = apiKey(),
                    firebaseBearer = "Bearer $token",
                    body = SupabaseVideoInsertDto(
                        authorId = uid,
                        provider = if (SupabaseConfig.isStreamConfigured) "cloudflare_stream" else "supabase_storage",
                        streamAssetId = videoUpload.storagePath,
                        playbackUrl = videoUpload.publicUrl,
                        caption = caption.trim(),
                        hashtags = hashtags,
                        musicTitle = resolvedMusicTitle,
                        musicUrl = uploadedAudio?.publicUrl
                    )
                )
            }
            if (!videoRes.isSuccessful && videoRes.code() == 400 && uploadedAudio != null) {
                executeWithAuthRetry(service) { token ->
                    service.createVideo(
                        apiKey = apiKey(),
                        firebaseBearer = "Bearer $token",
                        body = SupabaseVideoInsertDto(
                            authorId = uid,
                            provider = if (SupabaseConfig.isStreamConfigured) "cloudflare_stream" else "supabase_storage",
                            streamAssetId = videoUpload.storagePath,
                            playbackUrl = videoUpload.publicUrl,
                            caption = caption.trim(),
                            hashtags = hashtags,
                            musicTitle = resolvedMusicTitle,
                            musicUrl = null
                        )
                    )
                }
            }
        }

        FirebaseTelemetryManager.logEvent(context, "create_post", mapOf("type" to dbType))

        FeedPost(
            id = createdDto.id,
            authorId = uid,
            authorName = currentUser.fullName,
            authorUsername = currentUser.username,
            authorVerified = currentUser.hasActiveVerifiedBadge,
            authorAvatarUrl = currentUser.avatarUri,
            postType = postType,
            caption = caption.trim(),
            hashtags = hashtags,
            category = category,
            locationLabel = location,
            visibility = visibility,
            mediaUrls = uploadedMedia.map { it.second.publicUrl },
            musicTitle = if (uploadedAudio != null || postType == PostType.VIDEO) resolvedMusicTitle else null,
            musicUrl = uploadedAudio?.publicUrl,
            likesCount = 0,
            commentsCount = 0,
            sharesCount = 0,
            viewsCount = 0,
            createdAtIso = createdDto.createdAt,
            createdAtLabel = "Agora"
        )
    }.onFailure { ErrorMonitor.record(context, "create_post", it) }

    suspend fun editPost(
        postId: String,
        caption: String,
        hashtags: List<String>,
        location: String,
        visibility: PostVisibility
    ): Result<Unit> = runCatching {
        requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val res = executeWithAuthRetry(service) { token ->
            service.updatePost(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                idFilter = "eq.$postId",
                updates = mapOf(
                    "caption" to caption.trim(),
                    "hashtags" to hashtags,
                    "location_label" to location,
                    "visibility" to visibility.name.lowercase(),
                    "edited_at" to isoNow()
                )
            )
        }
        if (!res.isSuccessful) {
            val detail = extractHttpErrorDetail(res)
            throw IllegalStateException("Não foi possível editar a publicação (HTTP ${res.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
        }
    }.onFailure { ErrorMonitor.record(context, "edit_post", it) }

    suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val res = executeWithAuthRetry(service) { token ->
            service.deletePost(apiKey(), "Bearer $token", "eq.$postId")
        }
        if (!res.isSuccessful) {
            val detail = extractHttpErrorDetail(res)
            throw IllegalStateException("Não foi possível eliminar a publicação (HTTP ${res.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
        }
        cacheDao.deletePost(postId)
    }.onFailure { ErrorMonitor.record(context, "delete_post", it) }

    suspend fun togglePostLike(post: FeedPost, actorName: String): Result<Boolean> = runCatching {
        val uid = requireUserId()
        rateLimiter.checkAndRecord("like_post", maxPerMinute = 60)
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        if (post.isLiked) {
            val res = executeWithAuthRetry(service) { token ->
                service.unlikePost(apiKey(), "Bearer $token", "eq.$uid", "eq.${post.id}")
            }
            if (!res.isSuccessful) throw IllegalStateException("Erro ao remover curtida (HTTP ${res.code()}).")
            false
        } else {
            val res = executeWithAuthRetry(service) { token ->
                service.likePost(apiKey(), "Bearer $token", SupabaseLikeRecordDto(userId = uid, postId = post.id))
            }
            if (!res.isSuccessful && res.code() != 409) {
                throw IllegalStateException("Erro ao registar curtida (HTTP ${res.code()}).")
            }
            if (post.authorId != uid) {
                dispatchPushSilently(
                    service = service,
                    recipientId = post.authorId,
                    eventType = "like",
                    title = "$actorName curtiu a tua publicação",
                    body = post.caption.take(80),
                    referenceId = post.id
                )
            }
            true
        }
    }

    suspend fun togglePostSave(postId: String, currentlySaved: Boolean): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        if (currentlySaved) {
            executeWithAuthRetry(service) { token ->
                service.unsavePost(apiKey(), "Bearer $token", "eq.$uid", "eq.$postId")
            }
            false
        } else {
            executeWithAuthRetry(service) { token ->
                service.savePost(apiKey(), "Bearer $token", SupabaseSavedPostRecordDto(uid, postId))
            }
            true
        }
    }

    suspend fun recordPostView(postId: String) {
        val uid = currentAuthenticatedUid() ?: return
        val service = api ?: return
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.recordPostView(apiKey(), "Bearer $token", body = SupabasePostViewDto(postId, uid))
            }
        }
    }

    suspend fun sharePost(postId: String): Result<Unit> = runCatching {
        requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val res = executeWithAuthRetry(service) { token ->
            service.incrementPostShare(apiKey(), "Bearer $token", PostShareRpcRequest(postId))
        }
        if (!res.isSuccessful) throw IllegalStateException("Erro ao partilhar publicação (HTTP ${res.code()}).")
    }

    // =========================================================================
    // COMENTÁRIOS, RESPOSTAS E CURTIDAS EM COMENTÁRIOS
    // =========================================================================

    suspend fun fetchComments(postId: String? = null, videoId: String? = null): Result<List<PostComment>> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val service = api ?: return@runCatching emptyList()
        val res = executeWithAuthRetry(service) { token ->
            service.getComments(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                postFilter = postId?.let { "eq.$it" },
                videoFilter = videoId?.let { "eq.$it" }
            )
        }
        val likedCommentIds = runCatching {
            executeWithAuthRetry(service) { token ->
                service.getUserCommentLikes(apiKey(), "Bearer $token", "eq.$uid")
            }.body().orEmpty().map { it.commentId }.toSet()
        }.getOrDefault(emptySet())

        res.body().orEmpty().map { it.toDomain(isLiked = it.id in likedCommentIds) }
    }

    suspend fun addComment(
        postId: String? = null,
        videoId: String? = null,
        body: String,
        parentCommentId: String? = null,
        targetOwnerId: String? = null,
        parentCommentAuthorId: String? = null,
        actor: UserProfile
    ): Result<PostComment> = runCatching {
        val uid = requireUserId()
        rateLimiter.checkAndRecord("create_comment", maxPerMinute = 30)
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val clean = body.trim()
        if (clean.isBlank()) throw IllegalArgumentException("Comentário vazio.")
        val safety = SocializaAlgorithmEngine.evaluateContentSafety(clean, recentSubmittedTexts)
        if (!safety.isAllowed) {
            throw IllegalArgumentException(safety.reason ?: "Comentário bloqueado por regras de segurança.")
        }
        recentSubmittedTexts.add(0, clean)
        if (recentSubmittedTexts.size > 15) recentSubmittedTexts.removeAt(recentSubmittedTexts.lastIndex)

        val res = executeWithAuthRetry(service) { token ->
            service.createComment(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                body = SupabaseCommentInsertDto(
                    postId = postId,
                    videoId = videoId,
                    authorId = uid,
                    parentId = parentCommentId,
                    body = clean
                )
            )
        }
        if (!res.isSuccessful) {
            val detail = extractHttpErrorDetail(res)
            throw IllegalStateException("Erro ao guardar comentário (HTTP ${res.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
        }
        val dto = res.body()?.firstOrNull()
            ?: throw IllegalStateException("Erro ao guardar comentário (resposta vazia).")

        val refId = postId ?: videoId
        if (!targetOwnerId.isNullOrBlank() && targetOwnerId != uid) {
            dispatchPushSilently(
                service, targetOwnerId, "comment",
                "${actor.fullName} comentou", clean.take(100), refId
            )
        }
        if (!parentCommentAuthorId.isNullOrBlank() && parentCommentAuthorId != uid && parentCommentAuthorId != targetOwnerId) {
            dispatchPushSilently(
                service, parentCommentAuthorId, "comment_reply",
                "${actor.fullName} respondeu ao teu comentário", clean.take(100), refId
            )
        }

        PostComment(
            id = dto.id,
            authorId = uid,
            authorName = actor.fullName,
            authorUsername = actor.username,
            authorAvatarUrl = actor.avatarUri,
            parentId = parentCommentId,
            body = clean,
            timeAgo = "Agora",
            likesCount = 0,
            isLiked = false
        )
    }.onFailure { ErrorMonitor.record(context, "add_comment", it) }

    suspend fun toggleCommentLike(commentId: String, currentlyLiked: Boolean): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        if (currentlyLiked) {
            executeWithAuthRetry(service) { token ->
                service.unlikeComment(apiKey(), "Bearer $token", "eq.$uid", "eq.$commentId")
            }
            false
        } else {
            executeWithAuthRetry(service) { token ->
                service.likeComment(apiKey(), "Bearer $token", SupabaseCommentLikeDto(uid, commentId))
            }
            true
        }
    }

    suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val res = executeWithAuthRetry(service) { token ->
            service.deleteComment(apiKey(), "Bearer $token", "eq.$commentId")
        }
        if (!res.isSuccessful) throw IllegalStateException("Erro ao eliminar comentário (HTTP ${res.code()}).")
    }

    // =========================================================================
    // SEGUIDORES, BLOQUEIOS & DENÚNCIAS
    // =========================================================================

    suspend fun toggleFollowUser(targetUid: String, currentlyFollowing: Boolean, actorName: String): Result<Boolean> = runCatching {
        val uid = requireUserId()
        if (uid == targetUid) throw IllegalArgumentException("Não podes seguir-te a ti próprio.")
        rateLimiter.checkAndRecord("follow_user", maxPerMinute = 40)
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        if (currentlyFollowing) {
            executeWithAuthRetry(service) { token ->
                service.unfollowUser(apiKey(), "Bearer $token", "eq.$uid", "eq.$targetUid")
            }
            false
        } else {
            val res = executeWithAuthRetry(service) { token ->
                service.followUser(apiKey(), "Bearer $token", SupabaseFollowDto(uid, targetUid))
            }
            if (!res.isSuccessful && res.code() != 409) {
                throw IllegalStateException("Não foi possível seguir o utilizador (HTTP ${res.code()}).")
            }
            dispatchPushSilently(
                service, targetUid, "follow",
                "$actorName começou a seguir-te", "Toca para ver o perfil.", uid
            )
            true
        }
    }

    suspend fun blockUser(targetUid: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        executeWithAuthRetry(service) { token ->
            service.blockUser(apiKey(), "Bearer $token", SupabaseBlockDto(uid, targetUid))
        }
        // Também deixa de seguir mutuamente
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.unfollowUser(apiKey(), "Bearer $token", "eq.$uid", "eq.$targetUid")
            }
        }
    }

    suspend fun unblockUser(targetUid: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        executeWithAuthRetry(service) { token ->
            service.unblockUser(apiKey(), "Bearer $token", "eq.$uid", "eq.$targetUid")
        }
    }

    suspend fun fetchBlockedIds(): Set<String> {
        val uid = currentAuthenticatedUid() ?: return emptySet()
        if (getEffectiveBearerToken() == null) return emptySet()
        val service = api ?: return emptySet()
        return fetchBlockedIdsInternal(service, uid)
    }

    // =========================================================================
    // STORIES (24H) & VÍDEOS (REELS)
    // =========================================================================

    suspend fun fetchActiveStories(): Result<List<StoryItem>> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val service = api ?: return@runCatching emptyList()
        val res = executeWithAuthRetry(service) { token ->
            service.getActiveStories(apiKey(), "Bearer $token")
        }
        if (!res.isSuccessful) return@runCatching emptyList()
        val blockedIds = fetchBlockedIdsInternal(service, uid)
        val viewedIds = runCatching {
            executeWithAuthRetry(service) { token ->
                service.getStoryViews(apiKey(), "Bearer $token", "eq.$uid")
            }.body().orEmpty().map { it.storyId }.toSet()
        }.getOrDefault(emptySet())

        res.body().orEmpty()
            .filter { it.authorId !in blockedIds }
            .map { dto ->
                StoryItem(
                    id = dto.id,
                    authorId = dto.authorId,
                    authorName = dto.author?.fullName?.ifBlank { dto.author.username } ?: "Utilizador",
                    authorUsername = dto.author?.username ?: "utilizador",
                    avatarUrl = dto.author?.avatarUrl,
                    mediaUrl = dto.mediaUrl,
                    mediaType = dto.mediaType,
                    caption = dto.caption.orEmpty(),
                    timeAgo = formatRelativeTime(dto.createdAt),
                    expiresAtIso = dto.expiresAt,
                    isViewed = dto.id in viewedIds,
                    isOwn = dto.authorId == uid
                )
            }
    }

    suspend fun createStory(caption: String, mediaUri: Uri, currentUser: UserProfile): Result<StoryItem> = runCatching {
        val uid = requireUserId()
        rateLimiter.checkAndRecord("create_story", maxPerMinute = 10)
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val mime = context.contentResolver.getType(mediaUri).orEmpty()
        val isVideo = mime.startsWith("video/")
        val uploaded = withMediaAuthRetry(service) { token ->
            if (isVideo) {
                MediaUploadHelper.uploadBinaryUri(context, service, token, uid, "stories", mediaUri, "video/mp4", "mp4")
            } else {
                MediaUploadHelper.uploadImageUri(context, service, token, uid, "stories", mediaUri)
            }
        }

        val res = executeWithAuthRetry(service) { token ->
            service.createStory(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                body = SupabaseStoryInsertDto(
                    authorId = uid,
                    mediaUrl = uploaded.publicUrl,
                    mediaType = if (isVideo) "video" else "image",
                    caption = caption.trim().ifBlank { null }
                )
            )
        }
        val created = res.body()?.firstOrNull()
            ?: throw IllegalStateException("Erro ao publicar story (HTTP ${res.code()}).")
        StoryItem(
            id = created.id,
            authorId = uid,
            authorName = currentUser.fullName,
            authorUsername = currentUser.username,
            avatarUrl = currentUser.avatarUri,
            mediaUrl = created.mediaUrl,
            mediaType = created.mediaType,
            caption = created.caption.orEmpty(),
            timeAgo = "Agora",
            expiresAtIso = created.expiresAt,
            isViewed = false,
            isOwn = true
        )
    }

    suspend fun recordStoryView(storyId: String) {
        val uid = currentAuthenticatedUid() ?: return
        val service = api ?: return
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.recordStoryView(apiKey(), "Bearer $token", body = SupabaseStoryViewDto(storyId, uid))
            }
        }
    }

    suspend fun fetchVideos(page: Int = 0, pageSize: Int = 15): Result<List<VideoReel>> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val service = api ?: return@runCatching emptyList()
        val res = executeWithAuthRetry(service) { token ->
            service.getVideos(apiKey(), "Bearer $token", limit = pageSize, offset = page * pageSize)
        }
        if (!res.isSuccessful) return@runCatching emptyList()
        val blockedIds = fetchBlockedIdsInternal(service, uid)
        val followingIds = fetchFollowingIdsInternal(service, uid)
        val likedVideoIds = runCatching {
            executeWithAuthRetry(service) { token ->
                service.getUserVideoLikes(apiKey(), "Bearer $token", "eq.$uid")
            }.body().orEmpty().map { it.videoId }.toSet()
        }.getOrDefault(emptySet())

        val ownActiveBadge = preferences.getVerifiedUntilMs(uid) > System.currentTimeMillis()
        res.body().orEmpty()
            .filter { it.authorId !in blockedIds }
            .map { dto ->
                val authorUntil = parseIsoMillis(dto.author?.verifiedUntil)
                val isAuthorBadge = (dto.author?.isVerified == true) ||
                    authorUntil > System.currentTimeMillis() ||
                    (dto.authorId == uid && ownActiveBadge)
                VideoReel(
                    id = dto.id,
                    authorId = dto.authorId,
                    authorName = dto.author?.fullName?.ifBlank { dto.author.username } ?: "Criador",
                    authorUsername = dto.author?.username ?: "criador",
                    authorAvatarUrl = dto.author?.avatarUrl,
                    isVerified = isAuthorBadge,
                    isFollowing = dto.authorId in followingIds,
                    streamUrl = dto.playbackUrl,
                    thumbnailUrl = dto.thumbnailUrl,
                    caption = dto.caption,
                    hashtags = dto.hashtags,
                    musicTitle = dto.musicTitle,
                    musicUrl = dto.musicUrl,
                    effectLabel = dto.effectsTags.firstOrNull() ?: "Original",
                    autoSubtitle = dto.caption,
                    likesCount = dto.likesCount,
                    commentsCount = dto.commentsCount,
                    sharesCount = dto.sharesCount,
                    viewsCount = dto.viewsCount,
                    isLiked = dto.id in likedVideoIds
                )
            }
    }

    suspend fun toggleVideoLike(reel: VideoReel, actorName: String): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        if (reel.isLiked) {
            executeWithAuthRetry(service) { token ->
                service.unlikeVideo(apiKey(), "Bearer $token", "eq.$uid", "eq.${reel.id}")
            }
            false
        } else {
            executeWithAuthRetry(service) { token ->
                service.likeVideo(apiKey(), "Bearer $token", SupabaseVideoLikeDto(uid, reel.id))
            }
            if (reel.authorId != uid) {
                dispatchPushSilently(
                    service, reel.authorId, "like",
                    "$actorName curtiu o teu vídeo", reel.caption.take(80), reel.id
                )
            }
            true
        }
    }

    suspend fun recordVideoView(
        videoId: String,
        watchSeconds: Int = 5,
        completed: Boolean = false,
        replayed: Boolean = false
    ) {
        val uid = currentAuthenticatedUid() ?: return
        val service = api ?: return
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.recordVideoView(
                    apiKey(),
                    "Bearer $token",
                    SupabaseVideoViewDto(videoId, uid, watchSeconds, completed, replayed)
                )
            }
        }
    }

    suspend fun recordInterestSignals(topics: List<String>, delta: Double = 1.5) {
        if (topics.isEmpty()) return
        topics.forEach { raw ->
            val clean = raw.trim().lowercase()
            if (clean.isNotBlank()) {
                val cur = interestWeights[clean] ?: 0.0
                interestWeights[clean] = (cur + delta).coerceIn(0.5, 100.0)
            }
        }
        val service = api ?: return
        if (getEffectiveBearerToken() == null) return
        topics.take(4).forEach { topic ->
            runCatching {
                executeWithAuthRetry(service) { token ->
                    service.upsertInterestSignal(apiKey(), "Bearer $token", InterestSignalUpsertDto(topic, delta))
                }
            }
        }
    }

    fun currentInterestWeights(): Map<String, Double> = interestWeights.toMap()

    suspend fun fetchRecommendedVideoScoreHints(page: Int = 0, pageSize: Int = 15): Map<String, Double> {
        val service = api ?: return emptyMap()
        if (getEffectiveBearerToken() == null) return emptyMap()
        return runCatching {
            executeWithAuthRetry(service) { token ->
                service.getRecommendedVideos(
                    apiKey(),
                    "Bearer $token",
                    RecommendedVideosRequestDto(pageSize, page * pageSize)
                )
            }.body().orEmpty().associate { it.videoId to it.score }
        }.getOrDefault(emptyMap())
    }

    suspend fun fetchTrendingTopics(posts: List<FeedPost>, reels: List<VideoReel>): List<TrendingTopic> {
        val service = api
        val remote = if (service != null && getEffectiveBearerToken() != null) {
            runCatching {
                executeWithAuthRetry(service) { token ->
                    service.getTrendingTopics(apiKey(), "Bearer $token", TrendingTopicsRequestDto(8))
                }.body().orEmpty().map { TrendingTopic(it.tag, it.mentionsCount, it.trendScore) }
            }.getOrDefault(emptyList())
        } else emptyList()
        return remote.ifEmpty { SocializaAlgorithmEngine.computeTrendingTopics(posts, reels, limit = 8) }
    }

    suspend fun shareVideo(videoId: String) {
        val service = api ?: return
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.incrementVideoShare(apiKey(), "Bearer $token", VideoShareRpcRequest(videoId))
            }
        }
    }

    // =========================================================================
    // MENSAGENS REAIS & CONVERSAS
    // =========================================================================

    suspend fun startDirectConversation(targetUserId: String): Result<String> = runCatching {
        requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val res = executeWithAuthRetry(service) { token ->
            service.getOrCreateDirectConversation(apiKey(), "Bearer $token", DirectConversationRpcRequest(targetUserId))
        }
        if (!res.isSuccessful) {
            throw IllegalStateException("Não foi possível iniciar conversa (bloqueio ou erro HTTP ${res.code()}).")
        }
        res.body()?.trim('"') ?: throw IllegalStateException("Conversa não devolvida pelo servidor.")
    }

    suspend fun fetchConversations(): Result<List<ConversationItem>> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val service = api ?: return@runCatching emptyList()
        val res = executeWithAuthRetry(service) { token ->
            service.getConversations(apiKey(), "Bearer $token")
        }
        if (!res.isSuccessful) return@runCatching emptyList()
        val blockedIds = fetchBlockedIdsInternal(service, uid)

        res.body().orEmpty().mapNotNull { dto ->
            val otherMember = dto.members.firstOrNull { it.userId != uid }
            if (!dto.isGroup && otherMember != null && otherMember.userId in blockedIds) {
                return@mapNotNull null
            }
            val title = if (dto.isGroup) {
                dto.title?.ifBlank { "Grupo Socializa" } ?: "Grupo Socializa"
            } else {
                otherMember?.profile?.fullName?.ifBlank { otherMember.profile.username } ?: "Utilizador"
            }
            ConversationItem(
                id = dto.id,
                title = title,
                otherUserId = if (dto.isGroup) null else otherMember?.userId,
                isGroup = dto.isGroup,
                avatarUrl = if (dto.isGroup) dto.avatarUrl else otherMember?.profile?.avatarUrl,
                lastMessage = dto.lastMessagePreview.orEmpty().ifBlank { "Nova conversa" },
                timeLabel = formatRelativeTime(dto.lastMessageAt),
                unreadCount = 0,
                isOnline = otherMember?.profile?.isOnline == true,
                memberCount = dto.members.size.coerceAtLeast(1),
                messages = emptyList()
            )
        }
    }

    suspend fun fetchMessages(conversationId: String): Result<List<ChatMessage>> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val service = api ?: return@runCatching emptyList()
        val res = executeWithAuthRetry(service) { token ->
            service.getMessages(apiKey(), "Bearer $token", "eq.$conversationId")
        }
        val messages = res.body().orEmpty().map { it.toDomain(uid) }
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.markConversationRead(apiKey(), "Bearer $token", ConversationIdRpcRequest(conversationId))
            }
        }
        messages
    }

    suspend fun sendMessage(
        conversationId: String,
        body: String,
        type: MessageType,
        mediaUri: Uri? = null,
        localAudioFile: File? = null,
        recipientUserId: String? = null,
        senderName: String
    ): Result<ChatMessage> = runCatching {
        val uid = requireUserId()
        rateLimiter.checkAndRecord("send_message", maxPerMinute = 60)
        val service = api ?: throw IllegalStateException("Supabase não configurado.")

        var attachmentUrl: String? = null
        var attachmentName: String? = null
        if (mediaUri != null) {
            attachmentName = MediaUploadHelper.resolveDisplayName(context, mediaUri)
            val uploaded = if (type in listOf(MessageType.IMAGE, MessageType.VIDEO, MessageType.DOCUMENT, MessageType.AUDIO)) {
                withMediaAuthRetry(service) { token ->
                    when (type) {
                        MessageType.IMAGE -> MediaUploadHelper.uploadImageUri(context, service, token, uid, "chat", mediaUri)
                        MessageType.VIDEO -> MediaUploadHelper.uploadBinaryUri(context, service, token, uid, "chat", mediaUri, "video/mp4", "mp4")
                        MessageType.DOCUMENT -> MediaUploadHelper.uploadBinaryUri(context, service, token, uid, "chat", mediaUri, "application/pdf", "pdf")
                        MessageType.AUDIO -> MediaUploadHelper.uploadBinaryUri(context, service, token, uid, "chat", mediaUri, "audio/mp4", "m4a")
                        else -> Result.failure(IllegalStateException("Tipo não suportado"))
                    }
                }
            } else null
            attachmentUrl = uploaded?.publicUrl
        } else if (localAudioFile != null && localAudioFile.exists()) {
            attachmentName = localAudioFile.name
            val uploaded = withMediaAuthRetry(service) { token ->
                MediaUploadHelper.uploadLocalFile(service, token, uid, "chat", localAudioFile, "audio/mp4")
            }
            attachmentUrl = uploaded.publicUrl
        }

        val res = executeWithAuthRetry(service) { token ->
            service.sendMessage(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                body = SupabaseMessageInsertDto(
                    conversationId = conversationId,
                    senderId = uid,
                    messageType = type.name.lowercase(),
                    body = body.trim(),
                    attachmentUrl = attachmentUrl,
                    attachmentName = attachmentName
                )
            )
        }
        if (!res.isSuccessful) {
            val detail = extractHttpErrorDetail(res)
            throw IllegalStateException("Erro ao enviar mensagem (HTTP ${res.code()}${if (detail.isNotBlank()) ": $detail" else ""}).")
        }
        val created = res.body()?.firstOrNull()
            ?: throw IllegalStateException("Erro ao enviar mensagem (resposta vazia).")

        if (!recipientUserId.isNullOrBlank() && recipientUserId != uid) {
            dispatchPushSilently(
                service, recipientUserId, "message",
                senderName, body.ifBlank { attachmentName ?: "Novo anexo" }, conversationId
            )
        }

        created.toDomain(uid).copy(senderName = senderName)
    }.onFailure { ErrorMonitor.record(context, "send_message", it) }

    // =========================================================================
    // NOTIFICAÇÕES, MARKETPLACE, COMUNIDADES & MODERAÇÃO
    // =========================================================================

    suspend fun fetchNotifications(): Result<List<AppNotification>> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val service = api ?: return@runCatching emptyList()
        val res = executeWithAuthRetry(service) { token ->
            service.getNotifications(apiKey(), "Bearer $token", "eq.$uid")
        }
        res.body().orEmpty().map { dto ->
            val type = when (dto.eventType.lowercase()) {
                "follow" -> NotificationEventType.FOLLOW
                "like" -> NotificationEventType.LIKE
                "comment" -> NotificationEventType.COMMENT
                "comment_reply" -> NotificationEventType.COMMENT_REPLY
                "message" -> NotificationEventType.MESSAGE
                "mention" -> NotificationEventType.MENTION
                "invite" -> NotificationEventType.INVITE
                "live_stream" -> NotificationEventType.LIVE_STREAM
                else -> NotificationEventType.COMMENT
            }
            AppNotification(
                id = dto.id,
                type = type,
                title = dto.title,
                body = dto.body,
                referenceId = dto.referenceId,
                actorId = dto.actorId,
                timeLabel = formatRelativeTime(dto.createdAt),
                isRead = dto.isRead
            )
        }
    }

    suspend fun markAllNotificationsRead(): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching
        val service = api ?: return@runCatching
        executeWithAuthRetry(service) { token ->
            service.markAllNotificationsRead(apiKey(), "Bearer $token", "eq.$uid")
        }
    }

    suspend fun fetchProducts(): Result<List<MarketplaceProduct>> = runCatching {
        requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val service = api ?: return@runCatching emptyList()
        val res = executeWithAuthRetry(service) { token ->
            service.getProducts(apiKey(), "Bearer $token")
        }
        res.body().orEmpty().map { dto ->
            MarketplaceProduct(
                id = dto.id,
                sellerId = dto.sellerId,
                sellerName = dto.seller?.fullName?.ifBlank { dto.seller.username } ?: "Vendedor",
                title = dto.title,
                priceKz = dto.priceKz.toLong(),
                category = dto.category,
                province = dto.province,
                imageUrl = dto.imageUrl,
                description = dto.description
            )
        }
    }

    suspend fun createProduct(
        title: String,
        priceKz: Long,
        category: String,
        province: String,
        description: String,
        imageUri: Uri?,
        sellerName: String
    ): Result<MarketplaceProduct> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val imageUrl = if (imageUri != null) {
            runCatching {
                withMediaAuthRetry(service) { token ->
                    MediaUploadHelper.uploadImageUri(context, service, token, uid, "marketplace", imageUri)
                }.publicUrl
            }.getOrNull()
        } else null
        val res = executeWithAuthRetry(service) { token ->
            service.createProduct(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                body = SupabaseProductInsertDto(uid, title.trim(), description.trim(), category, priceKz, province, imageUrl)
            )
        }
        val created = res.body()?.firstOrNull()
            ?: throw IllegalStateException("Erro ao criar anúncio (HTTP ${res.code()}).")
        MarketplaceProduct(
            id = created.id,
            sellerId = uid,
            sellerName = sellerName,
            title = created.title,
            priceKz = created.priceKz.toLong(),
            category = created.category,
            province = created.province,
            imageUrl = created.imageUrl,
            description = created.description
        )
    }

    suspend fun fetchGroups(): Result<List<CommunityGroup>> = runCatching {
        val uid = requireUserId()
        if (getEffectiveBearerToken() == null) return@runCatching emptyList()
        val service = api ?: return@runCatching emptyList()
        val joinedIds = runCatching {
            executeWithAuthRetry(service) { token ->
                service.getMyGroupMemberships(apiKey(), "Bearer $token", "eq.$uid")
            }.body().orEmpty().map { it.groupId }.toSet()
        }.getOrDefault(emptySet())
        val res = executeWithAuthRetry(service) { token ->
            service.getGroups(apiKey(), "Bearer $token")
        }
        res.body().orEmpty().map { dto ->
            CommunityGroup(
                id = dto.id,
                ownerId = dto.ownerId,
                name = dto.name,
                province = dto.province ?: "Angola",
                description = dto.description,
                membersCount = dto.membersCount,
                isJoined = dto.id in joinedIds,
                coverUrl = dto.coverUrl
            )
        }
    }

    suspend fun toggleGroupMembership(groupId: String, currentlyJoined: Boolean): Result<Boolean> = runCatching {
        val uid = requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        if (currentlyJoined) {
            executeWithAuthRetry(service) { token ->
                service.leaveGroup(apiKey(), "Bearer $token", "eq.$groupId", "eq.$uid")
            }
            false
        } else {
            executeWithAuthRetry(service) { token ->
                service.joinGroup(apiKey(), "Bearer $token", SupabaseGroupMemberDto(groupId, uid))
            }
            true
        }
    }

    suspend fun submitReport(targetType: String, targetId: String, reason: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        rateLimiter.checkAndRecord("submit_report", maxPerMinute = 10)
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val res = executeWithAuthRetry(service) { token ->
            service.submitReport(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                body = SupabaseReportInsertDto(uid, targetType, targetId, reason)
            )
        }
        if (!res.isSuccessful) throw IllegalStateException("Erro ao enviar denúncia (HTTP ${res.code()}).")
    }

    suspend fun fetchReports(): List<ModerationReport> {
        if (getEffectiveBearerToken() == null) return emptyList()
        val service = api ?: return emptyList()
        return runCatching {
            executeWithAuthRetry(service) { token ->
                service.getReports(apiKey(), "Bearer $token")
            }.body().orEmpty().map { dto ->
                ModerationReport(
                    id = dto.id,
                    reporterId = dto.reporterId,
                    reporterUsername = dto.reporter?.username ?: "utilizador",
                    targetType = dto.targetType,
                    targetId = dto.targetId,
                    targetPreview = "${dto.targetType} (${dto.targetId.take(8)}…)",
                    reason = dto.reason,
                    status = dto.status,
                    createdAtLabel = formatRelativeTime(dto.createdAt)
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchAuditLogs(): List<AuditLogEntry> {
        if (getEffectiveBearerToken() == null) return emptyList()
        val service = api ?: return emptyList()
        return runCatching {
            executeWithAuthRetry(service) { token ->
                service.getAuditLogs(apiKey(), "Bearer $token")
            }.body().orEmpty().map { dto ->
                AuditLogEntry(
                    id = dto.id,
                    actorUsername = dto.actor?.username ?: "sistema",
                    action = dto.action.uppercase(),
                    targetSummary = dto.entityType,
                    timestampLabel = formatRelativeTime(dto.createdAt)
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun executeAdminModerationRemote(reportId: String?, action: String, targetId: String, reason: String): Result<Unit> = runCatching {
        requireUserId()
        val service = api ?: throw IllegalStateException("Supabase não configurado.")
        val res = executeWithAuthRetry(service) { token ->
            service.adminModerate(
                apiKey = apiKey(),
                firebaseBearer = "Bearer $token",
                payload = SupabaseAdminModerationDto(reportId, action, targetId, reason)
            )
        }
        if (!res.isSuccessful) throw IllegalStateException("Ação administrativa recusada (HTTP ${res.code()}).")
    }

    fun computeCreatorStats(user: UserProfile, posts: List<FeedPost>, reels: List<VideoReel>): CreatorStats {
        val myPosts = posts.filter { it.authorId == user.id }
        val myReels = reels.filter { it.authorId == user.id }
        return CreatorStats(
            totalPosts = maxOf(user.postsCount, myPosts.size),
            totalVideos = myReels.size,
            totalViews = myPosts.sumOf { it.viewsCount } + myReels.sumOf { it.viewsCount },
            totalLikes = myPosts.sumOf { it.likesCount } + myReels.sumOf { it.likesCount },
            totalComments = myPosts.sumOf { it.commentsCount } + myReels.sumOf { it.commentsCount },
            followersCount = user.followersCount,
            followingCount = user.followingCount
        )
    }

    private suspend fun syncDevicePushToken(uid: String, firebaseToken: String) {
        val fcmToken = preferences.fcmTokenFlow.first()
        val service = api
        if (service == null || fcmToken.isNullOrBlank()) return
        val deviceId = preferences.getOrCreateDeviceId()
        runCatching {
            executeWithAuthRetry(service, initialTokenOverride = firebaseToken) { token ->
                service.upsertDevicePushToken(
                    apiKey = apiKey(),
                    firebaseBearer = "Bearer $token",
                    body = SupabaseDeviceTokenUpsertDto(uid, fcmToken, "android", deviceId)
                )
            }
        }
    }

    private suspend fun dispatchPushSilently(
        service: SupabaseApiService,
        recipientId: String,
        eventType: String,
        title: String,
        body: String,
        referenceId: String?
    ) {
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.dispatchPushNotification(
                    apiKey = apiKey(),
                    firebaseBearer = "Bearer $token",
                    payload = SupabasePushDispatchDto(
                        recipientId = recipientId,
                        eventType = eventType,
                        title = title,
                        body = body,
                        referenceId = referenceId,
                        dispatchOnly = true
                    )
                )
            }
        }
    }

    private suspend fun fetchFollowingIdsInternal(service: SupabaseApiService, uid: String): Set<String> =
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.getFollowing(apiKey(), "Bearer $token", "eq.$uid")
            }.body().orEmpty().map { it.followingId }.toSet()
        }.getOrDefault(emptySet())

    private suspend fun fetchBlockedIdsInternal(service: SupabaseApiService, uid: String): Set<String> =
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.getBlocks(apiKey(), "Bearer $token", "eq.$uid")
            }.body().orEmpty().map { it.blockedId }.toSet()
        }.getOrDefault(emptySet())

    private suspend fun fetchLikedPostIdsInternal(service: SupabaseApiService, uid: String): Set<String> =
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.getUserLikes(apiKey(), "Bearer $token", "eq.$uid")
            }.body().orEmpty().map { it.postId }.toSet()
        }.getOrDefault(emptySet())

    private suspend fun fetchSavedPostIdsInternal(service: SupabaseApiService, uid: String): Set<String> =
        runCatching {
            executeWithAuthRetry(service) { token ->
                service.getUserSavedPosts(apiKey(), "Bearer $token", "eq.$uid")
            }.body().orEmpty().map { it.postId }.toSet()
        }.getOrDefault(emptySet())

    fun angolanProvinces(): List<String> = listOf(
        "Luanda", "Benguela", "Huambo", "Huíla", "Cabinda", "Malanje",
        "Namibe", "Cunene", "Bié", "Uíge", "Zaire", "Cuanza Norte",
        "Cuanza Sul", "Lunda Norte", "Lunda Sul", "Moxico", "Cuando Cubango", "Bengo"
    )

    // =========================================================================
    // MAPEADORES DTO <-> DOMÍNIO & CACHE
    // =========================================================================

    private fun SupabaseProfileDto.toDomain(defaultAvatar: String? = null) = UserProfile(
        id = id,
        username = username,
        fullName = fullName.ifBlank { username },
        bio = bio,
        avatarUri = avatarUrl ?: defaultAvatar,
        country = country,
        province = province,
        website = website.orEmpty(),
        isPrivate = isPrivate,
        isVerified = isVerified,
        verifiedUntilMs = parseIsoMillis(verifiedUntil),
        role = role,
        isSuspended = isSuspended,
        followersCount = followersCount,
        followingCount = followingCount,
        postsCount = postsCount,
        isOnline = isOnline
    )

    private fun SupabasePostDto.toDomain(
        isLiked: Boolean,
        isSaved: Boolean,
        isAuthorFollowed: Boolean
    ): FeedPost {
        val type = when (postType.lowercase()) {
            "video" -> PostType.VIDEO
            "carousel" -> PostType.CAROUSEL
            "text" -> PostType.TEXT
            else -> PostType.PHOTO
        }
        val vis = when (visibility.lowercase()) {
            "followers" -> PostVisibility.FOLLOWERS
            "private" -> PostVisibility.PRIVATE
            else -> PostVisibility.PUBLIC
        }
        val visualMedia = media.filter { !it.mediaType.equals("audio", ignoreCase = true) }
            .sortedBy { it.sortOrder }
            .map { it.publicUrl }
        val audioMedia = media.firstOrNull { it.mediaType.equals("audio", ignoreCase = true) }
        val authorUntil = parseIsoMillis(author?.verifiedUntil)
        val authorHasBadge = (author?.isVerified == true) || authorUntil > System.currentTimeMillis()
        return FeedPost(
            id = id,
            authorId = authorId,
            authorName = author?.fullName?.ifBlank { author.username } ?: "Utilizador",
            authorUsername = author?.username ?: "utilizador",
            authorVerified = authorHasBadge,
            authorAvatarUrl = author?.avatarUrl,
            postType = type,
            caption = caption,
            hashtags = hashtags,
            category = category,
            locationLabel = locationLabel.orEmpty(),
            visibility = vis,
            mediaUrls = visualMedia,
            musicTitle = if (audioMedia != null) "Música adicionada" else null,
            musicUrl = audioMedia?.publicUrl,
            likesCount = likesCount,
            commentsCount = commentsCount,
            sharesCount = sharesCount,
            viewsCount = viewsCount,
            isLiked = isLiked,
            isSaved = isSaved,
            isAuthorFollowed = isAuthorFollowed,
            createdAtIso = createdAt,
            createdAtLabel = formatRelativeTime(createdAt),
            editedAtLabel = editedAt?.let { "editado" }
        )
    }

    private fun SupabaseCommentDto.toDomain(isLiked: Boolean) = PostComment(
        id = id,
        authorId = authorId,
        authorName = author?.fullName?.ifBlank { author.username } ?: "Utilizador",
        authorUsername = author?.username ?: "utilizador",
        authorAvatarUrl = author?.avatarUrl,
        parentId = parentId,
        body = body,
        timeAgo = formatRelativeTime(createdAt),
        likesCount = likesCount,
        isLiked = isLiked
    )

    private fun SupabaseMessageDto.toDomain(myUid: String): ChatMessage {
        val type = when (messageType.lowercase()) {
            "image" -> MessageType.IMAGE
            "video" -> MessageType.VIDEO
            "audio" -> MessageType.AUDIO
            "document" -> MessageType.DOCUMENT
            "call_event" -> MessageType.CALL_EVENT
            else -> MessageType.TEXT
        }
        val status = when (deliveryStatus.lowercase()) {
            "read" -> DeliveryStatus.READ
            "delivered" -> DeliveryStatus.DELIVERED
            else -> DeliveryStatus.SENT
        }
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            senderName = sender?.fullName?.ifBlank { sender.username } ?: "Utilizador",
            body = body,
            type = type,
            timeLabel = formatRelativeTime(createdAt),
            deliveryStatus = status,
            isMine = senderId == myUid,
            attachmentUrl = attachmentUrl,
            attachmentName = attachmentName
        )
    }

    private fun UserProfile.toCacheEntity() = CachedProfileEntity(
        id = id,
        username = username,
        fullName = fullName,
        bio = bio,
        avatarUrl = avatarUri,
        country = country,
        province = province,
        website = website,
        isPrivate = isPrivate,
        isVerified = isVerified,
        verifiedUntilMs = verifiedUntilMs,
        role = role,
        followersCount = followersCount,
        followingCount = followingCount,
        postsCount = postsCount,
        cachedAtMs = System.currentTimeMillis()
    )

    private fun CachedProfileEntity.toDomain() = UserProfile(
        id = id,
        username = username,
        fullName = fullName,
        bio = bio,
        avatarUri = avatarUrl,
        country = country,
        province = province,
        website = website,
        isPrivate = isPrivate,
        isVerified = isVerified,
        verifiedUntilMs = verifiedUntilMs,
        role = role,
        followersCount = followersCount,
        followingCount = followingCount,
        postsCount = postsCount
    )

    private fun FeedPost.toCacheEntity(ownerUid: String) = CachedPostEntity(
        id = id,
        ownerUid = ownerUid,
        authorId = authorId,
        authorName = authorName,
        authorUsername = authorUsername,
        authorAvatarUrl = authorAvatarUrl,
        authorVerified = authorVerified,
        postType = postType.name,
        caption = caption,
        hashtagsCsv = hashtags.joinToString(","),
        locationLabel = locationLabel,
        visibility = visibility.name,
        mediaUrlsCsv = mediaUrls.joinToString("|"),
        musicTitle = musicTitle,
        musicUrl = musicUrl,
        likesCount = likesCount,
        commentsCount = commentsCount,
        sharesCount = sharesCount,
        viewsCount = viewsCount,
        isLiked = isLiked,
        isSaved = isSaved,
        createdAtIso = createdAtIso,
        cachedAtMs = System.currentTimeMillis()
    )

    private fun CachedPostEntity.toDomain() = FeedPost(
        id = id,
        authorId = authorId,
        authorName = authorName,
        authorUsername = authorUsername,
        authorVerified = authorVerified,
        authorAvatarUrl = authorAvatarUrl,
        postType = runCatching { PostType.valueOf(postType) }.getOrDefault(PostType.PHOTO),
        caption = caption,
        hashtags = hashtagsCsv.split(",").filter { it.isNotBlank() },
        locationLabel = locationLabel,
        visibility = runCatching { PostVisibility.valueOf(visibility) }.getOrDefault(PostVisibility.PUBLIC),
        mediaUrls = mediaUrlsCsv.split("|").filter { it.isNotBlank() },
        musicTitle = musicTitle,
        musicUrl = musicUrl,
        likesCount = likesCount,
        commentsCount = commentsCount,
        sharesCount = sharesCount,
        viewsCount = viewsCount,
        isLiked = isLiked,
        isSaved = isSaved,
        createdAtIso = createdAtIso,
        createdAtLabel = formatRelativeTime(createdAtIso)
    )

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun parseIsoMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            val ms = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(iso)?.time
            }.getOrNull()
            if (ms != null) return ms
        }
        return 0L
    }

    private fun formatRelativeTime(iso: String): String {
        val ts = parseIsoMillis(iso).takeIf { it > 0L } ?: return "Agora"
        val diffSec = ((System.currentTimeMillis() - ts) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60 -> "Agora"
            diffSec < 3600 -> "${diffSec / 60} min"
            diffSec < 86_400 -> "${diffSec / 3600} h"
            else -> "${diffSec / 86_400} d"
        }
    }
}
