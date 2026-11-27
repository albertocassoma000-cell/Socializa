package com.example.data.model

enum class ThemeMode(val label: String) {
    DARK("Modo Escuro"),
    LIGHT("Modo Claro"),
    SYSTEM("Sistema")
}

enum class FeedMode(val label: String) {
    FOR_YOU("Para ti"),
    FOLLOWING("Seguindo"),
    TRENDING("Em alta")
}

enum class AuthModeTab(val label: String) {
    SIGN_IN("Entrar"),
    REGISTER("Criar conta"),
    FORGOT_PASSWORD("Recuperar")
}

enum class OtpAuthStep {
    NONE,
    REGISTER_CODE,
    RESET_CODE
}

enum class PostType(val label: String) {
    PHOTO("Foto"),
    VIDEO("Vídeo"),
    CAROUSEL("Carrossel"),
    TEXT("Texto"),
    STORY("Story")
}

enum class PostVisibility(val label: String) {
    PUBLIC("Público"),
    FOLLOWERS("Seguidores"),
    PRIVATE("Privado")
}

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, CALL_EVENT
}

enum class DeliveryStatus(val label: String) {
    SENT("Enviada"),
    DELIVERED("Entregue"),
    READ("Lida")
}

enum class NotificationEventType(val label: String) {
    FOLLOW("Novo seguidor"),
    LIKE("Curtida"),
    COMMENT("Comentário"),
    COMMENT_REPLY("Resposta"),
    MESSAGE("Mensagem"),
    MENTION("Menção"),
    INVITE("Convite"),
    LIVE_STREAM("Ao vivo")
}

data class UserProfile(
    val id: String,
    val username: String,
    val fullName: String,
    val bio: String = "",
    val avatarUri: String? = null,
    val country: String = "Angola",
    val province: String = "Luanda",
    val website: String = "",
    val isPrivate: Boolean = false,
    val isVerified: Boolean = false,
    val verifiedUntilMs: Long = 0L,
    val role: String = "user", // user | moderator | admin
    val isSuspended: Boolean = false,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val postsCount: Int = 0,
    val isOnline: Boolean = true,
    val isFollowedByMe: Boolean = false,
    val isBlockedByMe: Boolean = false
) {
    fun isBadgeActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        isVerified || verifiedUntilMs > nowMs

    val hasActiveVerifiedBadge: Boolean
        get() = isBadgeActive()
}

data class StoryItem(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val avatarUrl: String? = null,
    val mediaUrl: String,
    val mediaType: String = "image",
    val caption: String = "",
    val timeAgo: String,
    val expiresAtIso: String = "",
    val viewsCount: Int = 0,
    val isViewed: Boolean = false,
    val isOwn: Boolean = false
)

data class PostComment(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorAvatarUrl: String? = null,
    val parentId: String? = null,
    val body: String,
    val timeAgo: String,
    val likesCount: Int = 0,
    val isLiked: Boolean = false
)

data class FeedPost(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorVerified: Boolean = false,
    val authorAvatarUrl: String? = null,
    val postType: PostType,
    val caption: String,
    val hashtags: List<String> = emptyList(),
    val category: String? = null,
    val locationLabel: String = "",
    val visibility: PostVisibility = PostVisibility.PUBLIC,
    val mediaUrls: List<String> = emptyList(),
    val musicTitle: String? = null,
    val musicUrl: String? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val viewsCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val isAuthorFollowed: Boolean = false,
    val createdAtIso: String = "",
    val createdAtLabel: String = "Agora",
    val editedAtLabel: String? = null,
    val comments: List<PostComment> = emptyList()
)

data class VideoReel(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorAvatarUrl: String? = null,
    val isVerified: Boolean = false,
    val isFollowing: Boolean = false,
    val streamUrl: String,
    val thumbnailUrl: String? = null,
    val caption: String,
    val hashtags: List<String> = emptyList(),
    val musicTitle: String = "Som original",
    val musicUrl: String? = null,
    val effectLabel: String = "Original",
    val autoSubtitle: String = "",
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val viewsCount: Int = 0,
    val isLiked: Boolean = false,
    val isSaved: Boolean = false,
    val comments: List<PostComment> = emptyList()
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val type: MessageType = MessageType.TEXT,
    val timeLabel: String,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    val isMine: Boolean,
    val attachmentUrl: String? = null,
    val attachmentName: String? = null
)

data class ConversationItem(
    val id: String,
    val title: String,
    val otherUserId: String? = null,
    val isGroup: Boolean = false,
    val avatarUrl: String? = null,
    val lastMessage: String = "",
    val timeLabel: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val memberCount: Int = 2,
    val messages: List<ChatMessage> = emptyList()
)

data class MarketplaceProduct(
    val id: String,
    val sellerId: String,
    val sellerName: String,
    val title: String,
    val priceKz: Long,
    val category: String,
    val province: String,
    val imageUrl: String? = null,
    val description: String,
    val isFavorite: Boolean = false
)

data class CommunityGroup(
    val id: String,
    val ownerId: String,
    val name: String,
    val province: String,
    val description: String,
    val membersCount: Int,
    val isJoined: Boolean,
    val coverUrl: String? = null
)

data class AppNotification(
    val id: String,
    val type: NotificationEventType,
    val title: String,
    val body: String,
    val referenceId: String? = null,
    val actorId: String? = null,
    val timeLabel: String,
    val isRead: Boolean = false
)

data class ModerationReport(
    val id: String,
    val reporterId: String,
    val reporterUsername: String,
    val targetType: String,
    val targetId: String,
    val targetPreview: String,
    val reason: String,
    val status: String, // open | reviewing | resolved | dismissed
    val createdAtLabel: String
)

data class AuditLogEntry(
    val id: String,
    val actorUsername: String,
    val action: String,
    val targetSummary: String,
    val timestampLabel: String
)

data class CreatorStats(
    val totalPosts: Int = 0,
    val totalVideos: Int = 0,
    val totalViews: Int = 0,
    val totalLikes: Int = 0,
    val totalComments: Int = 0,
    val followersCount: Int = 0,
    val followingCount: Int = 0
)

data class BackendStatus(
    val supabaseConfigured: Boolean,
    val supabaseUrlHost: String,
    val firebaseConfigured: Boolean,
    val fcmTokenPreview: String?,
    val liveKitConfigured: Boolean,
    val streamProviderConfigured: Boolean,
    val supabaseAuthDiagnostic: String? = null
)

data class TrendingTopic(
    val tag: String,
    val mentionsCount: Int = 0,
    val score: Double = 0.0
)

data class VideoTelemetry(
    val watchSeconds: Int = 0,
    val completed: Boolean = false,
    val replayed: Boolean = false
)

data class SearchResultsBundle(
    val people: List<UserProfile> = emptyList(),
    val posts: List<FeedPost> = emptyList(),
    val videos: List<VideoReel> = emptyList(),
    val groups: List<CommunityGroup> = emptyList(),
    val pages: List<UserProfile> = emptyList(),
    val hashtags: List<TrendingTopic> = emptyList()
)

