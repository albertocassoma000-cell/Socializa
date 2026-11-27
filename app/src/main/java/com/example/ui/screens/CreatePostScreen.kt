package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.PostType
import com.example.data.model.PostVisibility
import com.example.data.remote.MediaUploadHelper
import com.example.ui.components.rememberClickHaptic
import com.example.ui.theme.LocalMeuPaisColors
import com.example.ui.theme.OrangeVibrant

@Composable
fun CreatePostScreen(
    provinces: List<String>,
    isPublishing: Boolean,
    onClose: () -> Unit,
    onPublish: (
        caption: String,
        postType: PostType,
        category: String?,
        location: String,
        hashtags: String,
        visibility: PostVisibility,
        mediaUris: List<Uri>,
        publishAlsoToReels: Boolean,
        audioUri: Uri?,
        audioTitle: String?
    ) -> Unit
) {
    BackHandler(enabled = !isPublishing) { onClose() }

    val context = LocalContext.current
    val clickHaptic = rememberClickHaptic()
    val colors = LocalMeuPaisColors.current
    val categories = listOf("Geral", "Música", "Desporto", "Humor", "Tecnologia", "Moda", "Viagens", "Cultura")

    var caption by remember { mutableStateOf("") }
    var hashtags by remember { mutableStateOf("#MeuPais #Angola") }
    var selectedType by remember { mutableStateOf(PostType.PHOTO) }
    var selectedCategory by remember { mutableStateOf("Geral") }
    var selectedLocation by remember { mutableStateOf("${provinces.firstOrNull() ?: "Luanda"}, Angola") }
    var selectedVisibility by remember { mutableStateOf(PostVisibility.PUBLIC) }
    var publishToReels by remember { mutableStateOf(false) }
    var selectedMediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioTitle by remember { mutableStateOf<String?>(null) }

    var showLocationMenu by remember { mutableStateOf(false) }
    var showVisibilityMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedMediaUris = listOf(uri)
        }
    }

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedMediaUris = uris
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedAudioUri = uri
            val rawName = MediaUploadHelper.resolveDisplayName(context, uri)
            selectedAudioTitle = rawName.substringBeforeLast(".").ifBlank { rawName }
        }
    }

    val canPublish = !isPublishing && (
        caption.isNotBlank() ||
            selectedMediaUris.isNotEmpty()
        ) && (selectedType != PostType.STORY && selectedType != PostType.VIDEO || selectedMediaUris.isNotEmpty())

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
                        .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            clickHaptic()
                            onClose()
                        },
                        enabled = !isPublishing,
                        modifier = Modifier.testTag("create_close_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar")
                    }
                    Text(
                        text = "Criar publicação",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            clickHaptic()
                            onPublish(
                                caption,
                                selectedType,
                                selectedCategory.takeIf { it != "Geral" },
                                selectedLocation,
                                hashtags,
                                selectedVisibility,
                                selectedMediaUris,
                                publishToReels,
                                selectedAudioUri,
                                selectedAudioTitle
                            )
                        },
                        enabled = canPublish,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("publish_post_button")
                    ) {
                        if (isPublishing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Publicar", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .animateContentSize(animationSpec = tween(200))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (selectedType != PostType.TEXT) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (selectedMediaUris.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(selectedMediaUris) { uri ->
                                Box(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(196.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Mídia selecionada",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    IconButton(
                                        onClick = {
                                            clickHaptic()
                                            selectedMediaUris = selectedMediaUris - uri
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(48.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.65f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Remover", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.7.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp)
                            .clickable {
                                clickHaptic()
                                when (selectedType) {
                                    PostType.CAROUSEL -> multiPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    PostType.VIDEO -> singlePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                    else -> singlePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                }
                            }
                            .testTag("pick_media_box")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = OrangeVibrant)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    selectedMediaUris.isNotEmpty() -> "Alterar ou adicionar mídia (${selectedMediaUris.size})"
                                    selectedType == PostType.VIDEO -> "Selecionar vídeo da galeria"
                                    selectedType == PostType.CAROUSEL -> "Selecionar até 5 fotografias"
                                    selectedType == PostType.STORY -> "Selecionar foto ou vídeo para o Story (24h)"
                                    else -> "Selecionar fotografia da galeria"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (selectedType == PostType.VIDEO) {
                Surface(
                    color = if (selectedAudioUri != null) OrangeVibrant.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        width = 0.7.dp,
                        color = if (selectedAudioUri != null) OrangeVibrant else colors.cardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable {
                            clickHaptic()
                            audioPickerLauncher.launch("audio/*")
                        }
                        .testTag("pick_video_music_box")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Adicionar música do telemóvel",
                            tint = OrangeVibrant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedAudioTitle ?: "Adicionar música do telemóvel",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedAudioUri != null) {
                                    "Música selecionada para este vídeo"
                                } else {
                                    "Escolhe uma música ou áudio guardado no teu dispositivo"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selectedAudioUri != null) {
                            IconButton(
                                onClick = {
                                    clickHaptic()
                                    selectedAudioUri = null
                                    selectedAudioTitle = null
                                },
                                modifier = Modifier.testTag("clear_video_music_button")
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remover música")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                placeholder = { Text("O que estás a pensar? Partilha com a tua comunidade...") },
                minLines = 3,
                maxLines = 7,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("post_caption_input")
            )

            OutlinedTextField(
                value = hashtags,
                onValueChange = { hashtags = it },
                label = { Text("Hashtags") },
                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, tint = OrangeVibrant) },
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.7.dp, colors.cardBorder)
            ) {
                Column {
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .clickable {
                                    clickHaptic()
                                    showCategoryMenu = true
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Category, contentDescription = null, tint = OrangeVibrant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Categoria", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(selectedCategory, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        clickHaptic()
                                        selectedCategory = cat
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.divider, thickness = 0.7.dp)

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .clickable {
                                    clickHaptic()
                                    showLocationMenu = true
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = OrangeVibrant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Localização", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(selectedLocation, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = showLocationMenu, onDismissRequest = { showLocationMenu = false }) {
                            provinces.forEach { prov ->
                                DropdownMenuItem(
                                    text = { Text("$prov, Angola") },
                                    onClick = {
                                        clickHaptic()
                                        selectedLocation = "$prov, Angola"
                                        showLocationMenu = false
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.divider, thickness = 0.7.dp)

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .clickable {
                                    clickHaptic()
                                    showVisibilityMenu = true
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = OrangeVibrant)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Privacidade", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text(selectedVisibility.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = showVisibilityMenu, onDismissRequest = { showVisibilityMenu = false }) {
                            PostVisibility.entries.forEach { vis ->
                                DropdownMenuItem(
                                    text = { Text(vis.label) },
                                    onClick = {
                                        clickHaptic()
                                        selectedVisibility = vis
                                        showVisibilityMenu = false
                                    }
                                )
                            }
                        }
                    }

                    if (selectedType == PostType.VIDEO) {
                        Column {
                            HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, tint = OrangeVibrant)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Publicar também nos Vídeos", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = publishToReels,
                                    onCheckedChange = {
                                        clickHaptic()
                                        publishToReels = it
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = OrangeVibrant)
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val options = listOf(
                        PostType.PHOTO to Icons.Filled.PhotoCamera,
                        PostType.VIDEO to Icons.Filled.VideoLibrary,
                        PostType.CAROUSEL to Icons.Filled.Collections,
                        PostType.TEXT to Icons.Filled.TextFields,
                        PostType.STORY to Icons.Filled.AutoAwesome
                    )
                    options.forEach { (type, icon) ->
                        val selected = selectedType == type
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .testTag("create_type_${type.name}")
                                .clickable {
                                    clickHaptic()
                                    selectedType = type
                                    if (type == PostType.VIDEO) publishToReels = true
                                }
                                .padding(horizontal = 2.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = type.label,
                                tint = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
