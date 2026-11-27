package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PostComment
import com.example.data.model.StoryItem
import com.example.data.model.UserProfile
import com.example.ui.BottomTab
import com.example.ui.theme.CrimsonAngola
import com.example.ui.theme.GoldStar
import com.example.ui.theme.LocalMeuPaisColors
import com.example.ui.theme.OrangeVibrant
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

fun formatCompactCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale("pt", "PT"), "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale("pt", "PT"), "%.1f mil", count / 1_000.0)
        else -> count.toString()
    }
}

fun formatKwanza(value: Long): String {
    val formatter = NumberFormat.getIntegerInstance(Locale("pt", "PT"))
    return "${formatter.format(value)} Kz"
}

@Composable
fun UserAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val colors = LocalMeuPaisColors.current
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.elevatedSurface)
        )
    } else {
        val initials = name.trim().split(" ").filter { it.isNotBlank() }
            .take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "U" }
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(OrangeVibrant.copy(alpha = 0.14f))
                .border(1.dp, OrangeVibrant.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = OrangeVibrant,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalMeuPaisColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(OrangeVibrant.copy(alpha = 0.12f))
                .border(0.8.dp, colors.cardBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = OrangeVibrant, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            val clickHaptic = rememberClickHaptic()
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    clickHaptic()
                    onAction()
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(actionLabel, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MeuPaisLogoEmblem(
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_socializa_round_badge),
        contentDescription = "Socializa",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    )
}

@Composable
fun VerifiedCheckBadge(modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = "Conta verificada",
        tint = LocalMeuPaisColors.current.verifiedBadge,
        modifier = modifier.size(size)
    )
}

@Composable
fun MeuPaisBottomBar(
    currentTab: BottomTab,
    onSelectTab: (BottomTab) -> Unit,
    onOpenCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalMeuPaisColors.current
    val clickHaptic = rememberClickHaptic()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTab.entries.forEach { tab ->
                    if (tab == BottomTab.CREATE) {
                        val createInteraction = remember { MutableInteractionSource() }
                        val createPressed by createInteraction.collectIsPressedAsState()
                        val createScale by animateFloatAsState(
                            targetValue = if (createPressed) 0.92f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "createScale"
                        )
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .scale(createScale)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = createInteraction,
                                    indication = null
                                ) {
                                    clickHaptic()
                                    onOpenCreate()
                                }
                                .padding(4.dp)
                                .testTag("bottom_tab_create"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OrangeVibrant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Criar publicação",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        val selected = currentTab == tab
                        val tint by animateColorAsState(
                            targetValue = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "tabTint"
                        )
                        val tabScale by animateFloatAsState(
                            targetValue = if (selected) 1.06f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "tabScale"
                        )
                        val icon = when (tab) {
                            BottomTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                            BottomTab.EXPLORE -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
                            BottomTab.VIDEOS -> if (selected) Icons.Filled.PlayCircleFilled else Icons.Outlined.PlayCircle
                            BottomTab.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                            BottomTab.CREATE -> Icons.Filled.Add
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    clickHaptic()
                                    onSelectTab(tab)
                                }
                                .padding(horizontal = 2.dp, vertical = 4.dp)
                                .testTag("bottom_tab_${tab.route}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(tabScale)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = tint,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    currentUid: String,
    comments: List<PostComment>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSendComment: (body: String, replyTo: PostComment?) -> Unit,
    onToggleLikeComment: (String) -> Unit,
    onDeleteComment: (String) -> Unit
) {
    val colors = LocalMeuPaisColors.current
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<PostComment?>(null) }

    val rootComments = remember(comments) { comments.filter { it.parentId == null } }
    val repliesByParent = remember(comments) { comments.filter { it.parentId != null }.groupBy { it.parentId } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Comentários (${comments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OrangeVibrant, modifier = Modifier.size(28.dp))
                }
            } else if (comments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sê o primeiro a comentar nesta publicação!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rootComments, key = { it.id }) { item ->
                        CommentRowItem(
                            comment = item,
                            isOwn = item.authorId == currentUid,
                            isReply = false,
                            onReply = { replyingTo = item },
                            onLike = { onToggleLikeComment(item.id) },
                            onDelete = { onDeleteComment(item.id) }
                        )
                        repliesByParent[item.id].orEmpty().forEach { reply ->
                            Spacer(modifier = Modifier.height(8.dp))
                            CommentRowItem(
                                comment = reply,
                                isOwn = reply.authorId == currentUid,
                                isReply = true,
                                onReply = { replyingTo = item },
                                onLike = { onToggleLikeComment(reply.id) },
                                onDelete = { onDeleteComment(reply.id) }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = replyingTo != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                replyingTo?.let { target ->
                    Surface(
                        color = OrangeVibrant.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "A responder a @${target.authorUsername}",
                                style = MaterialTheme.typography.labelMedium,
                                color = OrangeVibrant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { replyingTo = null },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancelar resposta", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            val sendHaptic = rememberClickHaptic()
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(if (replyingTo != null) "Escreve uma resposta..." else "Escreve um comentário...")
                    },
                    modifier = Modifier.weight(1f).testTag("comment_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        sendHaptic()
                        if (draft.isNotBlank()) {
                            onSendComment(draft, replyingTo)
                            draft = ""
                            replyingTo = null
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OrangeVibrant)
                        .testTag("send_comment_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar comentário", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CommentRowItem(
    comment: PostComment,
    isOwn: Boolean,
    isReply: Boolean,
    onReply: () -> Unit,
    onLike: () -> Unit,
    onDelete: () -> Unit
) {
    val clickHaptic = rememberClickHaptic()
    val likeScale by animateFloatAsState(
        targetValue = if (comment.isLiked) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "commentLikeScale"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 36.dp else 0.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(
            name = comment.authorName,
            avatarUrl = comment.authorAvatarUrl,
            size = if (isReply) 28.dp else 34.dp
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "@${comment.authorUsername} • ${comment.timeAgo}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Responder",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            clickHaptic()
                            onReply()
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
                if (isOwn) {
                    IconButton(
                        onClick = {
                            clickHaptic()
                            onDelete()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Eliminar comentário",
                            tint = CrimsonAngola,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = {
                    clickHaptic()
                    onLike()
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (comment.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Curtir comentário",
                    tint = if (comment.isLiked) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(likeScale)
                )
            }
            if (comment.likesCount > 0) {
                Text(
                    text = comment.likesCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectedUserProfileSheet(
    user: UserProfile,
    onDismiss: () -> Unit,
    onToggleFollow: () -> Unit,
    onMessage: () -> Unit,
    onBlock: () -> Unit,
    onReport: (String) -> Unit
) {
    val colors = LocalMeuPaisColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            val clickHaptic = rememberClickHaptic()
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(name = user.fullName, avatarUrl = user.avatarUri, size = 64.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (user.hasActiveVerifiedBadge) {
                            Spacer(modifier = Modifier.width(5.dp))
                            VerifiedCheckBadge(size = 16.dp)
                        }
                    }
                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = OrangeVibrant, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${user.province}, ${user.country}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (user.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(user.bio, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatCompactCount(user.followersCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Seguidores", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatCompactCount(user.followingCount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("A seguir", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(user.postsCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Publicações", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        clickHaptic()
                        onToggleFollow()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isFollowedByMe) MaterialTheme.colorScheme.surfaceVariant else OrangeVibrant
                    ),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(
                        text = if (user.isFollowedByMe) "A seguir" else "Seguir",
                        color = if (user.isFollowedByMe) MaterialTheme.colorScheme.onSurface else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = {
                        clickHaptic()
                        onMessage()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.9.dp, colors.cardBorder),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Mensagem", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = {
                        clickHaptic()
                        onReport("Comportamento abusivo ou spam")
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Denunciar utilizador", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = {
                        clickHaptic()
                        onBlock()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Bloquear", color = CrimsonAngola, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StoryViewerDialog(
    story: StoryItem,
    onDismiss: () -> Unit,
    onReplyStory: (String) -> Unit
) {
    val clickHaptic = rememberClickHaptic()
    var progress by remember(story.id) { mutableFloatStateOf(0f) }
    var replyText by remember { mutableStateOf("") }

    LaunchedEffect(story.id) {
        while (progress < 1f) {
            delay(60)
            progress += 0.01f
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = story.mediaUrl,
                contentDescription = story.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = OrangeVibrant,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(name = story.authorName, avatarUrl = story.avatarUrl, size = 38.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(story.authorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "@${story.authorUsername} • ${story.timeAgo} (24h)",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            clickHaptic()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("close_story_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar story", tint = Color.White)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                if (story.caption.isNotBlank()) {
                    Text(
                        text = story.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (!story.isOwn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            placeholder = { Text("Responder ao story...", color = Color.White.copy(alpha = 0.7f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = OrangeVibrant,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.45f),
                                cursorColor = OrangeVibrant
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                clickHaptic()
                                if (replyText.isNotBlank()) {
                                    onReplyStory(replyText)
                                    replyText = ""
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(OrangeVibrant)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar resposta", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
