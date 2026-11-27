package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.data.model.VideoReel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedCheckBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.components.rememberClickHaptic
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OrangeVibrant
import kotlinx.coroutines.delay

@Composable
fun VideosScreen(
    reels: List<VideoReel>,
    isScreenActive: Boolean = true,
    onToggleLike: (String) -> Unit,
    onToggleFollow: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onShareReel: (String) -> Unit,
    onReelVisible: (String) -> Unit,
    onReelTelemetry: (reelId: String, watchSeconds: Int, completed: Boolean, replayed: Boolean) -> Unit = { _, _, _, _ -> },
    onOpenAuthorProfile: (String) -> Unit,
    onOpenCreateVideo: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val clickHaptic = rememberClickHaptic()
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

    var selectedFeedTab by remember { mutableStateOf("Para ti") }
    val filteredReels = remember(reels, selectedFeedTab) {
        if (selectedFeedTab == "Seguindo") {
            reels.filter { it.isFollowing }
        } else {
            reels
        }
    }

    var showCaptions by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (filteredReels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Filled.VideoLibrary,
                    title = if (selectedFeedTab == "Seguindo") {
                        "Sem vídeos de quem segues"
                    } else {
                        "Ainda não existem vídeos"
                    },
                    description = if (selectedFeedTab == "Seguindo") {
                        "Segue criadores em Explorar ou muda para Para ti para ver os vídeos publicados na comunidade."
                    } else {
                        "Publica o primeiro vídeo para aparecer nesta secção!"
                    },
                    actionLabel = if (selectedFeedTab == "Seguindo") "Explorar Criadores" else "Publicar Vídeo",
                    onAction = {
                        if (selectedFeedTab == "Seguindo") onOpenSearch() else onOpenCreateVideo()
                    }
                )
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { filteredReels.size })
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val reel = filteredReels[page]
                val isCurrentPage = pagerState.currentPage == page && isScreenActive && isResumed
                LaunchedEffect(reel.id, isCurrentPage) {
                    if (isCurrentPage) {
                        onReelVisible(reel.id)
                    }
                }
                ReelPageItem(
                    reel = reel,
                    isCurrentPage = isCurrentPage,
                    showCaptions = showCaptions,
                    onToggleCaptions = {
                        clickHaptic()
                        showCaptions = !showCaptions
                    },
                    onAuthorClick = {
                        clickHaptic()
                        onOpenAuthorProfile(reel.authorId)
                    },
                    onToggleLike = {
                        clickHaptic()
                        onToggleLike(reel.id)
                    },
                    onToggleFollow = {
                        clickHaptic()
                        onToggleFollow(reel.authorId)
                    },
                    onOpenComments = {
                        clickHaptic()
                        onOpenComments(reel.id)
                    },
                    onShare = {
                        clickHaptic()
                        onShareReel(reel.id)
                    },
                    onTelemetry = { watchSec, completed, replayed ->
                        onReelTelemetry(reel.id, watchSec, completed, replayed)
                    }
                )
            }
        }

        // Cabeçalho simétrico Para ti | Seguindo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(48.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                listOf("Para ti", "Seguindo").forEach { tab ->
                    val selected = selectedFeedTab == tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                clickHaptic()
                                selectedFeedTab = tab
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("videos_tab_$tab")
                    ) {
                        Text(
                            text = tab,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(26.dp)
                                .height(2.5.dp)
                                .background(
                                    if (selected) OrangeVibrant else Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            IconButton(onClick = {
                clickHaptic()
                onOpenSearch()
            }) {
                Icon(Icons.Filled.Search, contentDescription = "Pesquisar vídeos", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ReelPageItem(
    reel: VideoReel,
    isCurrentPage: Boolean,
    showCaptions: Boolean,
    onToggleCaptions: () -> Unit,
    onAuthorClick: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleFollow: () -> Unit,
    onOpenComments: () -> Unit,
    onShare: () -> Unit,
    onTelemetry: (watchSeconds: Int, completed: Boolean, replayed: Boolean) -> Unit
) {
    val context = LocalContext.current
    val clickHaptic = rememberClickHaptic()
    var isPlaying by remember { mutableStateOf(true) }
    var watchedSeconds by remember(reel.id) { mutableIntStateOf(0) }
    var hasCompleted by remember(reel.id) { mutableStateOf(false) }
    var hasReplayed by remember(reel.id) { mutableStateOf(false) }

    LaunchedEffect(reel.id, isCurrentPage, isPlaying) {
        if (!isCurrentPage || !isPlaying) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            watchedSeconds++
            if (watchedSeconds % 5 == 0) {
                onTelemetry(watchedSeconds, hasCompleted, hasReplayed)
            }
        }
    }

    DisposableEffect(reel.id) {
        onDispose {
            if (watchedSeconds > 0 || hasCompleted || hasReplayed) {
                onTelemetry(watchedSeconds.coerceAtLeast(1), hasCompleted, hasReplayed)
            }
        }
    }

    val likeScale by animateFloatAsState(
        targetValue = if (reel.isLiked) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "reel_like_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                clickHaptic()
                isPlaying = !isPlaying
            }
    ) {
        if (!reel.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = reel.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (reel.streamUrl.isNotBlank()) {
            val exoPlayer = remember(reel.streamUrl) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(reel.streamUrl)))
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                }
            }
            val musicPlayer = remember(reel.musicUrl) {
                reel.musicUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                        repeatMode = Player.REPEAT_MODE_ONE
                        prepare()
                    }
                }
            }
            val activePlay = isCurrentPage && isPlaying
            exoPlayer.volume = if (musicPlayer != null) 0.25f else 1f
            exoPlayer.playWhenReady = activePlay
            musicPlayer?.playWhenReady = activePlay

            DisposableEffect(exoPlayer, musicPlayer) {
                val listener = object : Player.Listener {
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                            val wasCompleted = hasCompleted
                            hasCompleted = true
                            if (wasCompleted) hasReplayed = true
                            onTelemetry(watchedSeconds.coerceAtLeast(3), hasCompleted, hasReplayed)
                        }
                    }
                }
                exoPlayer.addListener(listener)
                onDispose {
                    exoPlayer.removeListener(listener)
                    exoPlayer.release()
                    musicPlayer?.release()
                }
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.84f)
                        )
                    )
                )
        )

        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproduzir",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        if (showCaptions && reel.autoSubtitle.isNotBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 88.dp, bottom = 152.dp)
            ) {
                Text(
                    text = reel.autoSubtitle,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(contentAlignment = Alignment.BottomCenter) {
                UserAvatar(
                    name = reel.authorName,
                    avatarUrl = reel.authorAvatarUrl,
                    size = 48.dp,
                    modifier = Modifier
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { onAuthorClick() }
                )
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable { onToggleFollow() }
                        .testTag("reel_follow_${reel.id}"),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (reel.isFollowing) OnlineGreen else OrangeVibrant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (reel.isFollowing) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = "Seguir",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            ReelActionIcon(
                icon = Icons.Filled.Favorite,
                label = formatCompactCount(reel.likesCount),
                tint = if (reel.isLiked) OrangeVibrant else Color.White,
                iconScale = likeScale,
                onClick = onToggleLike,
                tag = "reel_like_${reel.id}"
            )
            ReelActionIcon(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = formatCompactCount(reel.commentsCount),
                tint = Color.White,
                onClick = onOpenComments,
                tag = "reel_comment_${reel.id}"
            )
            ReelActionIcon(
                icon = Icons.Filled.Share,
                label = formatCompactCount(reel.sharesCount),
                tint = Color.White,
                onClick = onShare,
                tag = "reel_share_${reel.id}"
            )
            IconButton(onClick = onToggleCaptions) {
                Icon(
                    imageVector = Icons.Filled.ClosedCaption,
                    contentDescription = "Legendas",
                    tint = if (showCaptions) OrangeVibrant else Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 88.dp, bottom = 22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onAuthorClick() }
            ) {
                Text(
                    text = "@${reel.authorUsername}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (reel.isVerified) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VerifiedCheckBadge(size = 15.dp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${formatCompactCount(reel.viewsCount)} vis.",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (reel.caption.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = reel.caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (reel.hashtags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reel.hashtags.joinToString(" "),
                    color = OrangeVibrant,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = reel.musicTitle,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Estado",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ReelActionIcon(
    icon: ImageVector,
    label: String,
    tint: Color,
    iconScale: Float = 1f,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier
                .size(28.dp)
                .scale(iconScale)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}
