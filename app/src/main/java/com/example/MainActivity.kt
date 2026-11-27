package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.MessageType
import com.example.ui.BottomTab
import com.example.ui.MeuPaisViewModel
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.InspectedUserProfileSheet
import com.example.ui.components.MeuPaisBottomBar
import com.example.ui.components.StoryViewerDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.MessagesScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.theme.MeuPaisTheme
import com.example.ui.theme.OrangeVibrant
import kotlinx.serialization.Serializable

@Serializable object RouteHome
@Serializable object RouteExplore
@Serializable object RouteCreate
@Serializable object RouteVideos
@Serializable object RouteProfile
@Serializable object RouteMessages

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MeuPaisViewModel = viewModel()
            MeuPaisRoot(viewModel = viewModel)
        }
    }
}

@Composable
fun MeuPaisRoot(viewModel: MeuPaisViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val runOnMainThread: (() -> Unit) -> Unit = remember {
        { action ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                action()
            } else {
                Handler(Looper.getMainLooper()).post(action)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.showMessage(
            if (granted) "Notificações ativadas!"
            else "Permissão de notificações recusada."
        )
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    MeuPaisTheme(themeMode = state.themeMode) {
        if (!state.isReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OrangeVibrant)
            }
            return@MeuPaisTheme
        }

        val currentUser = state.currentUser
        // Proteção estrita das rotas privadas: exige sessão autenticada no Firebase
        if (!state.isAuthenticated || currentUser == null) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    AuthScreen(
                        isLoading = state.isLoadingAuth,
                        authMode = state.authMode,
                        otpStep = state.otpStep,
                        pendingAuthEmail = state.pendingAuthEmail,
                        otpRemainingSeconds = state.otpRemainingSeconds,
                        otpResendCooldownSeconds = state.otpResendCooldownSeconds,
                        authErrorMessage = state.authErrorMessage,
                        onSelectAuthMode = { viewModel.setAuthMode(it) },
                        onGoogleSignIn = { viewModel.signInWithGoogle() },
                        onEmailSignIn = { email, password -> viewModel.signInWithEmail(email, password) },
                        onStartRegister = { name, email, password -> viewModel.startEmailRegistration(name, email, password) },
                        onConfirmRegisterOtp = { otp -> viewModel.confirmRegistrationOtp(otp) },
                        onResendRegisterOtp = { viewModel.resendRegistrationOtp() },
                        onStartPasswordReset = { email -> viewModel.startPasswordReset(email) },
                        onConfirmPasswordReset = { otp, newPass -> viewModel.confirmPasswordReset(otp, newPass) },
                        onResendResetOtp = { viewModel.resendPasswordResetOtp() },
                        onCancelOtp = { viewModel.cancelOtpStep() }
                    )
                }
            }
            return@MeuPaisTheme
        }

        // Isolamento de estado de navegação e cache por UID autenticado
        key(currentUser.id) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val routeName = backStackEntry?.destination?.route.orEmpty()

            val currentTab = when {
                routeName.contains("RouteExplore") -> BottomTab.EXPLORE
                routeName.contains("RouteVideos") -> BottomTab.VIDEOS
                routeName.contains("RouteProfile") -> BottomTab.PROFILE
                else -> BottomTab.HOME
            }
            val showBottomBar = !routeName.contains("RouteCreate") && !routeName.contains("RouteMessages")

            Scaffold(
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = if (showBottomBar) 0.dp else 76.dp)
                    )
                },
                bottomBar = {
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter = slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(180)),
                        exit = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(160))
                    ) {
                        MeuPaisBottomBar(
                            currentTab = currentTab,
                            onSelectTab = { tab ->
                                val target: Any = when (tab) {
                                    BottomTab.HOME -> RouteHome
                                    BottomTab.EXPLORE -> RouteExplore
                                    BottomTab.VIDEOS -> RouteVideos
                                    BottomTab.PROFILE -> RouteProfile
                                    BottomTab.CREATE -> RouteCreate
                                }
                                navController.navigate(target) {
                                    popUpTo(RouteHome) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onOpenCreate = {
                                navController.navigate(RouteCreate) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = RouteHome,
                    enterTransition = { fadeIn(animationSpec = tween(200)) },
                    exitTransition = { fadeOut(animationSpec = tween(160)) },
                    popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                    popExitTransition = { fadeOut(animationSpec = tween(160)) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                ) {
                    composable<RouteHome> {
                        val homeActive = routeName.contains("RouteHome") &&
                            state.activeStory == null &&
                            state.activeCommentsPostId == null &&
                            state.activeCommentsVideoId == null
                        AdaptiveContentFrame {
                            HomeFeedScreen(
                                currentUser = currentUser,
                                feedMode = state.feedMode,
                                selectedProvinceFilter = state.selectedProvinceFilter,
                                provinces = viewModel.provinces,
                                stories = state.visibleStories,
                                posts = state.visiblePosts,
                                isLoadingFeed = state.isLoadingFeed,
                                isLoadingMorePosts = state.isLoadingMorePosts,
                                hasMorePosts = state.hasMorePosts,
                                notifications = state.notifications,
                                unreadNotificationsCount = state.unreadNotificationsCount,
                                unreadMessagesCount = state.unreadMessagesCount,
                                backendStatus = state.backendStatus,
                                isScreenActive = homeActive,
                                onSelectFeedMode = { viewModel.setFeedMode(it) },
                                onSelectProvinceFilter = { viewModel.setProvinceFilter(it) },
                                onRefreshFeed = { viewModel.refreshFeedOnly() },
                                onLoadMorePosts = { viewModel.loadMorePosts() },
                                onOpenSearch = {
                                    navController.navigate(RouteExplore) { launchSingleTop = true }
                                },
                                onOpenMessages = {
                                    navController.navigate(RouteMessages) { launchSingleTop = true }
                                },
                                onOpenCreatePost = {
                                    navController.navigate(RouteCreate) { launchSingleTop = true }
                                },
                                onOpenStory = { story -> viewModel.openStory(story) },
                                onOpenAuthorProfile = { uid ->
                                    if (uid == currentUser.id) {
                                        navController.navigate(RouteProfile) { launchSingleTop = true }
                                    } else {
                                        viewModel.openUserProfile(uid)
                                    }
                                },
                                onToggleFollowAuthor = { uid -> viewModel.toggleFollowUser(uid) },
                                onToggleLike = { viewModel.togglePostLike(it) },
                                onToggleSave = { viewModel.togglePostSave(it) },
                                onOpenComments = { postId -> viewModel.openComments(postId = postId) },
                                onSharePost = { viewModel.sharePost(it) },
                                onPostVisible = { viewModel.markPostViewed(it) },
                                onEditPost = { id, cap, tags, loc, vis ->
                                    viewModel.editPost(id, cap, tags, loc, vis)
                                },
                                onDeletePost = { viewModel.deletePost(it) },
                                onReportPost = { post, reason -> viewModel.reportTarget("post", post.id, reason) },
                                onBlockAuthor = { id, name -> viewModel.blockAuthor(id, name) },
                                onMarkNotificationsRead = { viewModel.markAllNotificationsRead() },
                                onSelectHashtag = { tag ->
                                    viewModel.selectHashtag(tag)
                                    navController.navigate(RouteExplore) { launchSingleTop = true }
                                },
                                onRetryBackendSync = { viewModel.retryBackendSync() }
                            )
                        }
                    }

                    composable<RouteExplore> {
                        AdaptiveContentFrame {
                            ExploreScreen(
                                searchQuery = state.searchQuery,
                                selectedHashtag = state.selectedHashtag,
                                selectedCategory = state.selectedExploreCategory,
                                selectedMarketCategory = state.selectedMarketCategory,
                                discoveredUsers = state.discoveredUsers,
                                posts = state.visiblePosts,
                                products = state.products,
                                provinces = viewModel.provinces,
                                trendingTopics = state.trendingTopics,
                                suggestedPeople = state.suggestedPeople,
                                groups = state.groups,
                                unifiedSearchResults = state.unifiedSearchResults,
                                onSearchChange = { viewModel.updateSearchQuery(it) },
                                onSelectHashtag = { viewModel.selectHashtag(it) },
                                onSelectCategory = { viewModel.selectExploreCategory(it) },
                                onSelectMarketCategory = { viewModel.selectMarketCategory(it) },
                                onOpenUserProfile = { uid -> viewModel.openUserProfile(uid) },
                                onToggleFollowUser = { uid -> viewModel.toggleFollowUser(uid) },
                                onMessageUser = { uid ->
                                    viewModel.startDirectConversation(uid) {
                                        runOnMainThread {
                                            navController.navigate(RouteMessages) { launchSingleTop = true }
                                        }
                                    }
                                },
                                onToggleProductFavorite = { viewModel.toggleProductFavorite(it) },
                                onAddProduct = { title, price, cat, prov, desc, imgUri ->
                                    viewModel.addMarketplaceProduct(title, price, cat, prov, desc, imgUri)
                                },
                                onOpenPostComments = { postId -> viewModel.openComments(postId = postId) },
                                onContactSeller = { product ->
                                    if (product.sellerId != currentUser.id) {
                                        viewModel.startDirectConversation(product.sellerId) {
                                            runOnMainThread {
                                                navController.navigate(RouteMessages) { launchSingleTop = true }
                                            }
                                        }
                                    } else {
                                        viewModel.showMessage("Este anúncio pertence à tua conta.")
                                    }
                                },
                                onToggleGroup = { groupId -> viewModel.toggleGroupMembership(groupId) },
                                onOpenVideos = {
                                    navController.navigate(RouteVideos) { launchSingleTop = true }
                                }
                            )
                        }
                    }

                    composable<RouteCreate>(
                        enterTransition = { slideInVertically(tween(240)) { it / 3 } + fadeIn(tween(200)) },
                        exitTransition = { slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(180)) }
                    ) {
                        AdaptiveContentFrame {
                            CreatePostScreen(
                                provinces = viewModel.provinces,
                                isPublishing = state.isPublishing,
                                onClose = { navController.popBackStack() },
                                onPublish = { caption, type, category, loc, tags, vis, uris, alsoReels, audioUri, audioTitle ->
                                    viewModel.createPost(
                                        caption = caption,
                                        postType = type,
                                        category = category,
                                        location = loc,
                                        hashtagsRaw = tags,
                                        visibility = vis,
                                        mediaUris = uris,
                                        publishAlsoToReels = alsoReels,
                                        audioUri = audioUri,
                                        audioTitle = audioTitle,
                                        onComplete = { runOnMainThread { navController.popBackStack() } }
                                    )
                                }
                            )
                        }
                    }

                    composable<RouteVideos> {
                        val videosActive = routeName.contains("RouteVideos") &&
                            state.activeCommentsPostId == null &&
                            state.activeCommentsVideoId == null
                        VideosScreen(
                            reels = state.reels,
                            isScreenActive = videosActive,
                            onToggleLike = { viewModel.toggleReelLike(it) },
                            onToggleFollow = { authorId -> viewModel.toggleFollowUser(authorId) },
                            onOpenComments = { videoId -> viewModel.openComments(videoId = videoId) },
                            onShareReel = { viewModel.shareReel(it) },
                            onReelVisible = { viewModel.markReelViewed(it) },
                            onReelTelemetry = { reelId, sec, completed, replayed ->
                                viewModel.markReelViewed(reelId, sec, completed, replayed)
                            },
                            onOpenAuthorProfile = { uid -> viewModel.openUserProfile(uid) },
                            onOpenCreateVideo = {
                                navController.navigate(RouteCreate) { launchSingleTop = true }
                            },
                            onOpenSearch = {
                                navController.navigate(RouteExplore) { launchSingleTop = true }
                            }
                        )
                    }

                    composable<RouteMessages>(
                        enterTransition = { slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(200)) },
                        exitTransition = { slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(160)) }
                    ) {
                        AdaptiveContentFrame {
                            MessagesScreen(
                                conversations = state.conversations,
                                discoveredUsers = state.discoveredUsers,
                                activeConversationId = state.activeConversationId,
                                activeCallTitle = state.activeCallTitle,
                                activeCallIsVideo = state.activeCallIsVideo,
                                liveKitConfigured = state.backendStatus?.liveKitConfigured == true,
                                onBackToFeed = { navController.popBackStack() },
                                onOpenConversation = { viewModel.openConversation(it) },
                                onStartDirectChat = { targetUid ->
                                    viewModel.startDirectConversation(targetUid)
                                },
                                onSendMessage = { convId, text, type, mediaUri, audioFile ->
                                    viewModel.sendMessage(convId, text, type, mediaUri, audioFile)
                                },
                                onStartCall = { title, isVideo -> viewModel.startCall(title, isVideo) },
                                onEndCall = { viewModel.endCall() },
                                onShowMessage = { viewModel.showMessage(it) }
                            )
                        }
                    }

                    composable<RouteProfile> {
                        AdaptiveContentFrame {
                            ProfileScreen(
                                user = currentUser,
                                userPosts = state.visiblePosts.filter { it.authorId == currentUser.id },
                                savedPosts = state.visiblePosts.filter { it.isSaved },
                                reels = state.reels.filter { it.authorId == currentUser.id },
                                groups = state.groups,
                                reports = state.reports,
                                auditLogs = state.auditLogs,
                                creatorStats = state.creatorStats,
                                themeMode = state.themeMode,
                                provinces = viewModel.provinces,
                                adsWatchedCount = state.adsWatchedCount,
                                isClaimingVerification = state.isClaimingVerificationBadge,
                                onAdWatched = { viewModel.incrementAdWatchedProgress() },
                                onClaimVerificationBadge = { viewModel.claimDailyVerificationBadge() },
                                onSetThemeMode = { viewModel.setThemeMode(it) },
                                onEditProfile = { full, username, bio, prov, web, priv, avatarUri ->
                                    viewModel.updateProfile(full, username, bio, prov, web, priv, avatarUri)
                                },
                                onOpenPostComments = { postId -> viewModel.openComments(postId = postId) },
                                onToggleGroup = { viewModel.toggleGroupMembership(it) },
                                onResolveReport = { id, action -> viewModel.resolveModerationReport(id, action) },
                                onOpenExploreMarket = {
                                    navController.navigate(RouteExplore) { launchSingleTop = true }
                                },
                                onRequestPushPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.showMessage("As notificações já estão ativas neste dispositivo.")
                                    }
                                },
                                onSignOut = { viewModel.signOut() }
                            )
                        }
                    }
                }
            }

            state.activeStory?.let { story ->
                StoryViewerDialog(
                    story = story,
                    onDismiss = { viewModel.openStory(null) },
                    onReplyStory = { reply ->
                        if (story.authorId != currentUser.id) {
                            viewModel.startDirectConversation(story.authorId) {
                                val convId = viewModel.uiState.value.activeConversationId
                                if (convId != null) {
                                    viewModel.sendMessage(
                                        conversationId = convId,
                                        text = "Resposta ao teu story: $reply",
                                        type = MessageType.TEXT
                                    )
                                }
                            }
                            viewModel.showMessage("Resposta enviada a ${story.authorName}")
                        }
                    }
                )
            }

            state.inspectedUser?.let { targetUser ->
                InspectedUserProfileSheet(
                    user = targetUser,
                    onDismiss = { viewModel.closeUserProfile() },
                    onToggleFollow = { viewModel.toggleFollowUser(targetUser.id) },
                    onMessage = {
                        viewModel.closeUserProfile()
                        viewModel.startDirectConversation(targetUser.id) {
                            runOnMainThread {
                                navController.navigate(RouteMessages) { launchSingleTop = true }
                            }
                        }
                    },
                    onBlock = { viewModel.blockAuthor(targetUser.id, targetUser.fullName) },
                    onReport = { reason ->
                        viewModel.reportTarget("user", targetUser.id, reason)
                    }
                )
            }

            if (state.activeCommentsPostId != null || state.activeCommentsVideoId != null) {
                CommentsBottomSheet(
                    currentUid = currentUser.id,
                    comments = state.activeComments,
                    isLoading = state.isLoadingComments,
                    onDismiss = { viewModel.openComments(null, null) },
                    onSendComment = { text, replyTo -> viewModel.addComment(text, replyTo) },
                    onToggleLikeComment = { viewModel.toggleCommentLike(it) },
                    onDeleteComment = { viewModel.deleteComment(it) }
                )
            }
        }
    }
}

@Composable
private fun AdaptiveContentFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
        ) {
            content()
        }
    }
}
