package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.AuditLogEntry
import com.example.data.model.CommunityGroup
import com.example.data.model.CreatorStats
import com.example.data.model.FeedPost
import com.example.data.model.ModerationReport
import com.example.data.model.ThemeMode
import com.example.data.model.UserProfile
import com.example.data.model.VideoReel
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedCheckBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.components.rememberClickHaptic
import com.example.ui.theme.CrimsonAngola
import com.example.ui.theme.LocalMeuPaisColors
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.OrangeVibrant
import com.example.ui.theme.VerifiedBlue
import kotlinx.coroutines.delay

private data class SponsorTextAd(
    val advertiser: String,
    val headline: String,
    val body: String,
    val domain: String
)

private val DAILY_TEXT_ADS = listOf(
    SponsorTextAd(
        advertiser = "Unitel Energia Digital",
        headline = "Pacotes sociais ilimitados para partilhar fotos e vídeos",
        body = "Navega nas tuas comunidades favoritas em todas as províncias com cobertura nacional rápida e estável.",
        domain = "unitel.ao/planos"
    ),
    SponsorTextAd(
        advertiser = "Kwanza Pay",
        headline = "Pagamentos e transferências instantâneas pelo telemóvel",
        body = "Envia e recebe valores entre amigos ou paga compras no Marketplace em segundos, sem comissões ocultas.",
        domain = "kwanzapay.ao"
    ),
    SponsorTextAd(
        advertiser = "Sabores de Luanda",
        headline = "Mufete, calulu e pratos típicos entregues à tua porta",
        body = "Descobre restaurantes locais perto de ti e encomenda refeições frescas preparadas na hora.",
        domain = "saboresdeluanda.ao"
    ),
    SponsorTextAd(
        advertiser = "Academia Futuro Angola",
        headline = "Cursos práticos de programação, design e empreendedorismo",
        body = "Aprende competências digitais com mentores locais e recebe certificado reconhecido no mercado.",
        domain = "academiafuturo.ao"
    ),
    SponsorTextAd(
        advertiser = "Viaja Angola",
        headline = "Roteiros exclusivos pelo Namibe, Huíla e Benguela",
        body = "Reserva alojamentos, passeios pela Serra da Leba e experiências culturais com desconto de fim de semana.",
        domain = "viajaangola.ao"
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    user: UserProfile,
    userPosts: List<FeedPost>,
    savedPosts: List<FeedPost>,
    reels: List<VideoReel>,
    groups: List<CommunityGroup>,
    reports: List<ModerationReport>,
    auditLogs: List<AuditLogEntry>,
    creatorStats: CreatorStats,
    themeMode: ThemeMode,
    provinces: List<String>,
    adsWatchedCount: Int = 0,
    isClaimingVerification: Boolean = false,
    onAdWatched: () -> Unit = {},
    onClaimVerificationBadge: () -> Unit = {},
    onSetThemeMode: (ThemeMode) -> Unit,
    onEditProfile: (fullName: String, username: String, bio: String, province: String, website: String, isPrivate: Boolean, newAvatarUri: Uri?) -> Unit,
    onOpenPostComments: (String) -> Unit,
    onToggleGroup: (String) -> Unit,
    onResolveReport: (reportId: String, action: String) -> Unit,
    onOpenExploreMarket: () -> Unit,
    onRequestPushPermission: () -> Unit,
    onSignOut: () -> Unit
) {
    val colors = LocalMeuPaisColors.current
    val clickHaptic = rememberClickHaptic()
    val isStaff = user.role in listOf("admin", "moderator")
    var selectedGridTab by remember { mutableIntStateOf(0) }
    var showEditProfileSheet by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showGroupsSheet by remember { mutableStateOf(false) }
    var showAdminSheet by remember { mutableStateOf(false) }
    var showStatsSheet by remember { mutableStateOf(false) }
    var showTextAdsDialog by remember { mutableStateOf(false) }

    val hasActiveBadge = user.hasActiveVerifiedBadge
    val remainingHours = remember(user.verifiedUntilMs, hasActiveBadge) {
        if (!hasActiveBadge || user.verifiedUntilMs <= 0L) 0
        else (((user.verifiedUntilMs - System.currentTimeMillis()).coerceAtLeast(0L) + 3_599_999L) / 3_600_000L).toInt()
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
                        .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "@${user.username}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            clickHaptic()
                            showSettingsSheet = true
                        },
                        modifier = Modifier.testTag("profile_settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurações")
                    }
                    IconButton(
                        onClick = {
                            clickHaptic()
                            showMenuSheet = true
                        },
                        modifier = Modifier.testTag("profile_menu_button")
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu principal")
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            name = user.fullName,
                            avatarUrl = user.avatarUri,
                            size = 80.dp,
                            modifier = Modifier.border(2.dp, OrangeVibrant, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            ProfileStatColumn(formatCompactCount(user.followersCount), "Seguidores")
                            ProfileStatColumn(formatCompactCount(user.followingCount), "A seguir")
                            ProfileStatColumn(user.postsCount.toString(), "Publicações")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (hasActiveBadge) {
                            Spacer(modifier = Modifier.width(6.dp))
                            VerifiedCheckBadge(size = 17.dp)
                        }
                        if (user.isPrivate) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = colors.elevatedSurface,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.7.dp, colors.cardBorder)
                            ) {
                                Text(
                                    text = "Privado",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (user.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = user.bio, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = OrangeVibrant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${user.province}, ${user.country}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (user.website.isNotBlank()) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                tint = OrangeVibrant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = user.website,
                                style = MaterialTheme.typography.bodySmall,
                                color = OrangeVibrant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                clickHaptic()
                                showEditProfileSheet = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("edit_profile_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.7.dp, colors.cardBorder)
                        ) {
                            Text("Editar perfil", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                        if (isStaff) {
                            Button(
                                onClick = {
                                    clickHaptic()
                                    showAdminSheet = true
                                },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("open_admin_shortcut"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
                            ) {
                                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Admin", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = if (hasActiveBadge) VerifiedBlue.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            width = 0.7.dp,
                            color = if (hasActiveBadge) VerifiedBlue.copy(alpha = 0.45f) else colors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("daily_verification_card")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = "Selo de verificação",
                                    tint = if (hasActiveBadge) VerifiedBlue else OrangeVibrant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (hasActiveBadge) "Selo Verificado Ativo" else "Selo de Verificação (24h)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (hasActiveBadge) {
                                            if (remainingHours > 0) "Válido por mais ${remainingHours}h (expira após 1 dia)"
                                            else "Conta verificada"
                                        } else {
                                            "Assiste a 5 anúncios de texto para ativar por 1 dia ($adsWatchedCount/5)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!hasActiveBadge) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            clickHaptic()
                                            showTextAdsDialog = true
                                        },
                                        enabled = !isClaimingVerification,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("open_verification_ads_button")
                                    ) {
                                        if (isClaimingVerification) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text(
                                                text = "Ativar",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                            if (!hasActiveBadge && adsWatchedCount > 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { (adsWatchedCount / 5f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = OrangeVibrant,
                                    trackColor = colors.elevatedSurface
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabs = listOf(
                        Icons.Filled.GridView to "Publicações",
                        Icons.Filled.PlayCircleOutline to "Vídeos",
                        Icons.Filled.Bookmark to "Guardados"
                    )
                    tabs.forEachIndexed { idx, (icon, label) ->
                        val selected = selectedGridTab == idx
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable {
                                    clickHaptic()
                                    selectedGridTab = idx
                                }
                                .padding(top = 10.dp)
                                .testTag("profile_tab_$idx")
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (selected) OrangeVibrant else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(2.dp)
                                    .background(if (selected) OrangeVibrant else Color.Transparent)
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            }

            val gridItems: List<Any> = when (selectedGridTab) {
                0 -> userPosts
                1 -> reels
                else -> savedPosts
            }

            if (gridItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when (selectedGridTab) {
                                1 -> "Ainda não publicaste vídeos."
                                2 -> "Ainda não guardaste publicações."
                                else -> "Ainda não tens publicações."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(gridItems.chunked(3)) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        row.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(colors.elevatedSurface)
                                    .clickable {
                                        clickHaptic()
                                        if (item is FeedPost) onOpenPostComments(item.id)
                                    }
                            ) {
                                when (item) {
                                    is FeedPost -> {
                                        val firstMedia = item.mediaUrls.firstOrNull()
                                        if (!firstMedia.isNullOrBlank()) {
                                            AsyncImage(
                                                model = firstMedia,
                                                contentDescription = item.caption,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = item.caption,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 4,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    is VideoReel -> {
                                        if (!item.thumbnailUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = item.thumbnailUrl,
                                                contentDescription = item.caption,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = item.caption.ifBlank { "Vídeo" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Filled.PlayCircleOutline,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showTextAdsDialog) {
        TextAdsVerificationDialog(
            initialAdsWatched = adsWatchedCount,
            onAdCompleted = onAdWatched,
            onClaimBadge = {
                onClaimVerificationBadge()
                showTextAdsDialog = false
            },
            onDismiss = { showTextAdsDialog = false }
        )
    }

    if (showMenuSheet) {
        ModalBottomSheet(onDismissRequest = { showMenuSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(name = user.fullName, avatarUrl = user.avatarUri, size = 46.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(user.fullName, fontWeight = FontWeight.Bold)
                            if (hasActiveBadge) {
                                Spacer(modifier = Modifier.width(5.dp))
                                VerifiedCheckBadge(size = 15.dp)
                            }
                        }
                        Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                MenuOptionRow(Icons.Filled.Verified, "Selo de Verificação (24h)") {
                    clickHaptic()
                    showMenuSheet = false
                    if (!hasActiveBadge) showTextAdsDialog = true
                }
                MenuOptionRow(Icons.Filled.Groups, "Comunidades & Grupos") {
                    clickHaptic()
                    showMenuSheet = false
                    showGroupsSheet = true
                }
                MenuOptionRow(Icons.Filled.Storefront, "Marketplace") {
                    clickHaptic()
                    showMenuSheet = false
                    onOpenExploreMarket()
                }
                MenuOptionRow(Icons.Filled.BarChart, "Estatísticas da Conta") {
                    clickHaptic()
                    showMenuSheet = false
                    showStatsSheet = true
                }
                if (isStaff) {
                    MenuOptionRow(Icons.Filled.AdminPanelSettings, "Painel Administrativo & Moderação") {
                        clickHaptic()
                        showMenuSheet = false
                        showAdminSheet = true
                    }
                }
                MenuOptionRow(Icons.Filled.Settings, "Configurações & Aparência") {
                    clickHaptic()
                    showMenuSheet = false
                    showSettingsSheet = true
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                MenuOptionRow(Icons.AutoMirrored.Filled.ExitToApp, "Sair da conta", tint = CrimsonAngola) {
                    clickHaptic()
                    showMenuSheet = false
                    onSignOut()
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showEditProfileSheet) {
        var fullName by remember { mutableStateOf(user.fullName) }
        var username by remember { mutableStateOf(user.username) }
        var bio by remember { mutableStateOf(user.bio) }
        var province by remember { mutableStateOf(user.province) }
        var website by remember { mutableStateOf(user.website) }
        var isPrivate by remember { mutableStateOf(user.isPrivate) }
        var pickedAvatarUri by remember { mutableStateOf<Uri?>(null) }

        val avatarPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) pickedAvatarUri = uri
        }

        ModalBottomSheet(onDismissRequest = { showEditProfileSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                Text("Editar Perfil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pickedAvatarUri != null) {
                        AsyncImage(
                            model = pickedAvatarUri,
                            contentDescription = "Nova fotografia",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                        )
                    } else {
                        UserAvatar(name = user.fullName, avatarUrl = user.avatarUri, size = 64.dp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = {
                            clickHaptic()
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.7.dp, colors.cardBorder),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Alterar fotografia")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Nome completo") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Biografia") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = province,
                    onValueChange = { province = it },
                    label = { Text("Província (${provinces.firstOrNull() ?: "Angola"})") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("Website") }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conta privada (aprovar seguidores)", modifier = Modifier.weight(1f))
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = {
                            clickHaptic()
                            isPrivate = it
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = OrangeVibrant)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        clickHaptic()
                        onEditProfile(fullName, username, bio, province, website, isPrivate, pickedAvatarUri)
                        showEditProfileSheet = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Guardar alterações", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Configurações", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Aparência da Interface", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = {
                                clickHaptic()
                                onSetThemeMode(mode)
                            },
                            shape = RoundedCornerShape(8.dp),
                            label = { Text(mode.label, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangeVibrant,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = themeMode == mode,
                                borderColor = colors.cardBorder
                            ),
                            modifier = Modifier.testTag("theme_mode_${mode.name}")
                        )
                    }
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                Text("Conta & Segurança", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = {
                        clickHaptic()
                        onRequestPushPermission()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.7.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Ativar Notificações")
                }
                if (!hasActiveBadge) {
                    Button(
                        onClick = {
                            clickHaptic()
                            showSettingsSheet = false
                            showTextAdsDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Obter Selo por 24h (5 anúncios)", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    Text(
                        text = if (remainingHours > 0) "Selo verificado ativo (expira em ${remainingHours}h)." else "Conta verificada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VerifiedBlue
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showGroupsSheet) {
        ModalBottomSheet(onDismissRequest = { showGroupsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Comunidades & Grupos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                if (groups.isEmpty()) {
                    Text(
                        text = "Ainda não existem comunidades criadas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    groups.forEach { grp ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = grp.name, avatarUrl = grp.coverUrl, size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(grp.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${grp.province} • ${formatCompactCount(grp.membersCount)} membros",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    clickHaptic()
                                    onToggleGroup(grp.id)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = if (grp.isJoined) "Membro" else "Entrar",
                                    color = if (grp.isJoined) OnlineGreen else OrangeVibrant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        HorizontalDivider(color = colors.divider, thickness = 0.6.dp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showAdminSheet && isStaff) {
        ModalBottomSheet(onDismissRequest = { showAdminSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = OrangeVibrant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Painel de Moderação", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Área restrita à equipa de moderação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Denúncias em Aberto (${reports.count { it.status == "open" }})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (reports.isEmpty()) {
                    Text("Sem denúncias registadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    reports.forEach { rep ->
                        Surface(
                            color = colors.elevatedSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.7.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${rep.targetType.uppercase()} • ${rep.reason}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    Text(rep.status.uppercase(), color = if (rep.status == "open") CrimsonAngola else OnlineGreen, style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(rep.targetPreview, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Denunciado por @${rep.reporterUsername} • ${rep.createdAtLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (rep.status == "open") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                clickHaptic()
                                                onResolveReport(rep.id, "remove_post")
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonAngola),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Remover", color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clickHaptic()
                                                onResolveReport(rep.id, "dismiss_report")
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(0.7.dp, colors.cardBorder),
                                            modifier = Modifier.weight(1f).height(44.dp)
                                        ) {
                                            Text("Arquivar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
                Text("Registo de Moderação", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (auditLogs.isEmpty()) {
                    Text("Nenhum evento administrativo registado ainda.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    auditLogs.take(5).forEach { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.action, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OrangeVibrant)
                                Text(log.targetSummary, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(log.timestampLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showStatsSheet) {
        ModalBottomSheet(onDismissRequest = { showStatsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Estatísticas da Conta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Visualizações", formatCompactCount(creatorStats.totalViews), Modifier.weight(1f))
                    StatCard("Curtidas Recebidas", formatCompactCount(creatorStats.totalLikes), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Seguidores", formatCompactCount(creatorStats.followersCount), Modifier.weight(1f))
                    StatCard("Comentários", formatCompactCount(creatorStats.totalComments), Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Publicações", creatorStats.totalPosts.toString(), Modifier.weight(1f))
                    StatCard("Vídeos", creatorStats.totalVideos.toString(), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun TextAdsVerificationDialog(
    initialAdsWatched: Int,
    onAdCompleted: () -> Unit,
    onClaimBadge: () -> Unit,
    onDismiss: () -> Unit
) {
    val clickHaptic = rememberClickHaptic()
    val colors = LocalMeuPaisColors.current
    var currentStep by remember { mutableIntStateOf((initialAdsWatched + 1).coerceIn(1, 5)) }
    var countdown by remember(currentStep) { mutableIntStateOf(3) }

    LaunchedEffect(currentStep) {
        countdown = 3
        while (countdown > 0) {
            delay(1000L)
            countdown--
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.7.dp, colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("text_ads_verification_dialog")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, tint = OrangeVibrant, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Anúncio $currentStep de 5",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = OrangeVibrant.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Patrocinado",
                            color = OrangeVibrant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { currentStep / 5f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = OrangeVibrant,
                    trackColor = colors.elevatedSurface
                )

                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "sponsor_ad_step"
                ) { step ->
                    val ad = DAILY_TEXT_ADS[(step - 1).coerceIn(0, DAILY_TEXT_ADS.lastIndex)]
                    Surface(
                        color = colors.elevatedSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.7.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = ad.advertiser,
                                style = MaterialTheme.typography.labelMedium,
                                color = OrangeVibrant,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ad.headline,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ad.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ad.domain,
                                style = MaterialTheme.typography.labelSmall,
                                color = VerifiedBlue
                            )
                        }
                    }
                }

                Text(
                    text = "O selo de verificação fica ativo durante 24 horas após concluíres os 5 anúncios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = {
                            clickHaptic()
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.36f).height(48.dp)
                    ) {
                        Text("Fechar", maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = {
                            clickHaptic()
                            onAdCompleted()
                            if (currentStep < 5) {
                                currentStep++
                            } else {
                                onClaimBadge()
                            }
                        },
                        enabled = countdown == 0,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                        modifier = Modifier
                            .weight(0.64f)
                            .height(48.dp)
                            .testTag("text_ad_next_button")
                    ) {
                        val buttonText = when {
                            countdown > 0 -> "Aguarda ${countdown}s..."
                            currentStep < 5 -> "Próximo anúncio"
                            else -> "Ativar Selo (24h)"
                        }
                        Text(buttonText, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun MenuOptionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalMeuPaisColors.current
    Surface(
        color = colors.elevatedSurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(0.7.dp, colors.cardBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = OrangeVibrant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
