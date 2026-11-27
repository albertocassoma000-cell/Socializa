package com.example.domain.algorithm

import com.example.data.model.CommunityGroup
import com.example.data.model.FeedMode
import com.example.data.model.FeedPost
import com.example.data.model.MarketplaceProduct
import com.example.data.model.SearchResultsBundle
import com.example.data.model.TrendingTopic
import com.example.data.model.UserProfile
import com.example.data.model.VideoReel
import com.example.data.model.VideoTelemetry
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class SafetyCheckResult(
    val isAllowed: Boolean,
    val reason: String? = null
)

object SocializaAlgorithmEngine {

    private val prohibitedTerms = listOf(
        "ganha dinheiro rápido grátis",
        "transferência duplicada garantida",
        "clica no link fraudulento",
        "phishing",
        "scam",
        "fraude bancária"
    )

    fun extractInterestTopics(
        caption: String = "",
        hashtags: List<String> = emptyList(),
        category: String? = null
    ): List<String> {
        val out = linkedSetOf<String>()
        category?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        hashtags.forEach { raw ->
            val clean = raw.trim().lowercase().let { if (it.startsWith("#")) it else "#$it" }
            if (clean.length > 1) out.add(clean)
        }
        Regex("#[\\p{L}0-9_]+").findAll(caption).forEach { m ->
            out.add(m.value.lowercase())
        }
        return out.toList()
    }

    fun rankFeedPosts(
        posts: List<FeedPost>,
        mode: FeedMode,
        currentUser: UserProfile,
        interestWeights: Map<String, Double> = emptyMap(),
        serverScoreHints: Map<String, Double> = emptyMap(),
        nowMs: Long = System.currentTimeMillis()
    ): List<FeedPost> {
        if (posts.isEmpty()) return emptyList()

        return when (mode) {
            FeedMode.FOLLOWING -> {
                posts
                    .filter { it.isAuthorFollowed || it.authorId == currentUser.id }
                    .sortedByDescending { parseIsoMillis(it.createdAtIso, nowMs) }
            }

            FeedMode.TRENDING -> {
                posts.sortedByDescending { post ->
                    val serverHint = serverScoreHints[post.id] ?: 0.0
                    val ageHours = ageHoursSince(post.createdAtIso, nowMs)
                    val velocity = (
                        post.likesCount * 3.0 +
                            post.commentsCount * 5.0 +
                            post.sharesCount * 7.0 +
                            min(post.viewsCount, 5000) * 0.25
                        ) / (ageHours + 2.0).pow(1.35)
                    velocity + (serverHint * 0.1)
                }
            }

            FeedMode.FOR_YOU -> {
                // Primeira passagem: pontuação base por publicação
                val baseScored = posts.map { post ->
                    val score = computeForYouPostScore(
                        post = post,
                        currentUser = currentUser,
                        interestWeights = interestWeights,
                        serverHint = serverScoreHints[post.id] ?: 0.0,
                        nowMs = nowMs
                    )
                    post to score
                }.sortedByDescending { it.second }

                // Segunda passagem: penalidade anti-repetição (-20) quando o mesmo autor aparece > 2 vezes seguidas no topo
                val authorCount = mutableMapOf<String, Int>()
                val reranked = baseScored.map { (post, baseScore) ->
                    val seen = authorCount.getOrDefault(post.authorId, 0)
                    authorCount[post.authorId] = seen + 1
                    val antiRepetitionPenalty = if (seen >= 2) 20.0 * (seen - 1) else 0.0
                    post to (baseScore - antiRepetitionPenalty)
                }
                reranked.sortedByDescending { it.second }.map { it.first }
            }
        }
    }

    fun computeForYouPostScore(
        post: FeedPost,
        currentUser: UserProfile,
        interestWeights: Map<String, Double> = emptyMap(),
        serverHint: Double = 0.0,
        nowMs: Long = System.currentTimeMillis()
    ): Double {
        val topics = extractInterestTopics(post.caption, post.hashtags, post.category)
        val matchedInterest = topics.sumOf { interestWeights[it] ?: 0.0 }
        val interestBoost = if (matchedInterest > 0.0) min(30.0, 12.0 + matchedInterest * 2.5) else 0.0

        val ageHours = ageHoursSince(post.createdAtIso, nowMs)
        val recencyBoost = max(0.0, 20.0 * (1.0 - min(1.0, ageHours / 24.0)))

        val friendBoost = if (post.isAuthorFollowed) 15.0 else 0.0
        val commentsBoost = min(10.0, ln(1.0 + post.commentsCount) * 4.0)
        val sharesBoost = min(15.0, ln(1.0 + post.sharesCount) * 6.0)
        val likesBoost = min(8.0, ln(1.0 + post.likesCount) * 2.5)

        val userProvince = currentUser.province.trim()
        val proximityBoost = if (
            userProvince.isNotBlank() &&
            post.locationLabel.contains(userProvince, ignoreCase = true)
        ) 12.0 else 0.0

        return interestBoost +
            recencyBoost +
            friendBoost +
            commentsBoost +
            sharesBoost +
            likesBoost +
            proximityBoost +
            (serverHint * 0.15)
    }

    fun rankRecommendedVideos(
        reels: List<VideoReel>,
        interestWeights: Map<String, Double> = emptyMap(),
        telemetryByVideo: Map<String, VideoTelemetry> = emptyMap(),
        serverScoreHints: Map<String, Double> = emptyMap()
    ): List<VideoReel> {
        if (reels.isEmpty()) return emptyList()
        val base = reels.map { reel ->
            val topics = extractInterestTopics(reel.caption, reel.hashtags, null)
            val interestBoost = if (topics.any { (interestWeights[it] ?: 0.0) > 0.0 }) 25.0 else 0.0
            val tel = telemetryByVideo[reel.id]
            val watchScore = min(18.0, (tel?.watchSeconds ?: 0) * 0.45)
            val completionBoost = if (tel?.completed == true) 20.0 else 0.0
            val replayBoost = if (tel?.replayed == true) 18.0 else 0.0
            val engagementScore = (reel.likesCount * 2.5) +
                (reel.commentsCount * 4.5) +
                (reel.sharesCount * 6.0) +
                (if (reel.isSaved) 10.0 else 0.0) +
                (if (reel.isFollowing) 8.0 else 0.0)
            val serverBoost = (serverScoreHints[reel.id] ?: 0.0) * 0.2
            reel to (engagementScore + interestBoost + watchScore + completionBoost + replayBoost + serverBoost)
        }.sortedByDescending { it.second }

        val authorSeen = mutableMapOf<String, Int>()
        return base.map { (reel, score) ->
            val count = authorSeen.getOrDefault(reel.authorId, 0)
            authorSeen[reel.authorId] = count + 1
            val penalty = if (count >= 2) 15.0 * (count - 1) else 0.0
            reel to (score - penalty)
        }.sortedByDescending { it.second }.map { it.first }
    }

    fun suggestPeople(
        candidates: List<UserProfile>,
        currentUser: UserProfile,
        posts: List<FeedPost>,
        interestWeights: Map<String, Double> = emptyMap(),
        recentlySuggestedIds: Set<String> = emptySet(),
        serverScoreHints: Map<String, Double> = emptyMap()
    ): List<UserProfile> {
        val authorTopics = posts.groupBy { it.authorId }.mapValues { (_, userPosts) ->
            userPosts.flatMap { extractInterestTopics(it.caption, it.hashtags, it.category) }.toSet()
        }
        return candidates
            .filter { it.id != currentUser.id && !it.isFollowedByMe && !it.isBlockedByMe && !it.isSuspended }
            .sortedByDescending { candidate ->
                val sameProvince = if (candidate.province.equals(currentUser.province, ignoreCase = true)) 14.0 else 0.0
                val sharedInterests = authorTopics[candidate.id].orEmpty()
                    .count { (interestWeights[it] ?: 0.0) > 0.0 } * 12.0
                val popularity = min(18.0, ln(1.0 + candidate.followersCount) * 4.0)
                val activity = min(10.0, candidate.postsCount * 1.5)
                val verifiedBonus = if (candidate.hasActiveVerifiedBadge) 4.0 else 0.0
                val antiRepeatPenalty = if (candidate.id in recentlySuggestedIds) 15.0 else 0.0
                val serverBoost = serverScoreHints[candidate.id] ?: 0.0
                sameProvince + sharedInterests + popularity + activity + verifiedBonus + serverBoost - antiRepeatPenalty
            }
    }

    fun computeTrendingTopics(
        posts: List<FeedPost>,
        reels: List<VideoReel>,
        limit: Int = 8,
        nowMs: Long = System.currentTimeMillis()
    ): List<TrendingTopic> {
        val buckets = linkedMapOf<String, MutableList<Pair<String, Double>>>()

        posts.forEach { post ->
            val age = ageHoursSince(post.createdAtIso, nowMs)
            val weight = (10.0 + post.likesCount * 2.0 + post.commentsCount * 4.0 + post.sharesCount * 6.0) /
                (age + 2.0).pow(1.1)
            post.hashtags.forEach { raw ->
                val tag = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
                if (tag.length > 1) {
                    buckets.getOrPut(tag.lowercase()) { mutableListOf() }.add(tag to weight)
                }
            }
        }

        reels.forEach { reel ->
            val weight = (10.0 + reel.likesCount * 2.0 + reel.commentsCount * 4.0 + reel.sharesCount * 6.0) / 3.0
            reel.hashtags.forEach { raw ->
                val tag = raw.trim().let { if (it.startsWith("#")) it else "#$it" }
                if (tag.length > 1) {
                    buckets.getOrPut(tag.lowercase()) { mutableListOf() }.add(tag to weight)
                }
            }
        }

        val computed = buckets.values.map { entries ->
            TrendingTopic(
                tag = entries.first().first,
                mentionsCount = entries.size,
                score = entries.sumOf { it.second }
            )
        }.sortedByDescending { it.score }

        if (computed.isNotEmpty()) return computed.take(limit)
        return listOf(
            TrendingTopic("#Luanda", 0, 10.0),
            TrendingTopic("#Angola", 0, 9.0),
            TrendingTopic("#Música", 0, 8.0),
            TrendingTopic("#Futebol", 0, 7.0),
            TrendingTopic("#Cultura", 0, 6.0),
            TrendingTopic("#Juventude", 0, 5.0)
        ).take(limit)
    }

    fun searchUnified(
        query: String,
        people: List<UserProfile>,
        posts: List<FeedPost>,
        reels: List<VideoReel>,
        groups: List<CommunityGroup>,
        products: List<MarketplaceProduct>,
        trendingTopics: List<TrendingTopic>
    ): SearchResultsBundle {
        val q = query.trim()
        if (q.isBlank()) {
            return SearchResultsBundle(
                people = people,
                posts = posts,
                videos = reels,
                groups = groups,
                pages = people.filter { it.hasActiveVerifiedBadge || it.website.isNotBlank() || it.postsCount >= 3 }
                    .ifEmpty { products.map { p -> UserProfile(id = p.sellerId, username = p.sellerName.lowercase().replace(" ", "_"), fullName = p.sellerName, bio = p.title, province = p.province) } },
                hashtags = trendingTopics
            )
        }

        val matchedPeople = people.filter {
            it.fullName.contains(q, ignoreCase = true) ||
                it.username.contains(q, ignoreCase = true) ||
                it.province.contains(q, ignoreCase = true) ||
                it.bio.contains(q, ignoreCase = true)
        }.sortedByDescending {
            val exact = if (it.username.equals(q.removePrefix("@"), ignoreCase = true) || it.fullName.equals(q, ignoreCase = true)) 100.0 else 0.0
            exact + it.followersCount * 0.1
        }

        val matchedPosts = posts.filter {
            it.caption.contains(q, ignoreCase = true) ||
                it.authorName.contains(q, ignoreCase = true) ||
                it.authorUsername.contains(q, ignoreCase = true) ||
                it.locationLabel.contains(q, ignoreCase = true) ||
                it.hashtags.any { tag -> tag.contains(q, ignoreCase = true) }
        }

        val matchedVideos = reels.filter {
            it.caption.contains(q, ignoreCase = true) ||
                it.authorName.contains(q, ignoreCase = true) ||
                it.authorUsername.contains(q, ignoreCase = true) ||
                it.musicTitle.contains(q, ignoreCase = true) ||
                it.hashtags.any { tag -> tag.contains(q, ignoreCase = true) }
        }

        val matchedGroups = groups.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true) ||
                it.province.contains(q, ignoreCase = true)
        }

        val matchedPages = (people.filter {
            (it.hasActiveVerifiedBadge || it.website.isNotBlank() || it.postsCount >= 2) &&
                (it.fullName.contains(q, ignoreCase = true) || it.bio.contains(q, ignoreCase = true) || it.website.contains(q, ignoreCase = true))
        } + products.filter {
            it.title.contains(q, ignoreCase = true) || it.sellerName.contains(q, ignoreCase = true)
        }.map { p ->
            UserProfile(
                id = p.sellerId,
                username = p.sellerName.lowercase().replace(" ", "_"),
                fullName = p.sellerName,
                bio = "Loja • ${p.title}",
                province = p.province
            )
        }).distinctBy { it.id }

        val matchedTags = trendingTopics.filter {
            it.tag.contains(q.removePrefix("#"), ignoreCase = true)
        }

        return SearchResultsBundle(
            people = matchedPeople,
            posts = matchedPosts,
            videos = matchedVideos,
            groups = matchedGroups,
            pages = matchedPages,
            hashtags = matchedTags
        )
    }

    fun evaluateContentSafety(
        text: String,
        recentSubmissions: List<String> = emptyList()
    ): SafetyCheckResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SafetyCheckResult(true)
        val lower = trimmed.lowercase()

        if (Regex("(.)\\1{14,}").containsMatchIn(lower)) {
            return SafetyCheckResult(
                isAllowed = false,
                reason = "Publicação bloqueada: excesso de caracteres repetidos (anti-spam)."
            )
        }
        prohibitedTerms.forEach { term ->
            if (lower.contains(term)) {
                return SafetyCheckResult(
                    isAllowed = false,
                    reason = "Conteúdo bloqueado pela proteção de segurança da comunidade."
                )
            }
        }
        val links = Regex("https?://").findAll(lower).count()
        if (links > 4) {
            return SafetyCheckResult(
                isAllowed = false,
                reason = "Demasiados links externos na mesma mensagem (proteção anti-spam)."
            )
        }
        if (trimmed.length >= 12 && recentSubmissions.any { it.trim().equals(trimmed, ignoreCase = true) }) {
            return SafetyCheckResult(
                isAllowed = false,
                reason = "Publicação duplicada recente detetada. Altera o texto antes de publicar novamente."
            )
        }
        return SafetyCheckResult(true)
    }

    private fun ageHoursSince(iso: String, nowMs: Long): Double {
        val created = parseIsoMillis(iso, nowMs)
        return max(0.0, (nowMs - created) / 3_600_000.0)
    }

    private fun parseIsoMillis(iso: String, fallbackMs: Long): Long {
        if (iso.isBlank()) return fallbackMs
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(iso)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return fallbackMs
    }
}
