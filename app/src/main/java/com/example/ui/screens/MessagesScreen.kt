package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Badge
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.ConversationItem
import com.example.data.model.DeliveryStatus
import com.example.data.model.MessageType
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.rememberClickHaptic
import com.example.ui.theme.CrimsonAngola
import com.example.ui.theme.LocalMeuPaisColors
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OrangeVibrant
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    conversations: List<ConversationItem>,
    discoveredUsers: List<UserProfile>,
    activeConversationId: String?,
    activeCallTitle: String?,
    activeCallIsVideo: Boolean,
    liveKitConfigured: Boolean,
    onBackToFeed: () -> Unit,
    onOpenConversation: (String?) -> Unit,
    onStartDirectChat: (String) -> Unit,
    onSendMessage: (conversationId: String, text: String, type: MessageType, mediaUri: Uri?, localAudioFile: File?) -> Unit,
    onStartCall: (title: String, isVideo: Boolean) -> Unit,
    onEndCall: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    if (activeCallTitle != null) {
        ActiveCallOverlay(
            title = activeCallTitle,
            isVideo = activeCallIsVideo,
            liveKitConfigured = liveKitConfigured,
            onEndCall = onEndCall
        )
        return
    }

    val activeConversation = conversations.find { it.id == activeConversationId }
    if (activeConversation != null) {
        ChatDetailView(
            conversation = activeConversation,
            onBack = { onOpenConversation(null) },
            onSendMessage = { text, type, mediaUri, audioFile ->
                onSendMessage(activeConversation.id, text, type, mediaUri, audioFile)
            },
            onStartCall = { isVideo -> onStartCall(activeConversation.title, isVideo) },
            onShowMessage = onShowMessage
        )
        return
    }

    BackHandler { onBackToFeed() }

    val colors = LocalMeuPaisColors.current
    val clickHaptic = rememberClickHaptic()
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("Todas") }
    var showNewChatSheet by remember { mutableStateOf(false) }

    val filtered = remember(conversations, searchQuery, filterTab) {
        conversations.filter { conv ->
            val matchesQuery = searchQuery.isBlank() || conv.title.contains(searchQuery, ignoreCase = true)
            val matchesTab = when (filterTab) {
                "Não lidas" -> conv.unreadCount > 0
                "Grupos" -> conv.isGroup
                else -> true
            }
            matchesQuery && matchesTab
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            clickHaptic()
                            onBackToFeed()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                        Text(
                            text = "Mensagens",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                clickHaptic()
                                showNewChatSheet = true
                            },
                            modifier = Modifier.testTag("new_chat_button")
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Nova conversa", tint = OrangeVibrant)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Pesquisar conversas...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpar pesquisa")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf("Todas", "Não lidas", "Grupos")) { tab ->
                            FilterChip(
                                selected = filterTab == tab,
                                onClick = {
                                    clickHaptic()
                                    filterTab = tab
                                },
                                shape = RoundedCornerShape(8.dp),
                                label = { Text(tab, maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangeVibrant,
                                    selectedLabelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = filterTab == tab,
                                    borderColor = colors.cardBorder
                                ),
                                modifier = Modifier.testTag("messages_filter_$tab")
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            if (filtered.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        title = "Sem conversas ainda",
                        description = "Inicia uma conversa privada em tempo real com outro utilizador autenticado do Socializa.",
                        actionLabel = "Nova conversa",
                        onAction = { showNewChatSheet = true },
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(filtered, key = { it.id }) { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .clickable {
                                clickHaptic()
                                onOpenConversation(conv.id)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("conversation_${conv.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            UserAvatar(
                                name = conv.title,
                                avatarUrl = conv.avatarUrl,
                                size = 50.dp
                            )
                            if (conv.isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(13.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(OnlineGreen)
                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = conv.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = conv.timeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = conv.lastMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (conv.unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Badge(containerColor = CrimsonAngola, contentColor = Color.White) {
                                        Text(conv.unreadCount.toString())
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        color = colors.divider,
                        thickness = 0.7.dp,
                        modifier = Modifier.padding(start = 78.dp, end = 16.dp)
                    )
                }
            }
        }
    }

    if (showNewChatSheet) {
        ModalBottomSheet(onDismissRequest = { showNewChatSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Iniciar conversa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                if (discoveredUsers.isEmpty()) {
                    Text(
                        text = "Ainda não existem outros utilizadores registados disponíveis para conversar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                        items(discoveredUsers, key = { it.id }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 60.dp)
                                    .clickable {
                                        clickHaptic()
                                        showNewChatSheet = false
                                        onStartDirectChat(user.id)
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = user.fullName, avatarUrl = user.avatarUri, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.fullName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        text = "@${user.username} • ${user.province}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatDetailView(
    conversation: ConversationItem,
    onBack: () -> Unit,
    onSendMessage: (String, MessageType, Uri?, File?) -> Unit,
    onStartCall: (isVideo: Boolean) -> Unit,
    onShowMessage: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clickHaptic = rememberClickHaptic()
    val colors = LocalMeuPaisColors.current
    var draft by remember { mutableStateOf("") }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var currentAudioFile by remember { mutableStateOf<File?>(null) }

    fun stopAndCleanupRecorder(sendAudio: Boolean) {
        val rec = recorder
        val file = currentAudioFile
        recorder = null
        currentAudioFile = null
        isRecordingAudio = false
        if (rec != null) {
            runCatching {
                rec.stop()
                rec.release()
            }.onFailure {
                runCatching { rec.release() }
            }
            if (sendAudio && file != null && file.exists() && file.length() > 0L) {
                onSendMessage("Nota de voz", MessageType.AUDIO, null, file)
            } else {
                file?.delete()
            }
        }
    }

    fun startVoiceRecording() {
        val outputFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(64_000)
            rec.setAudioSamplingRate(22_050)
            rec.setOutputFile(outputFile.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            currentAudioFile = outputFile
            isRecordingAudio = true
        }.onFailure {
            runCatching { rec.release() }
            outputFile.delete()
            onShowMessage("Não foi possível iniciar o microfone neste dispositivo.")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAndCleanupRecorder(sendAudio = false)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            onShowMessage("Permissão de microfone necessária para enviar áudio.")
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val msgType = if (mime.startsWith("video/")) MessageType.VIDEO else MessageType.IMAGE
            val label = if (msgType == MessageType.VIDEO) "Vídeo partilhado" else "Fotografia partilhada"
            onSendMessage(label, msgType, uri, null)
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onSendMessage("Documento enviado", MessageType.DOCUMENT, uri, null)
        }
    }

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
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        clickHaptic()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                    UserAvatar(
                        name = conversation.title,
                        avatarUrl = conversation.avatarUrl,
                        size = 38.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (conversation.isGroup) "${conversation.memberCount} membros • Ativo"
                            else if (conversation.isOnline) "Online agora" else "Ligado",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (conversation.isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            clickHaptic()
                            onStartCall(false)
                        },
                        modifier = Modifier.testTag("start_voice_call")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Chamada de voz", tint = OrangeVibrant)
                    }
                    IconButton(
                        onClick = {
                            clickHaptic()
                            onStartCall(true)
                        },
                        modifier = Modifier.testTag("start_video_call")
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Chamada de vídeo", tint = OrangeVibrant)
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(conversation.messages, key = { it.id }) { msg ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (msg.isMine) OrangeVibrant else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = if (msg.isMine) null else BorderStroke(0.7.dp, colors.cardBorder),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (!msg.isMine && conversation.isGroup) {
                                Text(
                                    text = msg.senderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = OrangeVibrant
                                )
                            }
                            if (!msg.attachmentUrl.isNullOrBlank()) {
                                when (msg.type) {
                                    MessageType.IMAGE -> {
                                        AsyncImage(
                                            model = msg.attachmentUrl,
                                            contentDescription = msg.attachmentName ?: "Fotografia",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { runCatching { uriHandler.openUri(msg.attachmentUrl) } }
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    MessageType.VIDEO -> {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { runCatching { uriHandler.openUri(msg.attachmentUrl) } }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Filled.PlayCircleOutline, contentDescription = "Abrir vídeo", tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = msg.attachmentName ?: "Abrir vídeo",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    MessageType.AUDIO -> {
                                        Surface(
                                            color = if (msg.isMine) Color.White.copy(alpha = 0.2f) else colors.elevatedSurface,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { runCatching { uriHandler.openUri(msg.attachmentUrl) } }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.GraphicEq,
                                                    contentDescription = "Reproduzir áudio",
                                                    tint = if (msg.isMine) Color.White else OrangeVibrant
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Ouvir áudio",
                                                    color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    MessageType.DOCUMENT -> {
                                        Surface(
                                            color = if (msg.isMine) Color.White.copy(alpha = 0.2f) else colors.elevatedSurface,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { runCatching { uriHandler.openUri(msg.attachmentUrl) } }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.Description,
                                                    contentDescription = "Abrir documento",
                                                    tint = if (msg.isMine) Color.White else OrangeVibrant
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = msg.attachmentName ?: "Documento anexo",
                                                    color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    else -> Unit
                                }
                            }
                            if (msg.body.isNotBlank()) {
                                Text(
                                    text = msg.body,
                                    color = if (msg.isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                modifier = Modifier.align(Alignment.End),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = msg.timeLabel,
                                    fontSize = 10.sp,
                                    color = if (msg.isMine) Color.White.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (msg.isMine) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.DoneAll,
                                        contentDescription = msg.deliveryStatus.label,
                                        tint = if (msg.deliveryStatus == DeliveryStatus.READ) Color(0xFFBAE6FD) else Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                if (isRecordingAudio) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = CrimsonAngola)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "A gravar áudio...",
                            color = CrimsonAngola,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { stopAndCleanupRecorder(sendAudio = false) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancelar gravação")
                        }
                        IconButton(
                            onClick = { stopAndCleanupRecorder(sendAudio = true) },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OrangeVibrant)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Enviar nota de voz", tint = Color.White)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(onClick = {
                                clickHaptic()
                                showAttachMenu = true
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = "Anexar", tint = OrangeVibrant)
                            }
                            DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, tint = OrangeVibrant) },
                                    text = { Text("Fotografia ou Vídeo") },
                                    onClick = {
                                        clickHaptic()
                                        showAttachMenu = false
                                        mediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null, tint = OrangeVibrant) },
                                    text = { Text("Documento") },
                                    onClick = {
                                        clickHaptic()
                                        showAttachMenu = false
                                        documentLauncher.launch(arrayOf("application/pdf", "application/*", "text/*"))
                                    }
                                )
                            }
                        }
                        IconButton(onClick = {
                            clickHaptic()
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) startVoiceRecording()
                            else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = "Gravar áudio", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text("Mensagem...", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                clickHaptic()
                                if (draft.isNotBlank()) {
                                    onSendMessage(draft, MessageType.TEXT, null, null)
                                    draft = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OrangeVibrant)
                                .testTag("chat_send_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveCallOverlay(
    title: String,
    isVideo: Boolean,
    liveKitConfigured: Boolean,
    onEndCall: () -> Unit
) {
    BackHandler { onEndCall() }
    val pulseTransition = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "call_ring_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isVideo) "Chamada de Vídeo" else "Chamada de Voz",
                color = OrangeVibrant,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (liveKitConfigured) "Ligado com áudio encriptado"
                else "A estabelecer ligação segura...",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .border(1.dp, OrangeVibrant.copy(alpha = 0.35f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(OrangeVibrant.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Call,
                    contentDescription = null,
                    tint = OrangeVibrant,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        IconButton(
            onClick = onEndCall,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(CrimsonAngola)
                .testTag("end_call_button")
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "Terminar chamada", tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
