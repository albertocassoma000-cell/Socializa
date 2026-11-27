package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionPreferences
import com.example.data.model.AppNotification
import com.example.data.model.AuditLogEntry
import com.example.data.model.AuthModeTab
import com.example.data.model.BackendStatus
import com.example.data.model.CommunityGroup
import com.example.data.model.CreatorStats
import com.example.data.model.FeedMode
import com.example.data.model.FeedPost
import com.example.data.model.MarketplaceProduct
import com.example.data.model.MessageType
import com.example.data.model.ModerationReport
import com.example.data.model.OtpAuthStep
import com.example.data.model.PostComment
import com.example.data.model.PostType
import com.example.data.model.PostVisibility
import com.example.data.model.SearchResultsBundle
import com.example.data.model.StoryItem
import com.example.data.model.ThemeMode
import com.example.data.model.TrendingTopic
import com.example.data.model.UserProfile
import com.example.data.model.VideoReel
import com.example.data.model.VideoTelemetry
import com.example.data.model.ConversationItem
import com.example.data.repository.AuthResult
import com.example.data.repository.MeuPaisRepository
import com.example.domain.algorithm.SocializaAlgorithmEngine
import com.example.services.FirebaseTelemetryManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class BottomTab(val route: String, val label: String) {
    HOME("home", "Início"),
    EXPLORE("explore", "Explorar"),
    CREATE("create", "Criar"),
    VIDEOS("videos", "Vídeos"),
    PROFILE("profile", "Perfil")
}

data class MeuPaisUiState(
    val isReady: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isLoadingAuth: Boolean = false,
    val isLoadingFeed: Boolean = false,
    val isLoadingMorePosts: Boolean = false,
    val isPublishing: Boolean = false,
    val hasMorePosts: Boolean = true,
    val feedMode: FeedMode = FeedMode.FOR_YOU,
    val selectedProvinceFilter: String = "Todas",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val currentUser: UserProfile? = null,
    val inspectedUser: UserProfile? = null,
    val discoveredUsers: List<UserProfile> = emptyList(),
    val stories: List<StoryItem> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val reels: List<VideoReel> = emptyList(),
    val conversations: List<ConversationItem> = emptyList(),
    val products: List<MarketplaceProduct> = emptyList(),
    val groups: List<CommunityGroup> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val reports: List<ModerationReport> = emptyList(),
    val auditLogs: List<AuditLogEntry> = emptyList(),
    val blockedAuthorIds: Set<String> = emptySet(),
    val creatorStats: CreatorStats = CreatorStats(),
    val searchQuery: String = "",
    val selectedExploreCategory: String? = null,
    val selectedHashtag: String? = null,
    val selectedMarketCategory: String = "Tudo",
    val activeStory: StoryItem? = null,
    val activeCommentsPostId: String? = null,
    val activeCommentsVideoId: String? = null,
    val activeComments: List<PostComment> = emptyList(),
    val isLoadingComments: Boolean = false,
    val activeConversationId: String? = null,
    val activeCallTitle: String? = null,
    val activeCallIsVideo: Boolean = false,
    val adsWatchedCount: Int = 0,
    val isClaimingVerificationBadge: Boolean = false,
    val authMode: AuthModeTab = AuthModeTab.SIGN_IN,
    val otpStep: OtpAuthStep = OtpAuthStep.NONE,
    val pendingAuthEmail: String = "",
    val pendingFullName: String = "",
    val pendingPassword: String = "",
    val otpExpiresAtMs: Long = 0L,
    val otpRemainingSeconds: Int = 0,
    val otpResendCooldownSeconds: Int = 0,
    val authErrorMessage: String? = null,
    val trendingTopics: List<TrendingTopic> = emptyList(),
    val suggestedPeople: List<UserProfile> = emptyList(),
    val unifiedSearchResults: SearchResultsBundle = SearchResultsBundle(),
    val videoTelemetryById: Map<String, VideoTelemetry> = emptyMap(),
    val backendStatus: BackendStatus? = null,
    val snackbarMessage: String? = null
) {
    val unreadNotificationsCount: Int
        get() = notifications.count { !it.isRead }

    val unreadMessagesCount: Int
        get() = conversations.sumOf { it.unreadCount }

    val visiblePosts: List<FeedPost>
        get() = posts.filter { it.authorId !in blockedAuthorIds }

    val visibleStories: List<StoryItem>
        get() = stories.filter { it.authorId !in blockedAuthorIds }
}

class MeuPaisViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val PAGE_SIZE = 15
        internal var testRepositoryFactory: ((Application) -> MeuPaisRepository)? = null
    }

    private val appContext = application.applicationContext ?: application
    private val preferences = SessionPreferences(appContext)
    private val repository = testRepositoryFactory?.invoke(application)
        ?: MeuPaisRepository(appContext, preferences)

    private val _uiState = MutableStateFlow(MeuPaisUiState())
    val uiState: StateFlow<MeuPaisUiState> = _uiState.asStateFlow()

    val provinces: List<String> = repository.angolanProvinces()
    private var currentPostsPage = 0
    private val sessionSyncMutex = Mutex()
    private var syncedSessionUid: String? = null
    @Volatile private var isExplicitSignInActive: Boolean = false
    private var activeLoadAllJob: Job? = null
    private var otpCountdownJob: Job? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val fbUser = auth.currentUser
        if (fbUser == null) {
            syncedSessionUid = null
            currentPostsPage = 0
            activeLoadAllJob?.cancel()
            repository.stopRealtime()
            _uiState.update {
                MeuPaisUiState(
                    isReady = true,
                    isAuthenticated = false,
                    themeMode = it.themeMode,
                    backendStatus = it.backendStatus
                )
            }
        } else if (!isExplicitSignInActive) {
            viewModelScope.launch {
                ensureSessionSyncedAndLoaded(forceReload = false)
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                FirebaseTelemetryManager.initializeComplementaryServices(appContext, preferences)
            }
        }
        if (FirebaseTelemetryManager.isFirebaseInitialized(appContext)) {
            runCatching { Firebase.auth.addAuthStateListener(authStateListener) }
        }

        viewModelScope.launch {
            val savedTheme = runCatching { preferences.themeModeFlow.first() }.getOrDefault(ThemeMode.DARK)
            val status = runCatching { repository.getBackendStatus() }.getOrNull()
            _uiState.update { current ->
                current.copy(
                    isReady = true,
                    themeMode = savedTheme,
                    backendStatus = status
                )
            }

            if (repository.currentAuthenticatedUid() != null) {
                runCatching { ensureSessionSyncedAndLoaded(forceReload = false) }
            } else {
                isExplicitSignInActive = true
                val auto = try {
                    runCatching { repository.attemptAutoSignIn(::onRealtimeEvent) }.getOrNull()
                } finally {
                    isExplicitSignInActive = false
                }
                if (auto is AuthResult.Success) {
                    sessionSyncMutex.withLock {
                        syncedSessionUid = auto.profile.id
                    }
                    val updatedStatus = runCatching { repository.getBackendStatus() }.getOrNull()
                    val savedAdsCount = runCatching { preferences.getAdsWatchedCount(auto.profile.id) }.getOrDefault(0)
                    _uiState.update {
                        it.copy(
                            isAuthenticated = true,
                            currentUser = auto.profile,
                            adsWatchedCount = savedAdsCount,
                            backendStatus = updatedStatus,
                            snackbarMessage = auto.message
                        )
                    }
                    loadAllOnlineData(resetFeed = true)
                }
            }
        }
    }

    private suspend fun ensureSessionSyncedAndLoaded(forceReload: Boolean) {
        val uid = repository.currentAuthenticatedUid() ?: return
        val shouldSync = sessionSyncMutex.withLock {
            if (!forceReload && syncedSessionUid == uid) {
                false
            } else {
                syncedSessionUid = uid
                true
            }
        }
        if (!shouldSync) return

        val synced = repository.syncCurrentFirebaseUser(::onRealtimeEvent)
        val status = repository.getBackendStatus()
        if (synced != null) {
            val savedAdsCount = preferences.getAdsWatchedCount(synced.id)
            _uiState.update {
                it.copy(
                    isReady = true,
                    isAuthenticated = true,
                    currentUser = synced,
                    adsWatchedCount = savedAdsCount,
                    backendStatus = status
                )
            }
            loadAllOnlineData(resetFeed = true)
        } else {
            sessionSyncMutex.withLock {
                if (syncedSessionUid == uid) syncedSessionUid = null
            }
            _uiState.update { it.copy(backendStatus = status) }
        }
    }

    fun retryBackendSync() {
        viewModelScope.launch {
            repository.resetAuthRecoveryForRetry()
            if (repository.currentAuthenticatedUid() != null && runCatching { Firebase.auth.currentUser }.getOrNull() != null) {
                ensureSessionSyncedAndLoaded(forceReload = true)
            } else if (_uiState.value.isAuthenticated) {
                loadAllOnlineData(resetFeed = true)
            }
        }
    }

    override fun onCleared() {
        runCatching { Firebase.auth.removeAuthStateListener(authStateListener) }
        otpCountdownJob?.cancel()
        repository.stopRealtime()
        super.onCleared()
    }

    private fun onRealtimeEvent(table: String, @Suppress("UNUSED_PARAMETER") eventType: String, @Suppress("UNUSED_PARAMETER") payload: String) {
        if (!_uiState.value.isAuthenticated) return
        viewModelScope.launch {
            when (table) {
                "posts", "likes" -> refreshFeedOnly()
                "comments" -> {
                    _uiState.value.activeCommentsPostId?.let { refreshComments(postId = it) }
                    _uiState.value.activeCommentsVideoId?.let { refreshComments(videoId = it) }
                }
                "messages", "conversations" -> {
                    loadConversations()
                    _uiState.value.activeConversationId?.let { loadMessages(it) }
                }
                "notifications" -> loadNotifications()
                "stories" -> loadStories()
                "videos" -> loadVideos()
                "follows" -> refreshCurrentUserProfile()
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferences.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
        }
    }

    fun setAuthMode(tab: AuthModeTab) {
        _uiState.update {
            it.copy(
                authMode = tab,
                authErrorMessage = null
            )
        }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authErrorMessage = null) }
    }

    fun cancelOtpStep() {
        otpCountdownJob?.cancel()
        otpCountdownJob = null
        _uiState.update {
            it.copy(
                otpStep = OtpAuthStep.NONE,
                pendingPassword = "",
                otpExpiresAtMs = 0L,
                otpRemainingSeconds = 0,
                otpResendCooldownSeconds = 0,
                authErrorMessage = null
            )
        }
    }

    private fun startOtpCountdown(expiresInSeconds: Int, resendCooldownSeconds: Int = 45) {
        otpCountdownJob?.cancel()
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        _uiState.update {
            it.copy(
                otpExpiresAtMs = expiresAt,
                otpRemainingSeconds = expiresInSeconds,
                otpResendCooldownSeconds = resendCooldownSeconds
            )
        }
        otpCountdownJob = viewModelScope.launch {
            var remaining = expiresInSeconds
            var cooldown = resendCooldownSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining = ((expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
                if (cooldown > 0) cooldown--
                _uiState.update { state ->
                    if (state.otpStep == OtpAuthStep.NONE) {
                        state
                    } else {
                        state.copy(
                            otpRemainingSeconds = remaining,
                            otpResendCooldownSeconds = cooldown
                        )
                    }
                }
            }
        }
    }

    private fun isValidEmailFormat(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.contains("@") && trimmed.substringAfter("@").contains(".") && trimmed.length >= 5
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAuth = true, authErrorMessage = null) }
            isExplicitSignInActive = true
            val res = try {
                repository.signInWithGoogle(::onRealtimeEvent)
            } finally {
                isExplicitSignInActive = false
            }
            handleAuthResult(res, cancelMsg = "Login com Google cancelado.")
        }
    }

    fun signInWithEmail(email: String, password: String) {
        val cleanEmail = email.trim()
        if (!isValidEmailFormat(cleanEmail)) {
            _uiState.update { it.copy(authErrorMessage = "Introduz um e-mail válido.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(authErrorMessage = "A senha deve ter pelo menos 6 caracteres.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAuth = true, authErrorMessage = null) }
            isExplicitSignInActive = true
            val res = try {
                repository.signInWithEmailPassword(cleanEmail, password, ::onRealtimeEvent)
            } finally {
                isExplicitSignInActive = false
            }
            handleAuthResult(res, cancelMsg = "Autenticação cancelada.")
        }
    }

    fun startEmailRegistration(fullName: String, email: String, password: String) {
        val cleanName = fullName.trim()
        val cleanEmail = email.trim()
        if (cleanName.length < 2) {
            _uiState.update { it.copy(authErrorMessage = "Introduz o teu nome completo.") }
            return
        }
        if (!isValidEmailFormat(cleanEmail)) {
            _uiState.update { it.copy(authErrorMessage = "Introduz um e-mail válido.") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(authErrorMessage = "A senha deve ter pelo menos 6 caracteres.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAuth = true, authErrorMessage = null) }
            repository.sendRegistrationOtp(cleanName, cleanEmail)
                .onSuccess { ttlSeconds ->
                    _uiState.update {
                        it.copy(
                            isLoadingAuth = false,
                            otpStep = OtpAuthStep.REGISTER_CODE,
                            pendingFullName = cleanName,
                            pendingAuthEmail = cleanEmail,
                            pendingPassword = password,
                            authErrorMessage = null,
                            snackbarMessage = "Código de 6 dígitos enviado para $cleanEmail (válido por 5 min)."
                        )
                    }
                    startOtpCountdown(ttlSeconds)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Não foi possível enviar o código de verificação."
                    _uiState.update {
                        it.copy(isLoadingAuth = false, authErrorMessage = msg, snackbarMessage = msg)
                    }
                }
        }
    }

    fun confirmRegistrationOtp(otp: String) {
        val state = _uiState.value
        val cleanOtp = otp.filter { it.isDigit() }
        if (cleanOtp.length != 6) {
            _uiState.update { it.copy(authErrorMessage = "Introduz o código de 6 dígitos recebido no teu e-mail.") }
            return
        }
        if (state.otpRemainingSeconds <= 0) {
            _uiState.update { it.copy(authErrorMessage = "O código expirou (limite de 5 minutos). Pede um novo código.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAuth = true, authErrorMessage = null) }
            isExplicitSignInActive = true
            val res = try {
                repository.verifyRegistrationOtpAndSignIn(
                    fullName = state.pendingFullName,
                    email = state.pendingAuthEmail,
                    password = state.pendingPassword,
                    otp = cleanOtp,
                    onRealtime = ::onRealtimeEvent
                )
            } finally {
                isExplicitSignInActive = false
            }
            if (res is AuthResult.Success) {
                otpCountdownJob?.cancel()
                otpCountdownJob = null
            }
            handleAuthResult(res, cancelMsg = "Registo cancelado.")
        }
    }

    fun resendRegistrationOtp() {
        val state = _uiState.value
        if (state.otpResendCooldownSeconds > 0 || state.pendingAuthEmail.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAuth = true, authErrorMessage = null) }
            repository.sendRegistrationOtp(state.pendingFullName, state.pendingAuthEmail)
                .onSuccess { ttlSeconds ->
                    _uiState.update {
                        it.copy(
                            isLoadingAuth = false,
                            authErrorMessage = null,
                            snackbarMessage = "Novo código enviado para ${state.pendingAuthEmail}."
                        )
                    }
                    startOtpCountdown(ttlSeconds)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Erro ao reenviar código."
                    _uiState.update { it.copy(isLoadingAuth = false, authErrorMessage = msg) }
                }
        }
    }

    fun startPasswordReset(email: String) {
        val cleanEmail = email.trim()
        if (!isValidEmailFormat(cleanEmail)) {
            _uiState.update { it.copy(authErrorMessage = "Introduz o e-mail da tua conta.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAuth = true, authErrorMessage = null) }
            repository.sendPasswordResetOtp(cleanEmail)
                .onSuccess { ttlSeconds ->
                    _uiState.update {
                        it.copy(
                            isLoadingAuth = false,
                            otpStep = OtpAuthStep.RESET_CODE,
                            pendingAuthEmail = cleanEmail,
                            authErrorMessage = null,
                            snackbarMessage = "Código de recuperação de 6 dígitos enviado para $cleanEmail."
                        )
                    }
                    startOtpCountdown(ttlSeconds)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Não foi possível enviar o código de recuperação."
                    _uiState.update {
                        it.copy(isLoadingAuth = false, authErrorMessage = msg, snackbarMessage = msg)
                    }
                }
        }
    }

    fun confirmPasswordReset(otp: String, newPassword: String) {
        val state = _uiState.value
        val cleanOtp = otp.filter { it.isDigit() }
        if (cleanOtp.length != 6) {
            _uiState.update { it.copy(authErrorMessage = "Introduz o código de 6 dígitos recebido no teu e-mail.") }
            return
        }
        if (newPassword.length < 6) {
            _uiState.update { it.copy(authErrorMessage = "A nova senha deve ter pelo menos 6 caracteres.") }
            return
        }
        if (state.otpRemainingSeconds <= 0) {
            _uiState.update { it.copy(authErrorMessage = "O código expirou (limite de 5 minutos). Pede um novo código.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAuth = true, authErrorMessage = null) }
            repository.confirmPasswordResetWithOtp(state.pendingAuthEmail, cleanOtp, newPassword)
                .onSuccess { msg ->
                    otpCountdownJob?.cancel()
                    otpCountdownJob = null
                    _uiState.update {
                        it.copy(
                            isLoadingAuth = false,
                            authMode = AuthModeTab.SIGN_IN,
                            otpStep = OtpAuthStep.NONE,
                            pendingPassword = "",
                            otpExpiresAtMs = 0L,
                            otpRemainingSeconds = 0,
                            otpResendCooldownSeconds = 0,
                            authErrorMessage = null,
                            snackbarMessage = msg
                        )
                    }
                }
                .onFailure { err ->
                    val msg = err.message ?: "Código inválido ou expirado."
                    _uiState.update { it.copy(isLoadingAuth = false, authErrorMessage = msg) }
                }
        }
    }

    fun resendPasswordResetOtp() {
        val state = _uiState.value
        if (state.otpResendCooldownSeconds > 0 || state.pendingAuthEmail.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAuth = true, authErrorMessage = null) }
            repository.sendPasswordResetOtp(state.pendingAuthEmail)
                .onSuccess { ttlSeconds ->
                    _uiState.update {
                        it.copy(
                            isLoadingAuth = false,
                            authErrorMessage = null,
                            snackbarMessage = "Novo código de recuperação enviado para ${state.pendingAuthEmail}."
                        )
                    }
                    startOtpCountdown(ttlSeconds)
                }
                .onFailure { err ->
                    val msg = err.message ?: "Erro ao reenviar código."
                    _uiState.update { it.copy(isLoadingAuth = false, authErrorMessage = msg) }
                }
        }
    }

    private suspend fun handleAuthResult(res: AuthResult, cancelMsg: String) {
        when (res) {
            is AuthResult.Success -> {
                sessionSyncMutex.withLock {
                    syncedSessionUid = res.profile.id
                }
                val status = repository.getBackendStatus()
                val savedAdsCount = preferences.getAdsWatchedCount(res.profile.id)
                _uiState.update {
                    it.copy(
                        isLoadingAuth = false,
                        isAuthenticated = true,
                        currentUser = res.profile,
                        adsWatchedCount = savedAdsCount,
                        otpStep = OtpAuthStep.NONE,
                        pendingPassword = "",
                        otpExpiresAtMs = 0L,
                        otpRemainingSeconds = 0,
                        otpResendCooldownSeconds = 0,
                        authErrorMessage = null,
                        backendStatus = status,
                        snackbarMessage = res.message
                    )
                }
                loadAllOnlineData(resetFeed = true)
            }
            is AuthResult.Cancelled -> {
                _uiState.update {
                    it.copy(isLoadingAuth = false, snackbarMessage = cancelMsg)
                }
            }
            is AuthResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoadingAuth = false,
                        authErrorMessage = res.message,
                        snackbarMessage = res.message
                    )
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            otpCountdownJob?.cancel()
            otpCountdownJob = null
            sessionSyncMutex.withLock {
                syncedSessionUid = null
            }
            currentPostsPage = 0
            repository.signOut()
            val status = repository.getBackendStatus()
            _uiState.update {
                MeuPaisUiState(
                    isReady = true,
                    isAuthenticated = false,
                    themeMode = it.themeMode,
                    backendStatus = status,
                    snackbarMessage = "Sessão terminada com segurança."
                )
            }
        }
    }

    internal fun bindAuthenticatedUserForTest(profile: UserProfile, loadData: Boolean = false) {
        syncedSessionUid = profile.id
        _uiState.update {
            it.copy(isReady = true, isAuthenticated = true, currentUser = profile)
        }
        if (loadData) {
            loadAllOnlineData(resetFeed = true)
        }
    }

    // =========================================================================
    // CARREGAMENTO ONLINE & PAGINAÇÃO
    // =========================================================================

    fun loadAllOnlineData(resetFeed: Boolean = true) {
        if (!_uiState.value.isAuthenticated) return
        activeLoadAllJob?.cancel()
        activeLoadAllJob = viewModelScope.launch {
            if (repository.hasAuthRejection()) {
                val uid = _uiState.value.currentUser?.id
                val cached = if (uid != null) repository.loadCachedPostsForUi(uid) else emptyList()
                val latestStatus = repository.getBackendStatus()
                _uiState.update {
                    it.copy(
                        isLoadingFeed = false,
                        posts = if (cached.isNotEmpty()) cached else it.posts,
                        backendStatus = latestStatus
                    )
                }
                return@launch
            }
            if (resetFeed) refreshFeedOnly()
            val blocked = repository.fetchBlockedIds()
            _uiState.update { it.copy(blockedAuthorIds = blocked) }
            loadStories()
            loadVideos()
            loadConversations()
            loadNotifications()
            loadMarketplaceAndGroups()
            searchUsers(_uiState.value.searchQuery)
            val user = _uiState.value.currentUser
            if (user != null) {
                if (user.role in listOf("admin", "moderator")) {
                    val reports = repository.fetchReports()
                    val logs = repository.fetchAuditLogs()
                    _uiState.update { it.copy(reports = reports, auditLogs = logs) }
                }
                recomputeStats()
            }
            val latestStatus = repository.getBackendStatus()
            _uiState.update { it.copy(backendStatus = latestStatus) }
            refreshTrendingTopics()
            recomputeAlgorithmDerivedState()
        }
    }

    private suspend fun refreshTrendingTopics() {
        val state = _uiState.value
        val topics = repository.fetchTrendingTopics(state.visiblePosts, state.reels)
        _uiState.update { it.copy(trendingTopics = topics) }
        recomputeAlgorithmDerivedState()
    }

    fun refreshFeedOnly() {
        if (!_uiState.value.isAuthenticated) return
        viewModelScope.launch {
            if (repository.hasAuthRejection()) {
                val uid = _uiState.value.currentUser?.id
                val cached = if (uid != null) repository.loadCachedPostsForUi(uid) else emptyList()
                val latestStatus = repository.getBackendStatus()
                _uiState.update {
                    it.copy(
                        isLoadingFeed = false,
                        posts = if (cached.isNotEmpty()) cached else it.posts,
                        backendStatus = latestStatus
                    )
                }
                recomputeAlgorithmDerivedState()
                return@launch
            }
            currentPostsPage = 0
            _uiState.update { it.copy(isLoadingFeed = true) }
            val state = _uiState.value
            val result = repository.fetchFeedPosts(
                mode = state.feedMode,
                page = 0,
                pageSize = PAGE_SIZE,
                provinceFilter = state.selectedProvinceFilter.takeIf { it != "Todas" },
                categoryFilter = state.selectedExploreCategory,
                currentUserProfile = state.currentUser
            )
            val latestStatus = repository.getBackendStatus()
            result.onSuccess { fetched ->
                _uiState.update {
                    it.copy(
                        isLoadingFeed = false,
                        posts = fetched,
                        hasMorePosts = fetched.size >= PAGE_SIZE,
                        backendStatus = latestStatus
                    )
                }
                recomputeStats()
                recomputeAlgorithmDerivedState()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingFeed = false,
                        backendStatus = latestStatus,
                        snackbarMessage = if (repository.hasAuthRejection()) {
                            it.snackbarMessage
                        } else {
                            err.message ?: "Erro ao carregar feed."
                        }
                    )
                }
            }
        }
    }

    fun loadMorePosts() {
        val state = _uiState.value
        if (!state.isAuthenticated || state.isLoadingMorePosts || !state.hasMorePosts || repository.hasAuthRejection()) return
        viewModelScope.launch {
            val nextPage = currentPostsPage + 1
            _uiState.update { it.copy(isLoadingMorePosts = true) }
            val result = repository.fetchFeedPosts(
                mode = state.feedMode,
                page = nextPage,
                pageSize = PAGE_SIZE,
                provinceFilter = state.selectedProvinceFilter.takeIf { it != "Todas" },
                categoryFilter = state.selectedExploreCategory,
                currentUserProfile = state.currentUser
            )
            result.onSuccess { more ->
                currentPostsPage = nextPage
                _uiState.update { cur ->
                    val existingIds = cur.posts.mapTo(HashSet()) { it.id }
                    val merged = cur.posts + more.filter { it.id !in existingIds }
                    cur.copy(
                        isLoadingMorePosts = false,
                        posts = merged,
                        hasMorePosts = more.size >= PAGE_SIZE
                    )
                }
                recomputeAlgorithmDerivedState()
            }.onFailure {
                _uiState.update { it.copy(isLoadingMorePosts = false) }
            }
        }
    }

    fun setFeedMode(mode: FeedMode) {
        if (_uiState.value.feedMode == mode) return
        _uiState.update { it.copy(feedMode = mode) }
        refreshFeedOnly()
    }

    fun setProvinceFilter(province: String) {
        _uiState.update { it.copy(selectedProvinceFilter = province) }
        refreshFeedOnly()
    }

    private suspend fun refreshCurrentUserProfile() {
        val uid = _uiState.value.currentUser?.id ?: return
        repository.fetchUserProfile(uid).onSuccess { refreshed ->
            _uiState.update { it.copy(currentUser = refreshed) }
            recomputeStats()
        }
    }

    private fun recomputeStats() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        _uiState.update {
            it.copy(creatorStats = repository.computeCreatorStats(user, state.posts, state.reels))
        }
    }

    private fun recomputeAlgorithmDerivedState() {
        val state = _uiState.value
        val user = state.currentUser ?: return
        val weights = repository.currentInterestWeights()
        val topics = if (state.trendingTopics.isNotEmpty()) {
            state.trendingTopics
        } else {
            SocializaAlgorithmEngine.computeTrendingTopics(state.visiblePosts, state.reels)
        }
        val suggestions = SocializaAlgorithmEngine.suggestPeople(
            candidates = state.discoveredUsers,
            currentUser = user,
            posts = state.visiblePosts,
            interestWeights = weights
        )
        val unified = SocializaAlgorithmEngine.searchUnified(
            query = state.searchQuery,
            people = state.discoveredUsers,
            posts = state.visiblePosts,
            reels = state.reels,
            groups = state.groups,
            products = state.products,
            trendingTopics = topics
        )
        _uiState.update {
            it.copy(
                trendingTopics = topics,
                suggestedPeople = suggestions,
                unifiedSearchResults = unified
            )
        }
    }

    // =========================================================================
    // PUBLICAÇÕES (CRIAR, EDITAR, ELIMINAR, CURTIR, PARTILHAR, GUARDAR, VER)
    // =========================================================================

    fun createPost(
        caption: String,
        postType: PostType,
        category: String?,
        location: String,
        hashtagsRaw: String,
        visibility: PostVisibility,
        mediaUris: List<Uri>,
        publishAlsoToReels: Boolean,
        audioUri: Uri? = null,
        audioTitle: String? = null,
        onComplete: () -> Unit = {}
    ) {
        val user = _uiState.value.currentUser ?: return
        val parsedTags = hashtagsRaw
            .split(" ", ",", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("#")) it else "#$it" }

        viewModelScope.launch {
            _uiState.update { it.copy(isPublishing = true) }
            if (postType == PostType.STORY) {
                val uri = mediaUris.firstOrNull()
                if (uri == null) {
                    _uiState.update { it.copy(isPublishing = false, snackbarMessage = "Seleciona uma imagem ou vídeo para o Story.") }
                    return@launch
                }
                repository.createStory(caption, uri, user).onSuccess { story ->
                    _uiState.update { state ->
                        state.copy(
                            isPublishing = false,
                            stories = listOf(story) + state.stories,
                            snackbarMessage = "Story publicado (válido por 24h)!"
                        )
                    }
                    onComplete()
                }.onFailure { err ->
                    _uiState.update { it.copy(isPublishing = false, snackbarMessage = err.message) }
                }
                return@launch
            }

            repository.createPost(
                caption = caption,
                postType = postType,
                category = category,
                location = location,
                hashtags = parsedTags,
                visibility = visibility,
                mediaUris = mediaUris,
                publishAlsoToReels = publishAlsoToReels,
                currentUser = user,
                audioUri = audioUri,
                audioTitle = audioTitle
            ).onSuccess { createdPost ->
                _uiState.update { state ->
                    state.copy(
                        isPublishing = false,
                        posts = listOf(createdPost) + state.posts,
                        currentUser = user.copy(postsCount = user.postsCount + 1),
                        snackbarMessage = "Publicação partilhada!"
                    )
                }
                if (publishAlsoToReels || postType == PostType.VIDEO) {
                    loadVideos()
                }
                recomputeStats()
                recomputeAlgorithmDerivedState()
                onComplete()
            }.onFailure { err ->
                _uiState.update { it.copy(isPublishing = false, snackbarMessage = err.message ?: "Erro ao publicar.") }
            }
        }
    }

    fun editPost(postId: String, caption: String, hashtagsRaw: String, location: String, visibility: PostVisibility) {
        val tags = hashtagsRaw.split(" ", ",", ";").map { it.trim() }.filter { it.isNotBlank() }
            .map { if (it.startsWith("#")) it else "#$it" }
        viewModelScope.launch {
            repository.editPost(postId, caption, tags, location, visibility).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.map { p ->
                            if (p.id == postId) p.copy(caption = caption.trim(), hashtags = tags, locationLabel = location, visibility = visibility, editedAtLabel = "editado")
                            else p
                        },
                        snackbarMessage = "Publicação atualizada!"
                    )
                }
            }.onFailure { err ->
                showMessage(err.message ?: "Erro ao editar publicação.")
            }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            repository.deletePost(postId).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.filterNot { it.id == postId },
                        currentUser = state.currentUser?.let { it.copy(postsCount = (it.postsCount - 1).coerceAtLeast(0)) },
                        snackbarMessage = "Publicação eliminada."
                    )
                }
                recomputeStats()
                recomputeAlgorithmDerivedState()
            }.onFailure { err ->
                showMessage(err.message ?: "Erro ao eliminar publicação.")
            }
        }
    }

    fun togglePostLike(postId: String) {
        val user = _uiState.value.currentUser ?: return
        val target = _uiState.value.posts.find { it.id == postId } ?: return
        val optimisticLiked = !target.isLiked
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map { p ->
                    if (p.id == postId) p.copy(isLiked = optimisticLiked, likesCount = (p.likesCount + if (optimisticLiked) 1 else -1).coerceAtLeast(0))
                    else p
                }
            )
        }
        viewModelScope.launch {
            if (optimisticLiked) {
                val topics = SocializaAlgorithmEngine.extractInterestTopics(target.caption, target.hashtags, target.category)
                repository.recordInterestSignals(topics, delta = 2.0)
            }
            repository.togglePostLike(target, user.fullName).onFailure { err ->
                // Reverte se falhar
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.map { p -> if (p.id == postId) target else p },
                        snackbarMessage = err.message ?: "Erro ao atualizar curtida."
                    )
                }
            }
        }
    }

    fun togglePostSave(postId: String) {
        val target = _uiState.value.posts.find { it.id == postId } ?: return
        val newSaved = !target.isSaved
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map { if (it.id == postId) it.copy(isSaved = newSaved) else it },
                snackbarMessage = if (newSaved) "Publicação guardada." else "Removida dos guardados."
            )
        }
        viewModelScope.launch {
            if (newSaved) {
                val topics = SocializaAlgorithmEngine.extractInterestTopics(target.caption, target.hashtags, target.category)
                repository.recordInterestSignals(topics, delta = 2.5)
            }
            repository.togglePostSave(postId, target.isSaved)
        }
    }

    fun sharePost(postId: String) {
        val target = _uiState.value.posts.find { it.id == postId }
        _uiState.update { state ->
            state.copy(
                posts = state.posts.map { if (it.id == postId) it.copy(sharesCount = it.sharesCount + 1) else it },
                snackbarMessage = "Partilha registada!"
            )
        }
        viewModelScope.launch {
            if (target != null) {
                val topics = SocializaAlgorithmEngine.extractInterestTopics(target.caption, target.hashtags, target.category)
                repository.recordInterestSignals(topics, delta = 3.0)
            }
            repository.sharePost(postId)
        }
    }

    fun markPostViewed(postId: String) {
        viewModelScope.launch { repository.recordPostView(postId) }
    }

    // =========================================================================
    // COMENTÁRIOS REAIS, RESPOSTAS & CURTIDAS EM COMENTÁRIOS
    // =========================================================================

    fun openComments(postId: String? = null, videoId: String? = null) {
        _uiState.update {
            it.copy(
                activeCommentsPostId = postId,
                activeCommentsVideoId = videoId,
                activeComments = emptyList(),
                isLoadingComments = postId != null || videoId != null
            )
        }
        if (postId != null || videoId != null) {
            viewModelScope.launch { refreshComments(postId, videoId) }
        }
    }

    private suspend fun refreshComments(postId: String? = null, videoId: String? = null) {
        repository.fetchComments(postId, videoId).onSuccess { list ->
            _uiState.update { state ->
                if (state.activeCommentsPostId != postId || state.activeCommentsVideoId != videoId) {
                    state
                } else {
                    val missingLocal = state.activeComments.filter { existing ->
                        list.none { it.id == existing.id }
                    }
                    state.copy(
                        activeComments = list + missingLocal,
                        isLoadingComments = false
                    )
                }
            }
        }.onFailure {
            _uiState.update { state ->
                if (state.activeCommentsPostId == postId && state.activeCommentsVideoId == videoId) {
                    state.copy(isLoadingComments = false)
                } else {
                    state
                }
            }
        }
    }

    fun addComment(body: String, parentComment: PostComment? = null) {
        val user = _uiState.value.currentUser ?: return
        val postId = _uiState.value.activeCommentsPostId
        val videoId = _uiState.value.activeCommentsVideoId
        if (postId == null && videoId == null) return
        val targetOwnerId = postId?.let { id -> _uiState.value.posts.find { it.id == id }?.authorId }
            ?: videoId?.let { id -> _uiState.value.reels.find { it.id == id }?.authorId }

        viewModelScope.launch {
            repository.addComment(
                postId = postId,
                videoId = videoId,
                body = body,
                parentCommentId = parentComment?.id,
                targetOwnerId = targetOwnerId,
                parentCommentAuthorId = parentComment?.authorId,
                actor = user
            ).onSuccess { created ->
                _uiState.update { state ->
                    val updatedActiveComments = if (state.activeCommentsPostId == postId && state.activeCommentsVideoId == videoId) {
                        if (state.activeComments.none { it.id == created.id }) state.activeComments + created else state.activeComments
                    } else {
                        state.activeComments
                    }
                    state.copy(
                        activeComments = updatedActiveComments,
                        posts = if (postId != null) state.posts.map { if (it.id == postId) it.copy(commentsCount = it.commentsCount + 1) else it } else state.posts,
                        reels = if (videoId != null) state.reels.map { if (it.id == videoId) it.copy(commentsCount = it.commentsCount + 1) else it } else state.reels
                    )
                }
                val postObj = postId?.let { id -> _uiState.value.posts.find { it.id == id } }
                val reelObj = videoId?.let { id -> _uiState.value.reels.find { it.id == id } }
                val topics = when {
                    postObj != null -> SocializaAlgorithmEngine.extractInterestTopics(postObj.caption, postObj.hashtags, postObj.category)
                    reelObj != null -> SocializaAlgorithmEngine.extractInterestTopics(reelObj.caption, reelObj.hashtags, null)
                    else -> emptyList()
                }
                repository.recordInterestSignals(topics, delta = 2.5)
            }.onFailure { err ->
                showMessage(err.message ?: "Erro ao enviar comentário.")
            }
        }
    }

    fun toggleCommentLike(commentId: String) {
        val target = _uiState.value.activeComments.find { it.id == commentId } ?: return
        val newLiked = !target.isLiked
        _uiState.update { state ->
            state.copy(
                activeComments = state.activeComments.map {
                    if (it.id == commentId) it.copy(isLiked = newLiked, likesCount = (it.likesCount + if (newLiked) 1 else -1).coerceAtLeast(0))
                    else it
                }
            )
        }
        viewModelScope.launch {
            repository.toggleCommentLike(commentId, target.isLiked)
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            repository.deleteComment(commentId).onSuccess {
                _uiState.update { state ->
                    state.copy(activeComments = state.activeComments.filterNot { it.id == commentId })
                }
            }
        }
    }

    // =========================================================================
    // UTILIZADORES REAIS, SEGUIDORES, PERFIS, BLOQUEIO & DENÚNCIAS
    // =========================================================================

    fun updateSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
        recomputeAlgorithmDerivedState()
        if (q.trim().length >= 3) {
            viewModelScope.launch {
                val topics = SocializaAlgorithmEngine.extractInterestTopics(caption = q.trim())
                    .ifEmpty { listOf(q.trim().lowercase()) }
                repository.recordInterestSignals(topics, delta = 1.0)
            }
        }
        searchUsers(q)
    }

    fun searchUsers(query: String) {
        if (!_uiState.value.isAuthenticated) return
        viewModelScope.launch {
            repository.searchUsers(
                query = query,
                province = _uiState.value.selectedProvinceFilter.takeIf { it != "Todas" }
            ).onSuccess { users ->
                _uiState.update { it.copy(discoveredUsers = users) }
                recomputeAlgorithmDerivedState()
            }
        }
    }

    fun openUserProfile(userId: String) {
        viewModelScope.launch {
            repository.fetchUserProfile(userId).onSuccess { profile ->
                _uiState.update { it.copy(inspectedUser = profile) }
            }.onFailure { err ->
                showMessage(err.message ?: "Não foi possível carregar perfil.")
            }
        }
    }

    fun closeUserProfile() {
        _uiState.update { it.copy(inspectedUser = null) }
    }

    fun toggleFollowUser(targetUid: String) {
        val actor = _uiState.value.currentUser ?: return
        if (actor.id == targetUid) return
        val currentlyFollowing = _uiState.value.discoveredUsers.find { it.id == targetUid }?.isFollowedByMe
            ?: _uiState.value.inspectedUser?.takeIf { it.id == targetUid }?.isFollowedByMe
            ?: _uiState.value.posts.find { it.authorId == targetUid }?.isAuthorFollowed
            ?: _uiState.value.reels.find { it.authorId == targetUid }?.isFollowing
            ?: false

        viewModelScope.launch {
            repository.toggleFollowUser(targetUid, currentlyFollowing, actor.fullName).onSuccess { nowFollowing ->
                _uiState.update { state ->
                    state.copy(
                        discoveredUsers = state.discoveredUsers.map {
                            if (it.id == targetUid) it.copy(
                                isFollowedByMe = nowFollowing,
                                followersCount = (it.followersCount + if (nowFollowing) 1 else -1).coerceAtLeast(0)
                            ) else it
                        },
                        inspectedUser = state.inspectedUser?.let {
                            if (it.id == targetUid) it.copy(
                                isFollowedByMe = nowFollowing,
                                followersCount = (it.followersCount + if (nowFollowing) 1 else -1).coerceAtLeast(0)
                            ) else it
                        },
                        posts = state.posts.map { if (it.authorId == targetUid) it.copy(isAuthorFollowed = nowFollowing) else it },
                        reels = state.reels.map { if (it.authorId == targetUid) it.copy(isFollowing = nowFollowing) else it },
                        currentUser = state.currentUser?.let {
                            it.copy(followingCount = (it.followingCount + if (nowFollowing) 1 else -1).coerceAtLeast(0))
                        },
                        snackbarMessage = if (nowFollowing) "A seguir utilizador!" else "Deixou de seguir."
                    )
                }
            }.onFailure { err ->
                showMessage(err.message ?: "Erro ao atualizar seguidor.")
            }
        }
    }

    fun blockAuthor(authorId: String, authorName: String) {
        viewModelScope.launch {
            repository.blockUser(authorId).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        blockedAuthorIds = state.blockedAuthorIds + authorId,
                        inspectedUser = if (state.inspectedUser?.id == authorId) null else state.inspectedUser,
                        conversations = state.conversations.filterNot { it.otherUserId == authorId },
                        snackbarMessage = "$authorName foi bloqueado."
                    )
                }
            }
        }
    }

    fun reportTarget(targetType: String, targetId: String, reason: String) {
        viewModelScope.launch {
            repository.submitReport(targetType, targetId, reason).onSuccess {
                showMessage("Denúncia enviada para moderação.")
            }.onFailure { err ->
                showMessage(err.message ?: "Erro ao enviar denúncia.")
            }
        }
    }

    // =========================================================================
    // STORIES & VÍDEOS REAIS
    // =========================================================================

    private suspend fun loadStories() {
        repository.fetchActiveStories().onSuccess { list ->
            _uiState.update { it.copy(stories = list) }
        }
    }

    fun openStory(story: StoryItem?) {
        _uiState.update { state ->
            val updated = if (story != null) state.stories.map { if (it.id == story.id) it.copy(isViewed = true) else it } else state.stories
            state.copy(stories = updated, activeStory = story)
        }
        if (story != null) {
            viewModelScope.launch { repository.recordStoryView(story.id) }
        }
    }

    private suspend fun loadVideos() {
        repository.fetchVideos(page = 0, pageSize = PAGE_SIZE).onSuccess { list ->
            val hints = repository.fetchRecommendedVideoScoreHints(page = 0, pageSize = PAGE_SIZE)
            val ranked = SocializaAlgorithmEngine.rankRecommendedVideos(
                reels = list,
                interestWeights = repository.currentInterestWeights(),
                telemetryByVideo = _uiState.value.videoTelemetryById,
                serverScoreHints = hints
            )
            _uiState.update { it.copy(reels = ranked) }
            recomputeStats()
            recomputeAlgorithmDerivedState()
        }
    }

    fun toggleReelLike(reelId: String) {
        val user = _uiState.value.currentUser ?: return
        val reel = _uiState.value.reels.find { it.id == reelId } ?: return
        val liked = !reel.isLiked
        _uiState.update { state ->
            state.copy(
                reels = state.reels.map {
                    if (it.id == reelId) it.copy(isLiked = liked, likesCount = (it.likesCount + if (liked) 1 else -1).coerceAtLeast(0))
                    else it
                }
            )
        }
        viewModelScope.launch {
            if (liked) {
                val topics = SocializaAlgorithmEngine.extractInterestTopics(reel.caption, reel.hashtags, null)
                repository.recordInterestSignals(topics, delta = 2.0)
            }
            repository.toggleVideoLike(reel, user.fullName)
        }
    }

    fun markReelViewed(
        reelId: String,
        watchSeconds: Int = 5,
        completed: Boolean = false,
        replayed: Boolean = false
    ) {
        val prev = _uiState.value.videoTelemetryById[reelId] ?: VideoTelemetry()
        val updatedTel = VideoTelemetry(
            watchSeconds = maxOf(prev.watchSeconds, watchSeconds),
            completed = prev.completed || completed,
            replayed = prev.replayed || replayed
        )
        _uiState.update { state ->
            state.copy(videoTelemetryById = state.videoTelemetryById + (reelId to updatedTel))
        }
        viewModelScope.launch {
            repository.recordVideoView(
                videoId = reelId,
                watchSeconds = updatedTel.watchSeconds,
                completed = updatedTel.completed,
                replayed = updatedTel.replayed
            )
            if (completed || replayed || watchSeconds >= 5) {
                val reel = _uiState.value.reels.find { it.id == reelId }
                if (reel != null) {
                    val topics = SocializaAlgorithmEngine.extractInterestTopics(reel.caption, reel.hashtags, null)
                    val delta = if (replayed) 3.0 else if (completed) 2.5 else 1.2
                    repository.recordInterestSignals(topics, delta = delta)
                }
            }
        }
    }

    fun shareReel(reelId: String) {
        val target = _uiState.value.reels.find { it.id == reelId }
        _uiState.update { state ->
            state.copy(
                reels = state.reels.map { if (it.id == reelId) it.copy(sharesCount = it.sharesCount + 1) else it },
                snackbarMessage = "Partilha de vídeo registada!"
            )
        }
        viewModelScope.launch {
            if (target != null) {
                val topics = SocializaAlgorithmEngine.extractInterestTopics(target.caption, target.hashtags, null)
                repository.recordInterestSignals(topics, delta = 3.0)
            }
            repository.shareVideo(reelId)
        }
    }

    // =========================================================================
    // MENSAGENS & CONVERSAS REAIS
    // =========================================================================

    private suspend fun loadConversations() {
        repository.fetchConversations().onSuccess { convs ->
            _uiState.update { state ->
                // Preservar mensagens já carregadas da conversa ativa
                val existingById = state.conversations.associateBy { it.id }
                val merged = convs.map { c -> c.copy(messages = existingById[c.id]?.messages.orEmpty()) }
                state.copy(conversations = merged)
            }
        }
    }

    fun startDirectConversation(targetUserId: String, onOpened: () -> Unit = {}) {
        viewModelScope.launch {
            repository.startDirectConversation(targetUserId).onSuccess { convId ->
                loadConversations()
                openConversation(convId)
                onOpened()
            }.onFailure { err ->
                showMessage(err.message ?: "Não foi possível abrir conversa.")
            }
        }
    }

    fun openConversation(conversationId: String?) {
        _uiState.update { it.copy(activeConversationId = conversationId) }
        if (conversationId != null) {
            viewModelScope.launch { loadMessages(conversationId) }
        }
    }

    private suspend fun loadMessages(conversationId: String) {
        repository.fetchMessages(conversationId).onSuccess { msgs ->
            _uiState.update { state ->
                state.copy(
                    conversations = state.conversations.map { conv ->
                        if (conv.id == conversationId) conv.copy(messages = msgs, unreadCount = 0) else conv
                    }
                )
            }
        }
    }

    fun sendMessage(
        conversationId: String,
        text: String,
        type: MessageType = MessageType.TEXT,
        mediaUri: Uri? = null,
        localAudioFile: File? = null
    ) {
        val user = _uiState.value.currentUser ?: return
        val conv = _uiState.value.conversations.find { it.id == conversationId }
        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                body = text,
                type = type,
                mediaUri = mediaUri,
                localAudioFile = localAudioFile,
                recipientUserId = conv?.otherUserId,
                senderName = user.fullName
            ).onSuccess { sent ->
                _uiState.update { state ->
                    state.copy(
                        conversations = state.conversations.map { c ->
                            if (c.id == conversationId) {
                                c.copy(
                                    lastMessage = sent.body.ifBlank { sent.attachmentName ?: "Anexo" },
                                    timeLabel = "Agora",
                                    messages = c.messages + sent
                                )
                            } else c
                        }
                    )
                }
            }.onFailure { err ->
                showMessage(err.message ?: "Erro ao enviar mensagem.")
            }
        }
    }

    fun startCall(title: String, isVideo: Boolean) {
        val convId = _uiState.value.activeConversationId
        _uiState.update { it.copy(activeCallTitle = title, activeCallIsVideo = isVideo) }
        if (convId != null) {
            sendMessage(
                conversationId = convId,
                text = if (isVideo) "Chamada de vídeo iniciada" else "Chamada de voz iniciada",
                type = MessageType.CALL_EVENT
            )
        }
    }

    fun endCall() {
        _uiState.update { it.copy(activeCallTitle = null, snackbarMessage = "Chamada terminada.") }
    }

    // =========================================================================
    // NOTIFICAÇÕES, MARKETPLACE, GRUPOS, PERFIL & ADMIN
    // =========================================================================

    private suspend fun loadNotifications() {
        repository.fetchNotifications().onSuccess { list ->
            _uiState.update { it.copy(notifications = list) }
        }
    }

    fun markAllNotificationsRead() {
        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
        }
        viewModelScope.launch { repository.markAllNotificationsRead() }
    }

    private suspend fun loadMarketplaceAndGroups() {
        repository.fetchProducts().onSuccess { prods ->
            _uiState.update { it.copy(products = prods) }
        }
        repository.fetchGroups().onSuccess { grps ->
            _uiState.update { it.copy(groups = grps) }
        }
        recomputeAlgorithmDerivedState()
    }

    fun selectHashtag(tag: String?) {
        val nextTag = if (_uiState.value.selectedHashtag == tag) null else tag
        _uiState.update { it.copy(selectedHashtag = nextTag) }
        if (!nextTag.isNullOrBlank()) {
            viewModelScope.launch {
                repository.recordInterestSignals(listOf(nextTag.lowercase()), delta = 2.0)
            }
        }
    }

    fun selectExploreCategory(cat: String?) {
        val nextCat = if (_uiState.value.selectedExploreCategory == cat) null else cat
        _uiState.update { it.copy(selectedExploreCategory = nextCat) }
        if (!nextCat.isNullOrBlank()) {
            viewModelScope.launch {
                repository.recordInterestSignals(listOf(nextCat.lowercase()), delta = 2.0)
            }
        }
        refreshFeedOnly()
    }

    fun selectMarketCategory(cat: String) {
        _uiState.update { it.copy(selectedMarketCategory = cat) }
    }

    fun toggleProductFavorite(productId: String) {
        _uiState.update { state ->
            state.copy(products = state.products.map { if (it.id == productId) it.copy(isFavorite = !it.isFavorite) else it })
        }
    }

    fun addMarketplaceProduct(title: String, priceKz: Long, category: String, province: String, description: String, imageUri: Uri? = null) {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            repository.createProduct(title, priceKz, category, province, description, imageUri, user.fullName)
                .onSuccess { prod ->
                    _uiState.update {
                        it.copy(products = listOf(prod) + it.products, snackbarMessage = "Anúncio publicado!")
                    }
                }.onFailure { err ->
                    showMessage(err.message ?: "Erro ao publicar anúncio.")
                }
        }
    }

    fun toggleGroupMembership(groupId: String) {
        val grp = _uiState.value.groups.find { it.id == groupId } ?: return
        viewModelScope.launch {
            repository.toggleGroupMembership(groupId, grp.isJoined).onSuccess { joined ->
                _uiState.update { state ->
                    state.copy(
                        groups = state.groups.map {
                            if (it.id == groupId) it.copy(isJoined = joined, membersCount = (it.membersCount + if (joined) 1 else -1).coerceAtLeast(0))
                            else it
                        }
                    )
                }
            }
        }
    }

    fun updateProfile(fullName: String, username: String, bio: String, province: String, website: String, isPrivate: Boolean, newAvatarUri: Uri? = null) {
        viewModelScope.launch {
            repository.updateOwnProfile(fullName, username, bio, province, website, isPrivate, newAvatarUri)
                .onSuccess { updated ->
                    _uiState.update { it.copy(currentUser = updated, snackbarMessage = "Perfil atualizado!") }
                    recomputeStats()
                }.onFailure { err ->
                    showMessage(err.message ?: "Erro ao atualizar perfil.")
                }
        }
    }

    fun incrementAdWatchedProgress() {
        val uid = _uiState.value.currentUser?.id ?: return
        val next = (_uiState.value.adsWatchedCount + 1).coerceAtMost(5)
        viewModelScope.launch {
            preferences.saveAdsWatchedCount(uid, next)
            _uiState.update { it.copy(adsWatchedCount = next) }
        }
    }

    fun claimDailyVerificationBadge() {
        val user = _uiState.value.currentUser ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isClaimingVerificationBadge = true) }
            repository.claimDailyVerifiedBadge(adsWatched = 5, currentUser = user)
                .onSuccess { updatedUser ->
                    _uiState.update { state ->
                        state.copy(
                            isClaimingVerificationBadge = false,
                            adsWatchedCount = 0,
                            currentUser = updatedUser,
                            posts = state.posts.map { p ->
                                if (p.authorId == updatedUser.id) p.copy(authorVerified = true) else p
                            },
                            reels = state.reels.map { r ->
                                if (r.authorId == updatedUser.id) r.copy(isVerified = true) else r
                            },
                            snackbarMessage = "Selo de verificação ativado por 24 horas!"
                        )
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isClaimingVerificationBadge = false,
                            snackbarMessage = err.message ?: "Não foi possível ativar o selo."
                        )
                    }
                }
        }
    }

    fun resolveModerationReport(reportId: String, action: String) {
        val target = _uiState.value.reports.find { it.id == reportId } ?: return
        viewModelScope.launch {
            repository.executeAdminModerationRemote(reportId, action, target.targetId, target.reason)
                .onSuccess {
                    val reports = repository.fetchReports()
                    val logs = repository.fetchAuditLogs()
                    _uiState.update {
                        it.copy(reports = reports, auditLogs = logs, snackbarMessage = "Ação administrativa executada.")
                    }
                }.onFailure { err ->
                    showMessage(err.message ?: "Erro na moderação.")
                }
        }
    }
}
