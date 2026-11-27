package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.AppNotification
import com.example.data.model.BackendStatus
import com.example.data.model.FeedMode
import com.example.data.model.FeedPost
import com.example.data.model.PostType
import com.example.data.model.PostVisibility
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MeuPaisLogoEmblem
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedCheckBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.components.rememberClickHaptic
import com.example.ui.theme.CrimsonAngola
import com.example.ui.theme.LocalMeuPaisColors
import com.example.ui.theme.OrangeVibrant
import com.example.ui.theme.VerifiedBlue
import kotlin.math.abs

private fun FeedPost.isVideoPost(): Boolean {
    if (postType == PostType.VIDEO) return true
    val first = mediaUrls.firstOrNull()?.lowercase() ?: return false
    return first.endsWith(".mp4") || first.endsWith(".mov") || first.endsWith(".webm") || first.contains("/videos/")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    currentUser: UserProfile,
    feedMode: FeedMode,
    selectedProvinceFilter: String,
    provinces: List<String>,
    stories: List<StoryItem>,
    posts: List<FeedPost>,
    isLoadingFeed: Boolean,
    isLoadingMorePosts: Boolean,
    hasMorePosts: Boolean,
    notifications: List<AppNotification>,
    unreadNotificationsCount: Int,
    unreadMessagesCount: Int,
    backendStatus: BackendStatus?,
    isScreenActive: Boolean = true,
    onSelectFeedMode: (FeedMode) -> Unit,
    onSelectProvinceFilter: (String) -> Unit,
    onRefreshFeed: () -> Unit,
    onLoadMorePosts: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenStory: (StoryItem) -> Unit,
    onOpenAuthorProfile: (String) -> Unit,
    onToggleFollowAuthor: (String) -> Unit = {},
    onToggleLike: (String) -> Unit,
    onToggleSave: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onSharePost: (String) -> Unit,
    onPostVisible: (String) -> Unit,
    onEditPost: (postId: String, caption: String, hashtags: String, location: String, visibility: PostVisibility) -> Unit,
    onDeletePost: (String) -> Unit,
    onReportPost: (FeedPost, String) -> Unit,
    onBlockAuthor: (String, String) -> Unit,
    onMarkNotificationsRead: () -> Unit,
    onSelectHashtag: (String) -> Unit,
    onRetryBackendSync: () -> Unit = {}
) {
    val colors = LocalMeuPaisColors.current
    val clickHaptic = rememberClickHaptic()
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isResumed by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeAutoPlayPostId by remember(posts, isScreenActive, isResumed) {
        derivedStateOf {
            if (!isScreenActive || !isResumed) return@derivedStateOf null
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf null
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            val videoPostIds = posts.filter { it.isVideoPost() && it.mediaUrls.isNotEmpty() }.mapTo(HashSet()) { it.id }
            visibleItems
                .filter { item -> (item.key as? String) in videoPostIds }
                .minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    abs(itemCenter - viewportCenter)
                }
                ?.key as? String
        }
    }

    var showNotificationsSheet by remember { mutableStateOf(false) }
    var showProvinceDropdown by remember { mutableStateOf(false) }
    var postBeingEdited by remember { mutableStateOf<FeedPost?>(null) }
    var postPendingDelete by remember { mutableStateOf<FeedPost?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MeuPaisLogoEmblem(size = 30.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Socializa",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clickHaptic()
                            onOpenSearch()
                        },
                        modifier = Modifier.testTag("top_search_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Pesquisar")
                    }
                    IconButton(
                        onClick = {
                            clickHaptic()
                            showNotificationsSheet = true
                        },
                        modifier = Modifier.testTag("top_notifications_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotificationsCount > 0) {
                                    Badge(containerColor = CrimsonAngola, contentColor = Color.White) {
                                        Text(unreadNotificationsCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Notificações")
                        }
                    }
                    IconButton(
                        onClick = {
                            clickHaptic()
                            onOpenMessages()
                        },
                        modifier = Modifier.testTag("top_messages_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadMessagesCount > 0) {
                                    Badge(containerColor = OrangeVibrant, contentColor = Color.White) {
                                        Text(unreadMessagesCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Mensagens")
                        }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(FeedMode.entries) { mode ->
                        FilterChip(
                            selected = feedMode == mode,
                            onClick = {
                                clickHaptic()
                                onSelectFeedMode(mode)
                            },
                            shape = RoundedCornerShape(8.dp),
                            label = { Text(mode.label, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangeVibrant,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = feedMode == mode,
                                borderColor = colors.cardBorder
                            ),
                            modifier = Modifier.testTag("feed_mode_${mode.name}")
                        )
                    }
                    item {
                        Box {
                            FilterChip(
                                selected = selectedProvinceFilter != "Todas",
                                onClick = {
                                    clickHaptic()
                                    showProvinceDropdown = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                leadingIcon = {
                                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = {
                                    Text(
                                        text = if (selectedProvinceFilter == "Todas") "Província" else selectedProvinceFilter,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangeVibrant,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedProvinceFilter != "Todas",
                                    borderColor = colors.cardBorder
                                )
                            )
                            DropdownMenu(expanded = showProvinceDropdown, onDismissRequest = { showProvinceDropdown = false }) {
                                DropdownMenuItem(
                                    text = { Text("Todas as províncias") },
                                    onClick = {
                                        clickHaptic()
                                        onSelectProvinceFilter("Todas")
                                        showProvinceDropdown = false
                                    }
                                )
                                provinces.forEach { prov ->
                                    DropdownMenuItem(
                                        text = { Text(prov) },
                                        onClick = {
                                            clickHaptic()
                                            onSelectProvinceFilter(prov)
                                            showProvinceDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoadingFeed && posts.isNotEmpty(),
            onRefresh = {
                clickHaptic()
                onRefreshFeed()
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (backendStatus != null && !backendStatus.supabaseConfigured) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Ligação ao servidor pendente. Verifica a tua ligação à internet e tenta novamente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                } else if (!backendStatus?.supabaseAuthDiagnostic.isNullOrBlank()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("supabase_auth_diagnostic_card"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = "Estado da sessão",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sessão a sincronizar",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Não foi possível sincronizar a tua conta neste momento.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        clickHaptic()
                                        onRetryBackendSync()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = OrangeVibrant,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("retry_backend_sync_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sincronizar", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Barra rápida editorial de publicação
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clickHaptic()
                                onOpenCreatePost()
                            }
                            .testTag("quick_compose_card")
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = currentUser.fullName, avatarUrl = currentUser.avatarUri, size = 40.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    color = colors.elevatedSurface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.7.dp, colors.cardBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "O que estás a pensar?",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoLibrary,
                                        contentDescription = "Adicionar foto ou vídeo",
                                        tint = OrangeVibrant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Foto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                        }
                    }
                }

                // Faixa de Stories limpa
                item {
                    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    CreateStoryBubble(
                                        currentUser = currentUser,
                                        onClick = {
                                            clickHaptic()
                                            onOpenCreatePost()
                                        }
                                    )
                                }
                                items(stories, key = { it.id }) { story ->
                                    StoryBubbleItem(
                                        story = story,
                                        onClick = {
                                            clickHaptic()
                                            onOpenStory(story)
                                        }
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (isLoadingFeed && posts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = OrangeVibrant, strokeWidth = 2.5.dp)
                        }
                    }
                } else if (posts.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Filled.DynamicFeed,
                            title = "Ainda não existem publicações",
                            description = if (feedMode == FeedMode.FOLLOWING) {
                                "Segue pessoas em Explorar para ver as suas publicações aqui."
                            } else {
                                "Sê o primeiro a publicar uma fotografia, texto ou vídeo nesta comunidade!"
                            },
                            actionLabel = if (feedMode == FeedMode.FOLLOWING) "Descobrir Pessoas" else "Criar Publicação",
                            onAction = {
                                if (feedMode == FeedMode.FOLLOWING) onOpenSearch() else onOpenCreatePost()
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    items(posts, key = { it.id }) { post ->
                        LaunchedEffect(post.id) {
                            onPostVisible(post.id)
                        }
                        FeedPostCard(
                            post = post,
                            isOwnPost = post.authorId == currentUser.id,
                            shouldAutoPlayVideo = activeAutoPlayPostId == post.id,
                            onAuthorClick = {
                                clickHaptic()
                                onOpenAuthorProfile(post.authorId)
                            },
                            onToggleFollowAuthor = {
                                clickHaptic()
                                onToggleFollowAuthor(post.authorId)
                            },
                            onLike = {
                                clickHaptic()
                                onToggleLike(post.id)
                            },
                            onSave = {
                                clickHaptic()
                                onToggleSave(post.id)
                            },
                            onComment = {
                                clickHaptic()
                                onOpenComments(post.id)
                            },
                            onShare = {
                                clickHaptic()
                                onSharePost(post.id)
                            },
                            onEdit = {
                                clickHaptic()
                                postBeingEdited = post
                            },
                            onDelete = {
                                clickHaptic()
                                postPendingDelete = post
                            },
                            onReport = { reason ->
                                clickHaptic()
                                onReportPost(post, reason)
                            },
                            onBlock = {
                                clickHaptic()
                                onBlockAuthor(post.authorId, post.authorName)
                            },
                            onHashtagClick = { tag ->
                                clickHaptic()
                                onSelectHashtag(tag)
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (hasMorePosts) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMorePosts) {
                                    CircularProgressIndicator(color = OrangeVibrant, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            clickHaptic()
                                            onLoadMorePosts()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(0.7.dp, colors.cardBorder),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Carregar mais publicações")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotificationsSheet) {
        ModalBottomSheet(onDismissRequest = { showNotificationsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notificações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (notifications.isNotEmpty()) {
                        TextButton(onClick = {
                            clickHaptic()
                            onMarkNotificationsRead()
                        }) {
                            Text("Marcar lidas", color = OrangeVibrant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                if (notifications.isEmpty()) {
                    Text(
                        text = "Sem notificações por agora. Receberás alertas quando alguém te seguir, curtir, comentar ou enviar mensagem.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    notifications.forEach { notif ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (notif.isRead) Color.Transparent
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                )
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(OrangeVibrant.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, tint = OrangeVibrant, modifier = Modifier.size(19.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(notif.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Text(notif.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(notif.timeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (postBeingEdited != null) {
        val editing = postBeingEdited!!
        var caption by remember(editing.id) { mutableStateOf(editing.caption) }
        var tags by remember(editing.id) { mutableStateOf(editing.hashtags.joinToString(" ")) }
        var loc by remember(editing.id) { mutableStateOf(editing.locationLabel) }
        ModalBottomSheet(onDismissRequest = { postBeingEdited = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Editar Publicação", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Legenda") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Hashtags") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = loc,
                    onValueChange = { loc = it },
                    label = { Text("Localização") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        clickHaptic()
                        onEditPost(editing.id, caption, tags, loc, editing.visibility)
                        postBeingEdited = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
                ) {
                    Text("Guardar alterações", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (postPendingDelete != null) {
        AlertDialog(
            onDismissRequest = { postPendingDelete = null },
            shape = RoundedCornerShape(14.dp),
            title = { Text("Eliminar publicação") },
            text = { Text("Esta publicação e os seus comentários serão removidos permanentemente.") },
            confirmButton = {
                Button(
                    onClick = {
                        clickHaptic()
                        onDeletePost(postPendingDelete!!.id)
                        postPendingDelete = null
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAngola)
                ) { Text("Eliminar", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic()
                    postPendingDelete = null
                }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun CreateStoryBubble(currentUser: UserProfile, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable { onClick() }
            .testTag("create_story_bubble")
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            UserAvatar(name = currentUser.fullName, avatarUrl = currentUser.avatarUri, size = 62.dp)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(OrangeVibrant)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Criar story", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("Criar story", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StoryBubbleItem(story: StoryItem, onClick: () -> Unit) {
    val colors = LocalMeuPaisColors.current
    val ringBrush = if (story.isViewed) {
        Brush.linearGradient(listOf(colors.cardBorder, colors.cardBorder))
    } else {
        Brush.linearGradient(listOf(colors.storyRingStart, colors.storyRingEnd))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable { onClick() }
            .testTag("story_item_${story.id}")
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ringBrush)
                .padding(2.5.dp)
        ) {
            UserAvatar(
                name = story.authorName,
                avatarUrl = story.avatarUrl,
                size = 59.dp,
                modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(story.authorName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FeedPostCard(
    post: FeedPost,
    isOwnPost: Boolean,
    shouldAutoPlayVideo: Boolean = false,
    onAuthorClick: () -> Unit,
    onToggleFollowAuthor: () -> Unit = {},
    onLike: () -> Unit,
    onSave: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: (String) -> Unit,
    onBlock: () -> Unit,
    onHashtagClick: (String) -> Unit
) {
    val colors = LocalMeuPaisColors.current
    val clickHaptic = rememberClickHaptic()
    var menuExpanded by remember { mutableStateOf(false) }

    val likeIconScale by animateFloatAsState(
        targetValue = if (post.isLiked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "feed_like_scale"
    )

    val visibilityIcon = when (post.visibility) {
        PostVisibility.FOLLOWERS -> Icons.Filled.Group
        PostVisibility.PRIVATE -> Icons.Filled.Lock
        PostVisibility.PUBLIC -> Icons.Filled.Public
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feed_post_${post.id}")
    ) {
        Column {
            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAuthorClick() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(name = post.authorName, avatarUrl = post.authorAvatarUrl, size = 42.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = post.authorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (post.authorVerified) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerifiedCheckBadge(size = 15.dp)
                                }
                                if (!isOwnPost && !post.isAuthorFollowed) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• Seguir",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OrangeVibrant,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onToggleFollowAuthor() }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val editedSuffix = if (post.editedAtLabel != null) " • ${post.editedAtLabel}" else ""
                                val loc = if (post.locationLabel.isNotBlank()) " • ${post.locationLabel}" else ""
                                Text(
                                    text = "${post.createdAtLabel}$loc$editedSuffix",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Icon(
                                    imageVector = visibilityIcon,
                                    contentDescription = post.visibility.label,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = {
                            clickHaptic()
                            menuExpanded = true
                        }) {
                            Icon(Icons.Filled.MoreHoriz, contentDescription = "Mais opções")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (isOwnPost) {
                                DropdownMenuItem(
                                    text = { Text("Editar publicação") },
                                    onClick = {
                                        menuExpanded = false
                                        onEdit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar publicação", color = CrimsonAngola) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Ver perfil de @${post.authorUsername}") },
                                    onClick = {
                                        menuExpanded = false
                                        onAuthorClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Denunciar publicação") },
                                    onClick = {
                                        menuExpanded = false
                                        onReport("Conteúdo impróprio ou spam")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Bloquear ${post.authorName}", color = CrimsonAngola) },
                                    onClick = {
                                        menuExpanded = false
                                        onBlock()
                                    }
                                )
                            }
                        }
                    }
                }

                if (post.caption.isNotBlank() || post.hashtags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val annotated = buildAnnotatedString {
                        append(post.caption)
                        if (post.hashtags.isNotEmpty()) {
                            if (post.caption.isNotBlank()) append("\n")
                            post.hashtags.forEachIndexed { idx, tag ->
                                withStyle(SpanStyle(color = colors.hashtagText, fontWeight = FontWeight.SemiBold)) {
                                    append(tag)
                                }
                                if (idx < post.hashtags.lastIndex) append(" ")
                            }
                        }
                    }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable { post.hashtags.firstOrNull()?.let { onHashtagClick(it) } }
                    )
                }

                if (post.mediaUrls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (post.isVideoPost()) {
                        FeedVideoPlayer(
                            videoUrl = post.mediaUrls.first(),
                            musicUrl = post.musicUrl,
                            musicTitle = post.musicTitle,
                            autoPlay = shouldAutoPlayVideo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(310.dp)
                        )
                    } else if (post.mediaUrls.size == 1) {
                        AsyncImage(
                            model = post.mediaUrls.first(),
                            contentDescription = post.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp)
                        )
                    } else {
                        val pagerState = rememberPagerState(pageCount = { post.mediaUrls.size })
                        Box {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(290.dp)
                            ) { page ->
                                AsyncImage(
                                    model = post.mediaUrls[page],
                                    contentDescription = post.caption,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.68f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1}/${post.mediaUrls.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(19.dp)
                                .clip(CircleShape)
                                .background(if (post.isLiked) OrangeVibrant else VerifiedBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ThumbUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${formatCompactCount(post.likesCount)} • ${formatCompactCount(post.viewsCount)} vis.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${post.commentsCount} coment. • ${formatCompactCount(post.sharesCount)} part.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = colors.divider,
                    thickness = 0.7.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PostActionButton(
                        icon = if (post.isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        label = "Curtir",
                        tint = if (post.isLiked) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                        isHighlighted = post.isLiked,
                        iconScale = likeIconScale,
                        onClick = onLike,
                        tag = "like_post_${post.id}",
                        modifier = Modifier.weight(1f)
                    )
                    PostActionButton(
                        icon = Icons.AutoMirrored.Outlined.Comment,
                        label = "Comentar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        isHighlighted = false,
                        onClick = onComment,
                        tag = "comment_post_${post.id}",
                        modifier = Modifier.weight(1f)
                    )
                    PostActionButton(
                        icon = Icons.Outlined.Share,
                        label = "Partilhar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        isHighlighted = false,
                        onClick = onShare,
                        tag = "share_post_${post.id}",
                        modifier = Modifier.weight(1f)
                    )
                    PostActionButton(
                        icon = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        label = if (post.isSaved) "Guardado" else "Guardar",
                        tint = if (post.isSaved) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                        isHighlighted = post.isSaved,
                        onClick = onSave,
                        tag = "save_post_${post.id}",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
        }
    }
}

@Composable
private fun FeedVideoPlayer(
    videoUrl: String,
    musicUrl: String?,
    musicTitle: String?,
    autoPlay: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clickHaptic = rememberClickHaptic()
    var userPaused by remember(videoUrl) { mutableStateOf(false) }
    var isMuted by remember(videoUrl) { mutableStateOf(false) }

    LaunchedEffect(autoPlay) {
        if (!autoPlay) {
            userPaused = false
        }
    }

    val shouldPlay = autoPlay && !userPaused

    val videoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
        }
    }

    val musicPlayer = remember(musicUrl) {
        musicUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        }
    }

    LaunchedEffect(shouldPlay, isMuted, videoPlayer, musicPlayer) {
        videoPlayer.volume = when {
            isMuted -> 0f
            musicPlayer != null -> 0.25f
            else -> 1f
        }
        videoPlayer.playWhenReady = shouldPlay
        musicPlayer?.volume = if (isMuted) 0f else 1f
        musicPlayer?.playWhenReady = shouldPlay
    }

    DisposableEffect(videoPlayer, musicPlayer) {
        onDispose {
            videoPlayer.release()
            musicPlayer?.release()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable {
                clickHaptic()
                userPaused = !userPaused
            }
            .testTag("feed_autoplay_video")
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = videoPlayer
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = !shouldPlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproduzir vídeo",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        if (!musicTitle.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = OrangeVibrant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = musicTitle,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        IconButton(
            onClick = {
                clickHaptic()
                isMuted = !isMuted
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Ativar som" else "Silenciar",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PostActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    isHighlighted: Boolean,
    iconScale: Float = 1f,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(19.dp)
                .scale(iconScale)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
