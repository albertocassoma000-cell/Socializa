package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "cached_posts")
data class CachedPostEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorAvatarUrl: String?,
    val authorVerified: Boolean,
    val postType: String,
    val caption: String,
    val hashtagsCsv: String,
    val locationLabel: String,
    val visibility: String,
    val mediaUrlsCsv: String,
    val musicTitle: String? = null,
    val musicUrl: String? = null,
    val likesCount: Int,
    val commentsCount: Int,
    val sharesCount: Int,
    val viewsCount: Int,
    val isLiked: Boolean,
    val isSaved: Boolean,
    val createdAtIso: String,
    val cachedAtMs: Long
)

@Entity(tableName = "cached_profiles")
data class CachedProfileEntity(
    @PrimaryKey val id: String,
    val username: String,
    val fullName: String,
    val bio: String,
    val avatarUrl: String?,
    val country: String,
    val province: String,
    val website: String,
    val isPrivate: Boolean,
    val isVerified: Boolean,
    val verifiedUntilMs: Long = 0L,
    val role: String,
    val followersCount: Int,
    val followingCount: Int,
    val postsCount: Int,
    val cachedAtMs: Long
)

@Dao
interface MeuPaisCacheDao {
    @Query("SELECT * FROM cached_posts WHERE ownerUid = :ownerUid ORDER BY createdAtIso DESC LIMIT :limit")
    suspend fun getCachedPosts(ownerUid: String, limit: Int = 30): List<CachedPostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosts(posts: List<CachedPostEntity>)

    @Query("DELETE FROM cached_posts WHERE ownerUid = :ownerUid")
    suspend fun clearPostsForOwner(ownerUid: String)

    @Query("DELETE FROM cached_posts WHERE id = :postId")
    suspend fun deletePost(postId: String)

    @Query("SELECT * FROM cached_profiles WHERE id = :uid LIMIT 1")
    suspend fun getCachedProfile(uid: String): CachedProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: CachedProfileEntity)

    @Query("DELETE FROM cached_posts")
    suspend fun clearAllPosts()
}

@Database(
    entities = [CachedPostEntity::class, CachedProfileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MeuPaisCacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): MeuPaisCacheDao

    companion object {
        @Volatile
        private var INSTANCE: MeuPaisCacheDatabase? = null

        fun getInstance(context: Context): MeuPaisCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeuPaisCacheDatabase::class.java,
                    "meu_pais_cache.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
