package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class EnsureFirebaseProfileRequest(
    @Json(name = "p_full_name") val fullName: String?,
    @Json(name = "p_avatar_url") val avatarUrl: String?,
    @Json(name = "p_province") val province: String = "Luanda",
    @Json(name = "p_country") val country: String = "Angola"
)

data class FirebaseSessionExchangeRequest(
    @Json(name = "firebase_token") val firebaseToken: String
)

data class FirebaseSessionExchangeDto(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresIn: Long = 3300,
    @Json(name = "token_type") val tokenType: String = "bearer",
    @Json(name = "user_id") val userId: String? = null
)

data class SupabaseProfileDto(
    val id: String,
    val username: String,
    @Json(name = "full_name") val fullName: String = "",
    val bio: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val country: String = "Angola",
    val province: String = "Luanda",
    val website: String? = null,
    @Json(name = "is_private") val isPrivate: Boolean = false,
    @Json(name = "is_verified") val isVerified: Boolean = false,
    @Json(name = "verified_until") val verifiedUntil: String? = null,
    val role: String = "user",
    @Json(name = "is_suspended") val isSuspended: Boolean = false,
    @Json(name = "followers_count") val followersCount: Int = 0,
    @Json(name = "following_count") val followingCount: Int = 0,
    @Json(name = "posts_count") val postsCount: Int = 0,
    @Json(name = "is_online") val isOnline: Boolean = false
)

data class ClaimDailyVerifiedBadgeRequest(
    @Json(name = "p_ads_watched") val adsWatched: Int = 5
)

data class SupabaseProfileSummaryDto(
    val id: String = "",
    val username: String = "utilizador",
    @Json(name = "full_name") val fullName: String = "Utilizador",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean = false,
    @Json(name = "verified_until") val verifiedUntil: String? = null,
    @Json(name = "is_online") val isOnline: Boolean = false
)

data class SupabaseMediaDto(
    val id: String = "",
    @Json(name = "post_id") val postId: String? = null,
    @Json(name = "media_type") val mediaType: String = "image",
    @Json(name = "public_url") val publicUrl: String = "",
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "sort_order") val sortOrder: Int = 0
)

data class SupabasePostDto(
    val id: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "post_type") val postType: String = "photo",
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    val category: String? = null,
    @Json(name = "location_label") val locationLabel: String? = null,
    val visibility: String = "public",
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "comments_count") val commentsCount: Int = 0,
    @Json(name = "shares_count") val sharesCount: Int = 0,
    @Json(name = "views_count") val viewsCount: Int = 0,
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "edited_at") val editedAt: String? = null,
    val author: SupabaseProfileSummaryDto? = null,
    val media: List<SupabaseMediaDto> = emptyList()
)

data class SupabasePostInsertDto(
    @Json(name = "author_id") val authorId: String,
    @Json(name = "post_type") val postType: String,
    val caption: String,
    val hashtags: List<String>,
    val category: String? = null,
    @Json(name = "location_label") val locationLabel: String?,
    val visibility: String
)

data class SupabaseMediaInsertDto(
    @Json(name = "post_id") val postId: String,
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "media_type") val mediaType: String,
    @Json(name = "storage_bucket") val storageBucket: String = "media",
    @Json(name = "storage_path") val storagePath: String,
    @Json(name = "public_url") val publicUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    @Json(name = "sort_order") val sortOrder: Int = 0
)

data class SupabaseLikeRecordDto(
    val id: String? = null,
    @Json(name = "user_id") val userId: String,
    @Json(name = "post_id") val postId: String
)

data class SupabaseSavedPostRecordDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "post_id") val postId: String
)

data class SupabasePostViewDto(
    @Json(name = "post_id") val postId: String,
    @Json(name = "viewer_id") val viewerId: String
)

data class SupabaseCommentDto(
    val id: String,
    @Json(name = "post_id") val postId: String? = null,
    @Json(name = "video_id") val videoId: String? = null,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "parent_id") val parentId: String? = null,
    val body: String,
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "created_at") val createdAt: String = "",
    val author: SupabaseProfileSummaryDto? = null
)

data class SupabaseCommentInsertDto(
    @Json(name = "post_id") val postId: String? = null,
    @Json(name = "video_id") val videoId: String? = null,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "parent_id") val parentId: String? = null,
    val body: String
)

data class SupabaseCommentLikeDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "comment_id") val commentId: String
)

data class SupabaseFollowDto(
    @Json(name = "follower_id") val followerId: String,
    @Json(name = "following_id") val followingId: String,
    val status: String = "accepted"
)

data class SupabaseBlockDto(
    @Json(name = "blocker_id") val blockerId: String,
    @Json(name = "blocked_id") val blockedId: String
)

data class SupabaseStoryDto(
    val id: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "media_url") val mediaUrl: String,
    @Json(name = "media_type") val mediaType: String = "image",
    val caption: String? = null,
    @Json(name = "expires_at") val expiresAt: String = "",
    @Json(name = "created_at") val createdAt: String = "",
    val author: SupabaseProfileSummaryDto? = null
)

data class SupabaseStoryInsertDto(
    @Json(name = "author_id") val authorId: String,
    @Json(name = "media_url") val mediaUrl: String,
    @Json(name = "media_type") val mediaType: String = "image",
    val caption: String? = null
)

data class SupabaseStoryViewDto(
    @Json(name = "story_id") val storyId: String,
    @Json(name = "viewer_id") val viewerId: String
)

data class SupabaseVideoDto(
    val id: String,
    @Json(name = "author_id") val authorId: String,
    val provider: String = "supabase_storage",
    @Json(name = "stream_asset_id") val streamAssetId: String = "",
    @Json(name = "playback_url") val playbackUrl: String,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    val caption: String = "",
    val hashtags: List<String> = emptyList(),
    @Json(name = "music_title") val musicTitle: String = "Som original",
    @Json(name = "music_url") val musicUrl: String? = null,
    @Json(name = "effects_tags") val effectsTags: List<String> = emptyList(),
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "comments_count") val commentsCount: Int = 0,
    @Json(name = "shares_count") val sharesCount: Int = 0,
    @Json(name = "views_count") val viewsCount: Int = 0,
    @Json(name = "created_at") val createdAt: String = "",
    val author: SupabaseProfileSummaryDto? = null
)

data class SupabaseVideoInsertDto(
    @Json(name = "author_id") val authorId: String,
    val provider: String = "supabase_storage",
    @Json(name = "stream_asset_id") val streamAssetId: String,
    @Json(name = "playback_url") val playbackUrl: String,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    val caption: String,
    val hashtags: List<String>,
    @Json(name = "music_title") val musicTitle: String = "Som original",
    @Json(name = "music_url") val musicUrl: String? = null,
    @Json(name = "duet_source_video_id") val duetSourceVideoId: String? = null
)

data class SupabaseVideoLikeDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "video_id") val videoId: String
)

data class SupabaseVideoViewDto(
    @Json(name = "video_id") val videoId: String,
    @Json(name = "viewer_id") val viewerId: String,
    @Json(name = "watch_seconds") val watchSeconds: Int = 5,
    val completed: Boolean = false,
    val replayed: Boolean = false
)

data class EmailOtpRequestDto(
    val action: String,
    val email: String,
    @Json(name = "full_name") val fullName: String? = null,
    val password: String? = null,
    @Json(name = "new_password") val newPassword: String? = null,
    val otp: String? = null
)

data class EmailOtpResponseDto(
    val ok: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val code: String? = null,
    val uid: String? = null,
    val email: String? = null,
    @Json(name = "expires_in_seconds") val expiresInSeconds: Int = 300,
    @Json(name = "retry_after_seconds") val retryAfterSeconds: Int? = null
)

data class RankedFeedRequestDto(
    @Json(name = "p_mode") val mode: String = "for_you",
    @Json(name = "p_limit") val limit: Int = 15,
    @Json(name = "p_offset") val offset: Int = 0,
    @Json(name = "p_province") val province: String? = null,
    @Json(name = "p_category") val category: String? = null
)

data class RankedPostScoreDto(
    @Json(name = "post_id") val postId: String,
    val score: Double = 0.0
)

data class RecommendedVideosRequestDto(
    @Json(name = "p_limit") val limit: Int = 15,
    @Json(name = "p_offset") val offset: Int = 0
)

data class RecommendedVideoScoreDto(
    @Json(name = "video_id") val videoId: String,
    val score: Double = 0.0
)

data class TrendingTopicsRequestDto(
    @Json(name = "p_limit") val limit: Int = 10
)

data class TrendingTopicDto(
    val tag: String,
    @Json(name = "mentions_count") val mentionsCount: Int = 0,
    @Json(name = "trend_score") val trendScore: Double = 0.0
)

data class InterestSignalUpsertDto(
    @Json(name = "p_topic") val topic: String,
    @Json(name = "p_delta") val delta: Double = 1.5
)

data class DirectConversationRpcRequest(
    @Json(name = "p_target_user_id") val targetUserId: String
)

data class ConversationIdRpcRequest(
    @Json(name = "p_conversation_id") val conversationId: String
)

data class PostShareRpcRequest(
    @Json(name = "p_post_id") val postId: String
)

data class VideoShareRpcRequest(
    @Json(name = "p_video_id") val videoId: String
)

data class SupabaseConversationMemberNestedDto(
    @Json(name = "user_id") val userId: String,
    @Json(name = "last_read_at") val lastReadAt: String = "",
    val profile: SupabaseProfileSummaryDto? = null
)

data class SupabaseConversationDto(
    val id: String,
    @Json(name = "is_group") val isGroup: Boolean = false,
    val title: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "last_message_preview") val lastMessagePreview: String? = null,
    @Json(name = "last_message_at") val lastMessageAt: String = "",
    val members: List<SupabaseConversationMemberNestedDto> = emptyList()
)

data class SupabaseMessageDto(
    val id: String,
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "message_type") val messageType: String = "text",
    val body: String = "",
    @Json(name = "attachment_url") val attachmentUrl: String? = null,
    @Json(name = "attachment_name") val attachmentName: String? = null,
    @Json(name = "delivery_status") val deliveryStatus: String = "sent",
    @Json(name = "created_at") val createdAt: String = "",
    val sender: SupabaseProfileSummaryDto? = null
)

data class SupabaseMessageInsertDto(
    @Json(name = "conversation_id") val conversationId: String,
    @Json(name = "sender_id") val senderId: String,
    @Json(name = "message_type") val messageType: String,
    val body: String,
    @Json(name = "attachment_url") val attachmentUrl: String? = null,
    @Json(name = "attachment_name") val attachmentName: String? = null
)

data class SupabaseNotificationDto(
    val id: String,
    @Json(name = "recipient_id") val recipientId: String,
    @Json(name = "actor_id") val actorId: String? = null,
    @Json(name = "event_type") val eventType: String,
    val title: String,
    val body: String,
    @Json(name = "reference_id") val referenceId: String? = null,
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "created_at") val createdAt: String = ""
)

data class SupabaseProductDto(
    val id: String,
    @Json(name = "seller_id") val sellerId: String,
    val title: String,
    val description: String = "",
    val category: String = "Eletrónicos",
    @Json(name = "price_kz") val priceKz: Double = 0.0,
    val province: String = "Luanda",
    @Json(name = "image_url") val imageUrl: String? = null,
    val status: String = "active",
    val seller: SupabaseProfileSummaryDto? = null
)

data class SupabaseProductInsertDto(
    @Json(name = "seller_id") val sellerId: String,
    val title: String,
    val description: String,
    val category: String,
    @Json(name = "price_kz") val priceKz: Long,
    val province: String,
    @Json(name = "image_url") val imageUrl: String? = null
)

data class SupabaseGroupDto(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    val description: String = "",
    @Json(name = "cover_url") val coverUrl: String? = null,
    val province: String? = null,
    @Json(name = "members_count") val membersCount: Int = 1
)

data class SupabaseGroupInsertDto(
    @Json(name = "owner_id") val ownerId: String,
    val name: String,
    val description: String,
    val province: String
)

data class SupabaseGroupMemberDto(
    @Json(name = "group_id") val groupId: String,
    @Json(name = "user_id") val userId: String,
    val role: String = "member"
)

data class SupabaseDeviceTokenUpsertDto(
    @Json(name = "user_id") val userId: String,
    val token: String,
    val platform: String = "android",
    @Json(name = "device_id") val deviceId: String
)

data class SupabasePushDispatchDto(
    @Json(name = "recipient_id") val recipientId: String,
    @Json(name = "event_type") val eventType: String,
    val title: String,
    val body: String,
    @Json(name = "reference_id") val referenceId: String? = null,
    @Json(name = "dispatch_only") val dispatchOnly: Boolean = true
)

data class SupabaseReportDto(
    val id: String,
    @Json(name = "reporter_id") val reporterId: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val reason: String,
    val status: String = "open",
    @Json(name = "created_at") val createdAt: String = "",
    val reporter: SupabaseProfileSummaryDto? = null
)

data class SupabaseReportInsertDto(
    @Json(name = "reporter_id") val reporterId: String,
    @Json(name = "target_type") val targetType: String,
    @Json(name = "target_id") val targetId: String,
    val reason: String,
    val details: String? = null
)

data class SupabaseAuditLogDto(
    val id: String,
    @Json(name = "actor_id") val actorId: String? = null,
    val action: String,
    @Json(name = "entity_type") val entityType: String,
    @Json(name = "created_at") val createdAt: String = "",
    val actor: SupabaseProfileSummaryDto? = null
)

data class SupabaseAdminModerationDto(
    @Json(name = "report_id") val reportId: String? = null,
    val action: String,
    @Json(name = "target_id") val targetId: String,
    val reason: String? = null
)

data class SupabaseErrorLogInsertDto(
    @Json(name = "user_id") val userId: String?,
    val platform: String = "android",
    val screen: String,
    @Json(name = "error_message") val errorMessage: String,
    @Json(name = "stack_trace") val stackTrace: String? = null
)

interface SupabaseApiService {
    @POST("rest/v1/rpc/ensure_firebase_profile")
    suspend fun ensureFirebaseProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: EnsureFirebaseProfileRequest
    ): Response<SupabaseProfileDto>

    @POST("rest/v1/rpc/claim_daily_verified_badge")
    suspend fun claimDailyVerifiedBadge(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: ClaimDailyVerifiedBadgeRequest = ClaimDailyVerifiedBadgeRequest()
    ): Response<SupabaseProfileDto>

    @GET("rest/v1/profiles")
    suspend fun getProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseProfileDto>>

    @GET("rest/v1/profiles")
    suspend fun searchProfiles(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("or") orFilter: String? = null,
        @Query("province") provinceFilter: String? = null,
        @Query("is_suspended") suspendedFilter: String = "eq.false",
        @Query("order") order: String = "followers_count.desc",
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseProfileDto>>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("id") idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    // Posts (paginação, edição, eliminação, filtros)
    @GET("rest/v1/posts")
    suspend fun getPosts(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("select") select: String = "*,author:profiles!author_id(id,username,full_name,avatar_url,is_verified,is_online),media(id,post_id,media_type,public_url,thumbnail_url,sort_order)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 15,
        @Query("offset") offset: Int = 0,
        @Query("author_id") authorFilter: String? = null,
        @Query("location_label") locationFilter: String? = null,
        @Query("category") categoryFilter: String? = null
    ): Response<List<SupabasePostDto>>

    @POST("rest/v1/posts")
    suspend fun createPost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: SupabasePostInsertDto
    ): Response<List<SupabasePostDto>>

    @PATCH("rest/v1/posts")
    suspend fun updatePost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("id") idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    @DELETE("rest/v1/posts")
    suspend fun deletePost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("id") idFilter: String
    ): Response<Unit>

    @POST("rest/v1/media")
    suspend fun createMediaRecord(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseMediaInsertDto
    ): Response<Unit>

    // Likes, Saved Posts & Views
    @GET("rest/v1/likes")
    suspend fun getUserLikes(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String,
        @Query("select") select: String = "user_id,post_id"
    ): Response<List<SupabaseLikeRecordDto>>

    @POST("rest/v1/likes")
    suspend fun likePost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseLikeRecordDto
    ): Response<Unit>

    @DELETE("rest/v1/likes")
    suspend fun unlikePost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String,
        @Query("post_id") postFilter: String
    ): Response<Unit>

    @GET("rest/v1/saved_posts")
    suspend fun getUserSavedPosts(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String,
        @Query("select") select: String = "user_id,post_id"
    ): Response<List<SupabaseSavedPostRecordDto>>

    @POST("rest/v1/saved_posts")
    suspend fun savePost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseSavedPostRecordDto
    ): Response<Unit>

    @DELETE("rest/v1/saved_posts")
    suspend fun unsavePost(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String,
        @Query("post_id") postFilter: String
    ): Response<Unit>

    @POST("rest/v1/post_views?on_conflict=post_id,viewer_id")
    suspend fun recordPostView(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "resolution=ignore-duplicates",
        @Body body: SupabasePostViewDto
    ): Response<Unit>

    @POST("rest/v1/rpc/increment_post_share")
    suspend fun incrementPostShare(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: PostShareRpcRequest
    ): Response<Unit>

    // Comentários, Respostas e Curtidas de Comentários
    @GET("rest/v1/comments")
    suspend fun getComments(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("post_id") postFilter: String? = null,
        @Query("video_id") videoFilter: String? = null,
        @Query("select") select: String = "*,author:profiles!author_id(id,username,full_name,avatar_url,is_verified,is_online)",
        @Query("order") order: String = "created_at.asc",
        @Query("limit") limit: Int = 100
    ): Response<List<SupabaseCommentDto>>

    @POST("rest/v1/comments")
    suspend fun createComment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: SupabaseCommentInsertDto
    ): Response<List<SupabaseCommentDto>>

    @DELETE("rest/v1/comments")
    suspend fun deleteComment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("id") idFilter: String
    ): Response<Unit>

    @GET("rest/v1/comment_likes")
    suspend fun getUserCommentLikes(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String,
        @Query("select") select: String = "user_id,comment_id"
    ): Response<List<SupabaseCommentLikeDto>>

    @POST("rest/v1/comment_likes")
    suspend fun likeComment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseCommentLikeDto
    ): Response<Unit>

    @DELETE("rest/v1/comment_likes")
    suspend fun unlikeComment(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String,
        @Query("comment_id") commentFilter: String
    ): Response<Unit>

    // Seguidores e Bloqueios
    @GET("rest/v1/follows")
    suspend fun getFollowing(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("follower_id") followerFilter: String,
        @Query("select") select: String = "follower_id,following_id,status"
    ): Response<List<SupabaseFollowDto>>

    @POST("rest/v1/follows")
    suspend fun followUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseFollowDto
    ): Response<Unit>

    @DELETE("rest/v1/follows")
    suspend fun unfollowUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("follower_id") followerFilter: String,
        @Query("following_id") followingFilter: String
    ): Response<Unit>

    @GET("rest/v1/blocks")
    suspend fun getBlocks(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("blocker_id") blockerFilter: String,
        @Query("select") select: String = "blocker_id,blocked_id"
    ): Response<List<SupabaseBlockDto>>

    @POST("rest/v1/blocks")
    suspend fun blockUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseBlockDto
    ): Response<Unit>

    @DELETE("rest/v1/blocks")
    suspend fun unblockUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("blocker_id") blockerFilter: String,
        @Query("blocked_id") blockedFilter: String
    ): Response<Unit>

    // Stories (24h)
    @GET("rest/v1/stories")
    suspend fun getActiveStories(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("select") select: String = "*,author:profiles!author_id(id,username,full_name,avatar_url,is_verified,is_online)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 30
    ): Response<List<SupabaseStoryDto>>

    @POST("rest/v1/stories")
    suspend fun createStory(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: SupabaseStoryInsertDto
    ): Response<List<SupabaseStoryDto>>

    @GET("rest/v1/story_views")
    suspend fun getStoryViews(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("viewer_id") viewerFilter: String,
        @Query("select") select: String = "story_id,viewer_id"
    ): Response<List<SupabaseStoryViewDto>>

    @POST("rest/v1/story_views?on_conflict=story_id,viewer_id")
    suspend fun recordStoryView(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "resolution=ignore-duplicates",
        @Body body: SupabaseStoryViewDto
    ): Response<Unit>

    // Vídeos (Reels)
    @GET("rest/v1/videos")
    suspend fun getVideos(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("select") select: String = "*,author:profiles!author_id(id,username,full_name,avatar_url,is_verified,is_online)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 15,
        @Query("offset") offset: Int = 0
    ): Response<List<SupabaseVideoDto>>

    @POST("rest/v1/videos")
    suspend fun createVideo(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: SupabaseVideoInsertDto
    ): Response<List<SupabaseVideoDto>>

    @GET("rest/v1/video_likes")
    suspend fun getUserVideoLikes(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String,
        @Query("select") select: String = "user_id,video_id"
    ): Response<List<SupabaseVideoLikeDto>>

    @POST("rest/v1/video_likes")
    suspend fun likeVideo(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseVideoLikeDto
    ): Response<Unit>

    @DELETE("rest/v1/video_likes")
    suspend fun unlikeVideo(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String,
        @Query("video_id") videoFilter: String
    ): Response<Unit>

    @POST("rest/v1/video_views")
    suspend fun recordVideoView(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseVideoViewDto
    ): Response<Unit>

    @POST("rest/v1/rpc/increment_video_share")
    suspend fun incrementVideoShare(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: VideoShareRpcRequest
    ): Response<Unit>

    // Conversas e Mensagens Reais
    @POST("rest/v1/rpc/get_or_create_direct_conversation")
    suspend fun getOrCreateDirectConversation(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: DirectConversationRpcRequest
    ): Response<String>

    @POST("rest/v1/rpc/mark_conversation_read")
    suspend fun markConversationRead(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: ConversationIdRpcRequest
    ): Response<Unit>

    @GET("rest/v1/conversations")
    suspend fun getConversations(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("select") select: String = "id,is_group,title,avatar_url,last_message_preview,last_message_at,members:conversation_members(user_id,last_read_at,profile:profiles!user_id(id,username,full_name,avatar_url,is_verified,is_online))",
        @Query("order") order: String = "last_message_at.desc",
        @Query("limit") limit: Int = 30
    ): Response<List<SupabaseConversationDto>>

    @GET("rest/v1/messages")
    suspend fun getMessages(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("conversation_id") convFilter: String,
        @Query("select") select: String = "*,sender:profiles!sender_id(id,username,full_name,avatar_url,is_verified,is_online)",
        @Query("order") order: String = "created_at.asc",
        @Query("limit") limit: Int = 50
    ): Response<List<SupabaseMessageDto>>

    @POST("rest/v1/messages")
    suspend fun sendMessage(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: SupabaseMessageInsertDto
    ): Response<List<SupabaseMessageDto>>

    // Notificações & Push FCM
    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("recipient_id") recipientFilter: String,
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 40
    ): Response<List<SupabaseNotificationDto>>

    @PATCH("rest/v1/notifications")
    suspend fun markAllNotificationsRead(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("recipient_id") recipientFilter: String,
        @Query("is_read") unreadFilter: String = "eq.false",
        @Body updates: Map<String, Boolean> = mapOf("is_read" to true)
    ): Response<Unit>

    @POST("rest/v1/device_push_tokens?on_conflict=user_id,device_id")
    suspend fun upsertDevicePushToken(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "resolution=merge-duplicates",
        @Body body: SupabaseDeviceTokenUpsertDto
    ): Response<Unit>

    // Marketplace & Comunidades
    @GET("rest/v1/products")
    suspend fun getProducts(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("status") statusFilter: String = "eq.active",
        @Query("select") select: String = "*,seller:profiles!seller_id(id,username,full_name,avatar_url,is_verified,is_online)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 30
    ): Response<List<SupabaseProductDto>>

    @POST("rest/v1/products")
    suspend fun createProduct(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: SupabaseProductInsertDto
    ): Response<List<SupabaseProductDto>>

    @GET("rest/v1/groups")
    suspend fun getGroups(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("order") order: String = "members_count.desc",
        @Query("limit") limit: Int = 30
    ): Response<List<SupabaseGroupDto>>

    @GET("rest/v1/group_members")
    suspend fun getMyGroupMemberships(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("user_id") userFilter: String
    ): Response<List<SupabaseGroupMemberDto>>

    @POST("rest/v1/group_members")
    suspend fun joinGroup(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseGroupMemberDto
    ): Response<Unit>

    @DELETE("rest/v1/group_members")
    suspend fun leaveGroup(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("group_id") groupFilter: String,
        @Query("user_id") userFilter: String
    ): Response<Unit>

    // Moderação, Denúncias, Auditoria e Erros
    @GET("rest/v1/reports")
    suspend fun getReports(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("select") select: String = "*,reporter:profiles!reporter_id(id,username,full_name,avatar_url,is_verified,is_online)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 30
    ): Response<List<SupabaseReportDto>>

    @POST("rest/v1/reports")
    suspend fun submitReport(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseReportInsertDto
    ): Response<Unit>

    @GET("rest/v1/audit_logs")
    suspend fun getAuditLogs(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Query("select") select: String = "*,actor:profiles!actor_id(id,username,full_name,avatar_url,is_verified,is_online)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 20
    ): Response<List<SupabaseAuditLogDto>>

    @POST("rest/v1/error_logs")
    suspend fun logClientError(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: SupabaseErrorLogInsertDto
    ): Response<Unit>

    // Storage CDN & Edge Functions
    @POST("storage/v1/object/media/{objectPath}")
    suspend fun uploadMedia(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("Content-Type") contentType: String,
        @Path("objectPath", encoded = true) objectPath: String,
        @Body fileBody: RequestBody
    ): Response<Map<String, Any>>

    @POST("functions/v1/send-push-notification")
    suspend fun dispatchPushNotification(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body payload: SupabasePushDispatchDto
    ): Response<Map<String, Any>>

    @POST("functions/v1/admin-moderate")
    suspend fun adminModerate(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body payload: SupabaseAdminModerationDto
    ): Response<Map<String, Any>>

    @POST("functions/v1/firebase-session")
    suspend fun exchangeFirebaseSession(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Header("X-Firebase-Token") firebaseToken: String = firebaseBearer.removePrefix("Bearer ").trim(),
        @Body body: FirebaseSessionExchangeRequest = FirebaseSessionExchangeRequest(
            firebaseToken = firebaseBearer.removePrefix("Bearer ").trim()
        )
    ): Response<FirebaseSessionExchangeDto>

    @POST("functions/v1/email-otp")
    suspend fun callEmailOtp(
        @Header("apikey") apiKey: String,
        @Header("Authorization") gatewayBearer: String,
        @Body body: EmailOtpRequestDto
    ): Response<EmailOtpResponseDto>

    @POST("rest/v1/rpc/get_ranked_feed")
    suspend fun getRankedFeed(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: RankedFeedRequestDto
    ): Response<List<RankedPostScoreDto>>

    @POST("rest/v1/rpc/get_recommended_videos")
    suspend fun getRecommendedVideos(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: RecommendedVideosRequestDto
    ): Response<List<RecommendedVideoScoreDto>>

    @POST("rest/v1/rpc/get_trending_topics")
    suspend fun getTrendingTopics(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: TrendingTopicsRequestDto = TrendingTopicsRequestDto()
    ): Response<List<TrendingTopicDto>>

    @POST("rest/v1/rpc/upsert_interest_signal")
    suspend fun upsertInterestSignal(
        @Header("apikey") apiKey: String,
        @Header("Authorization") firebaseBearer: String,
        @Body body: InterestSignalUpsertDto
    ): Response<Unit>

    companion object {
        fun create(
            baseUrl: String = SupabaseConfig.supabaseUrl,
            customClient: OkHttpClient? = null
        ): SupabaseApiService? {
            if (customClient == null && !SupabaseConfig.isSupabaseConfigured) return null
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
            val client = customClient ?: OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(45, TimeUnit.SECONDS)
                .build()

            val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            return Retrofit.Builder()
                .baseUrl(normalizedBase)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(SupabaseApiService::class.java)
        }
    }
}
