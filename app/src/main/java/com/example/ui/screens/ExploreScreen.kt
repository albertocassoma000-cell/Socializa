package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.CommunityGroup
import com.example.data.model.FeedPost
import com.example.data.model.MarketplaceProduct
import com.example.data.model.SearchResultsBundle
import com.example.data.model.TrendingTopic
import com.example.data.model.UserProfile
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.VerifiedCheckBadge
import com.example.ui.components.formatCompactCount
import com.example.ui.components.formatKwanza
import com.example.ui.components.rememberClickHaptic
import com.example.ui.theme.LocalMeuPaisColors
import com.example.ui.theme.OrangeVibrant

private data class ExploreCategory(val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    searchQuery: String,
    selectedHashtag: String?,
    selectedCategory: String?,
    selectedMarketCategory: String,
    discoveredUsers: List<UserProfile>,
    posts: List<FeedPost>,
    products: List<MarketplaceProduct>,
    provinces: List<String>,
    trendingTopics: List<TrendingTopic> = emptyList(),
    suggestedPeople: List<UserProfile> = emptyList(),
    groups: List<CommunityGroup> = emptyList(),
    unifiedSearchResults: SearchResultsBundle? = null,
    onSearchChange: (String) -> Unit,
    onSelectHashtag: (String?) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectMarketCategory: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onToggleFollowUser: (String) -> Unit,
    onMessageUser: (String) -> Unit,
    onToggleProductFavorite: (String) -> Unit,
    onAddProduct: (title: String, priceKz: Long, category: String, province: String, description: String, imageUri: Uri?) -> Unit,
    onOpenPostComments: (String) -> Unit,
    onContactSeller: (MarketplaceProduct) -> Unit,
    onToggleGroup: (String) -> Unit = {},
    onOpenVideos: () -> Unit = {}
) {
    val colors = LocalMeuPaisColors.current
    val clickHaptic = rememberClickHaptic()
    var exploreTab by remember { mutableIntStateOf(0) } // 0 = Descobrir, 1 = Pessoas, 2 = Loja
    var showNewProductSheet by remember { mutableStateOf(false) }

    val hashtags = remember(trendingTopics) {
        if (trendingTopics.isNotEmpty()) {
            trendingTopics.map { it.tag }
        } else {
            listOf("#Luanda", "#Angola", "#Música", "#Futebol", "#Cultura", "#Juventude")
        }
    }
    val categories = listOf(
        ExploreCategory("Música", Icons.Filled.MusicNote),
        ExploreCategory("Desporto", Icons.Filled.SportsSoccer),
        ExploreCategory("Humor", Icons.Filled.SentimentSatisfiedAlt),
        ExploreCategory("Tecnologia", Icons.Filled.Devices),
        ExploreCategory("Moda", Icons.Filled.Checkroom),
        ExploreCategory("Viagens", Icons.Filled.Flight)
    )
    val marketCategories = listOf("Tudo", "Eletrónicos", "Moda", "Viagens", "Música")
    val publishableCategories = listOf("Eletrónicos", "Moda", "Viagens", "Música")

    val filteredPosts = remember(posts, searchQuery, selectedHashtag, selectedCategory) {
        posts.filter { post ->
            val matchesQuery = searchQuery.isBlank() ||
                post.caption.contains(searchQuery, ignoreCase = true) ||
                post.authorName.contains(searchQuery, ignoreCase = true) ||
                post.authorUsername.contains(searchQuery, ignoreCase = true) ||
                post.locationLabel.contains(searchQuery, ignoreCase = true) ||
                post.hashtags.any { it.contains(searchQuery, ignoreCase = true) }
            val matchesTag = selectedHashtag == null ||
                post.hashtags.any { it.equals(selectedHashtag, ignoreCase = true) }
            val matchesCat = selectedCategory == null ||
                post.category.equals(selectedCategory, ignoreCase = true) ||
                post.caption.contains(selectedCategory, ignoreCase = true) ||
                post.hashtags.any { it.contains(selectedCategory, ignoreCase = true) }
            matchesQuery && matchesTag && matchesCat
        }
    }

    val filteredProducts = remember(products, searchQuery, selectedMarketCategory) {
        products.filter { prod ->
            val matchesQuery = searchQuery.isBlank() ||
                prod.title.contains(searchQuery, ignoreCase = true) ||
                prod.province.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedMarketCategory == "Tudo" || prod.category == selectedMarketCategory
            matchesQuery && matchesCat
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
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (exploreTab) {
                                1 -> "Pessoas"
                                2 -> "Marketplace"
                                else -> "Explorar"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (exploreTab == 2) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    clickHaptic()
                                    showNewProductSheet = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("add_market_product_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Anunciar", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = exploreTab == 0,
                                onClick = {
                                    clickHaptic()
                                    exploreTab = 0
                                },
                                shape = RoundedCornerShape(8.dp),
                                label = { Text("Descobrir", maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangeVibrant,
                                    selectedLabelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = exploreTab == 0,
                                    borderColor = colors.cardBorder
                                ),
                                modifier = Modifier.testTag("explore_mode_discover")
                            )
                        }
                        item {
                            FilterChip(
                                selected = exploreTab == 1,
                                onClick = {
                                    clickHaptic()
                                    exploreTab = 1
                                },
                                shape = RoundedCornerShape(8.dp),
                                leadingIcon = { Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Pessoas", maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangeVibrant,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = exploreTab == 1,
                                    borderColor = colors.cardBorder
                                ),
                                modifier = Modifier.testTag("explore_mode_people")
                            )
                        }
                        item {
                            FilterChip(
                                selected = exploreTab == 2,
                                onClick = {
                                    clickHaptic()
                                    exploreTab = 2
                                },
                                shape = RoundedCornerShape(8.dp),
                                leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Loja", maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = OrangeVibrant,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = exploreTab == 2,
                                    borderColor = colors.cardBorder
                                ),
                                modifier = Modifier.testTag("explore_mode_market")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = {
                            Text(
                                text = "Pesquisar utilizadores, publicações ou lojas...",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Limpar pesquisa")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("explore_search_input")
                    )
                }
                HorizontalDivider(color = colors.divider, thickness = 0.7.dp)
            }
        }

        when (exploreTab) {
            0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Tendências", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(hashtags) { tag ->
                                    val selected = selectedHashtag == tag
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            clickHaptic()
                                            onSelectHashtag(tag)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        label = { Text(tag, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = OrangeVibrant,
                                            selectedLabelColor = Color.White
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = selected,
                                            borderColor = colors.cardBorder
                                        ),
                                        modifier = Modifier.testTag("trend_chip_$tag")
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text("Categorias", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(10.dp))
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val columnCount = if (maxWidth < 340.dp) 2 else 3
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    categories.chunked(columnCount).forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowItems.forEach { cat ->
                                                val selected = selectedCategory == cat.label
                                                Surface(
                                                    color = if (selected) OrangeVibrant else MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(10.dp),
                                                    border = BorderStroke(0.7.dp, if (selected) OrangeVibrant else colors.cardBorder),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(78.dp)
                                                        .clickable {
                                                            clickHaptic()
                                                            onSelectCategory(cat.label)
                                                        }
                                                        .testTag("category_${cat.label}")
                                                ) {
                                                    Column(
                                                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = cat.icon,
                                                            contentDescription = cat.label,
                                                            tint = if (selected) Color.White else OrangeVibrant,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = cat.label,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                            repeat(columnCount - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    if (searchQuery.isNotBlank() && unifiedSearchResults != null) {
                        if (unifiedSearchResults.videos.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Vídeos relacionados (${unifiedSearchResults.videos.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            items(unifiedSearchResults.videos.take(3), key = { "search_vid_${it.id}" }) { reel ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            clickHaptic()
                                            onOpenVideos()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(name = reel.authorName, avatarUrl = reel.authorAvatarUrl, size = 40.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("@${reel.authorUsername} • ${reel.musicTitle}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(reel.caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                        if (unifiedSearchResults.groups.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Grupos e comunidades (${unifiedSearchResults.groups.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            items(unifiedSearchResults.groups.take(4), key = { "search_grp_${it.id}" }) { grp ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(grp.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${grp.province} • ${grp.membersCount} membros", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            clickHaptic()
                                            onToggleGroup(grp.id)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.heightIn(min = 44.dp)
                                    ) {
                                        Text(if (grp.isJoined) "Membro" else "Aderir")
                                    }
                                }
                            }
                        }
                        if (unifiedSearchResults.pages.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Páginas e Lojas (${unifiedSearchResults.pages.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            items(unifiedSearchResults.pages.take(3), key = { "search_page_${it.id}" }) { page ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            clickHaptic()
                                            onOpenUserProfile(page.id)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(name = page.fullName, avatarUrl = page.avatarUri, size = 38.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(page.fullName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(page.bio.ifBlank { page.province }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Publicações encontradas (${filteredPosts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    if (filteredPosts.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Search,
                                title = "Nenhuma publicação encontrada",
                                description = "Experimenta outra pesquisa ou publica novos conteúdos para aparecerem em Explorar.",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(filteredPosts, key = { it.id }) { post ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clickHaptic()
                                        onOpenPostComments(post.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (post.mediaUrls.isNotEmpty()) {
                                    AsyncImage(
                                        model = post.mediaUrls.first(),
                                        contentDescription = post.caption,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(68.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    UserAvatar(name = post.authorName, avatarUrl = post.authorAvatarUrl, size = 52.dp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = post.authorName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = post.caption,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (post.hashtags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = post.hashtags.joinToString(" "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = OrangeVibrant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = colors.divider,
                                thickness = 0.7.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            1 -> {
                val peopleToShow = remember(discoveredUsers, suggestedPeople, searchQuery) {
                    if (searchQuery.isBlank() && suggestedPeople.isNotEmpty()) {
                        val sugIds = suggestedPeople.mapTo(HashSet()) { it.id }
                        suggestedPeople + discoveredUsers.filter { it.id !in sugIds }
                    } else {
                        discoveredUsers
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (searchQuery.isBlank() && suggestedPeople.isNotEmpty()) {
                        item {
                            Text(
                                text = "Sugestões para ti",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = OrangeVibrant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (peopleToShow.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Group,
                                title = "Nenhum utilizador encontrado",
                                description = "Quando outras pessoas entrarem no Socializa, os seus perfis aparecerão aqui.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        items(peopleToShow, key = { it.id }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clickHaptic()
                                        onOpenUserProfile(user.id)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = user.fullName, avatarUrl = user.avatarUri, size = 46.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = user.fullName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (user.hasActiveVerifiedBadge) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            VerifiedCheckBadge(size = 14.dp)
                                        }
                                    }
                                    Text(
                                        text = "@${user.username} • ${user.province}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${formatCompactCount(user.followersCount)} seguidores",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OrangeVibrant,
                                        maxLines = 1
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedButton(
                                        onClick = {
                                            clickHaptic()
                                            onMessageUser(user.id)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.7.dp, colors.cardBorder),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Chat", maxLines = 1, softWrap = false)
                                    }
                                    Button(
                                        onClick = {
                                            clickHaptic()
                                            onToggleFollowUser(user.id)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (user.isFollowedByMe) colors.elevatedSurface else OrangeVibrant
                                        ),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = if (user.isFollowedByMe) "A seguir" else "Seguir",
                                            color = if (user.isFollowedByMe) MaterialTheme.colorScheme.onSurface else Color.White,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = colors.divider,
                                thickness = 0.7.dp,
                                modifier = Modifier.padding(start = 74.dp, end = 16.dp)
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(marketCategories) { cat ->
                                FilterChip(
                                    selected = selectedMarketCategory == cat,
                                    onClick = {
                                        clickHaptic()
                                        onSelectMarketCategory(cat)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    label = { Text(cat, maxLines = 1, softWrap = false) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = OrangeVibrant,
                                        selectedLabelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selectedMarketCategory == cat,
                                        borderColor = colors.cardBorder
                                    )
                                )
                            }
                        }
                    }

                    if (filteredProducts.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Filled.Storefront,
                                title = "Marketplace sem anúncios ainda",
                                description = "Publica o primeiro produto ou serviço em Kwanza para a comunidade.",
                                actionLabel = "Anunciar Produto",
                                onAction = { showNewProductSheet = true }
                            )
                        }
                    } else {
                        items(filteredProducts.chunked(2)) { pair ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { product ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(0.7.dp, colors.cardBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                clickHaptic()
                                                onContactSeller(product)
                                            }
                                            .testTag("market_product_${product.id}")
                                    ) {
                                        Column {
                                            Box {
                                                if (!product.imageUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = product.imageUrl,
                                                        contentDescription = product.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxWidth().height(132.dp)
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(132.dp)
                                                            .background(colors.elevatedSurface),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(Icons.Filled.Storefront, contentDescription = null, tint = OrangeVibrant, modifier = Modifier.size(34.dp))
                                                    }
                                                }
                                                IconButton(
                                                    onClick = {
                                                        clickHaptic()
                                                        onToggleProductFavorite(product.id)
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(48.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.5f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (product.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                                            contentDescription = "Favorito",
                                                            tint = if (product.isFavorite) OrangeVibrant else Color.White,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = product.title,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = formatKwanza(product.priceKz),
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = OrangeVibrant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${product.province} • ${product.sellerName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewProductSheet) {
        var title by remember { mutableStateOf("") }
        var priceRaw by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var category by remember { mutableStateOf(publishableCategories.first()) }
        var province by remember { mutableStateOf(provinces.firstOrNull() ?: "Luanda") }
        var showProvinceMenu by remember { mutableStateOf(false) }
        var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) pickedImageUri = uri
        }
        val canSubmitProduct = title.isNotBlank() && (priceRaw.toLongOrNull() ?: 0L) > 0L

        ModalBottomSheet(onDismissRequest = { showNewProductSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Anunciar no Marketplace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = {
                        clickHaptic()
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.7.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (pickedImageUri != null) "Fotografia selecionada" else "Adicionar fotografia do produto")
                }

                Text("Categoria", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(publishableCategories) { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = {
                                clickHaptic()
                                category = cat
                            },
                            shape = RoundedCornerShape(8.dp),
                            label = { Text(cat, maxLines = 1, softWrap = false) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OrangeVibrant,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = category == cat,
                                borderColor = colors.cardBorder
                            )
                        )
                    }
                }

                Box {
                    Surface(
                        color = colors.elevatedSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.7.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                clickHaptic()
                                showProvinceMenu = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = OrangeVibrant, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Província", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(province, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DropdownMenu(expanded = showProvinceMenu, onDismissRequest = { showProvinceMenu = false }) {
                        provinces.forEach { prov ->
                            DropdownMenuItem(
                                text = { Text(prov) },
                                onClick = {
                                    clickHaptic()
                                    province = prov
                                    showProvinceMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título do produto") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = priceRaw,
                    onValueChange = { priceRaw = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Preço em Kwanza (AOA)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Descrição") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        clickHaptic()
                        val price = priceRaw.toLongOrNull() ?: 0L
                        if (title.isNotBlank() && price > 0L) {
                            onAddProduct(title, price, category, province, desc, pickedImageUri)
                            showNewProductSheet = false
                        }
                    },
                    enabled = canSubmitProduct,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangeVibrant)
                ) {
                    Text("Publicar Anúncio", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
