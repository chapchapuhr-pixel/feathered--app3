package com.unera.social.data

import com.unera.social.model.Comment
import com.unera.social.model.Post
import com.unera.social.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class LiveReactResult(
    val success: Boolean,
    val myReaction: String?,
    val reactionsCount: Int,
    val sharesCount: Int? = null,
    val commentsCount: Int? = null
)

data class LiveShareResult(
    val success: Boolean,
    val sharesCount: Int,
    val newPost: Post? = null
)

/**
 * FeedInteractionApiService
 * Implements the verbatim API specification from web Feed.tsx for:
 * 1. React (Posts, Videos, Groups, Music, Products, Events, Stories)
 * 2. Discuss / Comments (Fetch, Add, Like, Hide, Delete)
 * 3. Share (Posts, Videos, Groups, Music, Products, Events, Stories)
 */
object FeedInteractionApiService {

    private const val BASE_URL = "https://media.featheredsocial.site"
    private const val USER_AGENT = "FeatheredAndroid/1.0 (Linux; Android)"

    // =========================================================================
    // 1. REACT
    // =========================================================================

    /**
     * Reacts to any feed item type matching Feed.tsx / App.tsx reactToFeedItem
     */
    suspend fun reactToFeedItem(
        post: Post,
        userId: Long,
        reactionType: String
    ): LiveReactResult = withContext(Dispatchers.IO) {
        val isGroup = post.groupId != null || post.postType == "group_post"
        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl: String
        val payload = JSONObject()
        val extraHeaders = mutableMapOf<String, String>()

        when {
            isGroup -> {
                endpointUrl = "$BASE_URL/api/group-post-likes"
                val targetPostId = post.groupId ?: post.id
                payload.put("user_id", userId)
                payload.put("post_id", targetPostId)
                payload.put("type", reactionType)
            }
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                endpointUrl = "$BASE_URL/api/songs/$songId/react"
                payload.put("type", reactionType)
                payload.put("user_id", userId)
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                endpointUrl = "$BASE_URL/api/products/$productId/react"
                payload.put("type", reactionType)
                payload.put("user_id", userId)
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                endpointUrl = "$BASE_URL/api/events/$eventId/react"
                payload.put("type", reactionType)
                payload.put("user_id", userId)
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                endpointUrl = "$BASE_URL/api/stories/$storyId/react"
                extraHeaders["x-user-id"] = userId.toString()
                payload.put("user_id", userId)
                payload.put("type", reactionType)
            }
            else -> {
                // Default: Regular Post or Video Reel
                endpointUrl = "$BASE_URL/api/posts/${post.id}/react"
                payload.put("type", reactionType)
                payload.put("user_id", userId)
            }
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
                extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val success = root.optBoolean("success", true)
                    val myReactionRaw = if (root.has("my_reaction") && !root.isNull("my_reaction")) {
                        root.optString("my_reaction")
                    } else if (reactionType == "unlike") null else reactionType

                    val finalMyReaction = if (myReactionRaw == "unlike" || myReactionRaw.isNullOrBlank()) null else myReactionRaw

                    val reactionsCount = when {
                        root.has("reactions_count") -> root.optInt("reactions_count")
                        root.has("likes_count") -> root.optInt("likes_count")
                        root.has("count") -> root.optInt("count")
                        else -> if (finalMyReaction != null) post.likesCount + 1 else (post.likesCount - 1).coerceAtLeast(0)
                    }

                    val sharesCount = if (root.has("shares_count")) root.optInt("shares_count")
                        else if (root.has("shares")) root.optInt("shares") else null

                    val commentsCount = if (root.has("comments_count")) root.optInt("comments_count") else null

                    return@withContext LiveReactResult(
                        success = success,
                        myReaction = finalMyReaction,
                        reactionsCount = reactionsCount,
                        sharesCount = sharesCount,
                        commentsCount = commentsCount
                    )
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback optimistic calculation if network failure
        val isSame = post.myReaction == reactionType
        val newReaction = if (isSame || reactionType == "unlike") null else reactionType
        val prevHad = post.myReaction != null
        val newCount = when {
            !prevHad && newReaction != null -> post.likesCount + 1
            prevHad && newReaction == null -> (post.likesCount - 1).coerceAtLeast(0)
            else -> post.likesCount
        }
        LiveReactResult(success = false, myReaction = newReaction, reactionsCount = newCount)
    }

    // =========================================================================
    // 2. DISCUSS / COMMENTS
    // =========================================================================

    /**
     * Fetch live comments from the server matching getFeedCommentFetchEndpoint
     */
    suspend fun fetchComments(post: Post, viewerId: Long): List<Comment> = withContext(Dispatchers.IO) {
        val commentsList = mutableListOf<Comment>()
        val isGroup = post.groupId != null || post.postType == "group_post"
        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl = when {
            isGroup -> "$BASE_URL/api/group-post-comments?post_id=${post.groupId ?: post.id}&viewerId=$viewerId"
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                "$BASE_URL/api/songs/$songId/comments?viewerId=$viewerId"
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                "$BASE_URL/api/products/$productId/reviews?viewerId=$viewerId"
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                "$BASE_URL/api/events/$eventId/comments?viewerId=$viewerId"
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                "$BASE_URL/api/stories/$storyId/comments?viewerId=$viewerId"
            }
            else -> "$BASE_URL/api/posts/${post.id}/comments?viewerId=$viewerId"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", viewerId.toString())
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val arr = root.optJSONArray("comments")
                        ?: root.optJSONArray("reviews")
                        ?: root.optJSONArray("data")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            parseComment(obj, viewerId)?.let { commentsList.add(it) }
                        }
                    }
                } else if (responseText.startsWith("[")) {
                    val arr = JSONArray(responseText)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        parseComment(obj, viewerId)?.let { commentsList.add(it) }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        commentsList
    }

    /**
     * Submit a new live comment matching App.tsx createComment
     */
    suspend fun addComment(
        post: Post,
        userId: Long,
        text: String,
        parentCommentId: Long? = null,
        imageUrl: String? = null
    ): Comment? = withContext(Dispatchers.IO) {
        val isGroup = post.groupId != null || post.postType == "group_post"
        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl: String
        val payload = JSONObject().apply {
            put("user_id", userId)
        }

        when {
            isGroup -> {
                endpointUrl = "$BASE_URL/api/group-post-comments"
                payload.put("post_id", post.groupId ?: post.id)
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                endpointUrl = "$BASE_URL/api/songs/$songId/comment"
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                endpointUrl = "$BASE_URL/api/products/$productId/review"
                payload.put("rating", JSONObject.NULL)
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                endpointUrl = "$BASE_URL/api/events/$eventId/comment"
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                endpointUrl = "$BASE_URL/api/stories/$storyId/comments"
                payload.put("content", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
            else -> {
                endpointUrl = "$BASE_URL/api/posts/${post.id}/comments"
                payload.put("text", text)
                if (parentCommentId != null && parentCommentId > 0) payload.put("parent_comment_id", parentCommentId)
                if (!imageUrl.isNullOrBlank()) payload.put("image_url", imageUrl)
            }
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val commentObj = root.optJSONObject("comment")
                        ?: root.optJSONObject("review")
                        ?: root.optJSONObject("data")
                        ?: root
                    return@withContext parseComment(commentObj, userId)
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    /**
     * Like a comment matching getCommentLikeEndpoint
     */
    suspend fun likeComment(
        itemType: String,
        commentId: Long,
        userId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val endpointUrl = when (itemType) {
            "group_post" -> "$BASE_URL/api/group-post-comment-likes"
            "song", "music" -> "$BASE_URL/api/song-comments/$commentId/like"
            "product" -> "$BASE_URL/api/product-reviews/$commentId/like"
            "event" -> "$BASE_URL/api/event-comments/$commentId/like"
            else -> "$BASE_URL/api/post-comments/$commentId/like"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            val payload = JSONObject().apply {
                put("user_id", userId)
                if (itemType == "group_post") {
                    put("comment_id", commentId)
                }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            return@withContext code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Delete a comment matching CommentsSheet.handleDelete
     */
    suspend fun deleteComment(
        itemType: String,
        commentId: Long,
        userId: Long,
        parentId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val endpointUrl = when (itemType) {
            "song", "music" -> "$BASE_URL/api/songs/$parentId/comments?comment_id=$commentId&user_id=$userId"
            "product" -> "$BASE_URL/api/products/$parentId/reviews?comment_id=$commentId&user_id=$userId"
            "story" -> "$BASE_URL/api/stories/$parentId/comments?comment_id=$commentId&user_id=$userId"
            else -> "$BASE_URL/api/post-comments/$commentId/delete?user_id=$userId"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }
            val code = conn.responseCode
            conn.disconnect()
            return@withContext code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Hide or unhide a comment matching CommentsSheet.handleToggleHide
     */
    suspend fun hideComment(
        itemType: String,
        commentId: Long,
        userId: Long,
        parentId: Long,
        action: String // "hide" | "unhide"
    ): Boolean = withContext(Dispatchers.IO) {
        val isPatch = itemType in listOf("song", "music", "product", "story")
        val endpointUrl = when (itemType) {
            "song", "music" -> "$BASE_URL/api/songs/$parentId/comments"
            "product" -> "$BASE_URL/api/products/$parentId/reviews"
            "story" -> "$BASE_URL/api/stories/$parentId/comments"
            else -> "$BASE_URL/api/post-comments/$commentId/hide"
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = if (isPatch) "PATCH" else "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            val payload = JSONObject().apply {
                put("user_id", userId)
                put("action", action)
                if (isPatch) {
                    put("comment_id", commentId)
                }
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            conn.disconnect()
            return@withContext code in 200..299
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    // =========================================================================
    // 3. SHARE
    // =========================================================================

    /**
     * Share feed item matching ShareBottomSheet.getShareEndpoint
     */
    suspend fun shareItem(
        post: Post,
        userId: Long,
        destination: String = "feed",
        message: String = ""
    ): LiveShareResult = withContext(Dispatchers.IO) {
        val isGroup = post.groupId != null || post.postType == "group_post"
        val isMusic = post.song != null || post.postType == "music" || post.postType == "song"
        val isProduct = post.product != null || post.postType == "product" || post.postType == "marketplace"
        val isEvent = post.event != null || post.postType == "event"
        val isStory = post.story != null || post.postType == "story"

        val endpointUrl: String
        val payload = JSONObject()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val isoDate = isoFormat.format(Date())

        when {
            isGroup -> {
                endpointUrl = "$BASE_URL/api/groups/posts/share"
                payload.put("user_id", userId)
                payload.put("post_id", post.groupId ?: post.id)
                payload.put("group_id", post.groupId ?: 0L)
                payload.put("destination", destination)
                payload.put("source", "feed")
                if (message.isNotBlank()) payload.put("message", message)
            }
            isMusic -> {
                val songId = post.song?.id?.toLongOrNull() ?: post.id
                endpointUrl = "$BASE_URL/api/songs/$songId/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                payload.put("shared_at", isoDate)
                payload.put("item_type", "music")
                payload.put("post_id", post.id)
                payload.put("song_id", songId)
                if (message.isNotBlank()) payload.put("message", message)
            }
            isProduct -> {
                val productId = post.product?.id ?: post.id
                endpointUrl = "$BASE_URL/api/products/$productId/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                payload.put("shared_at", isoDate)
                payload.put("item_type", "product")
                payload.put("post_id", post.id)
                payload.put("product_id", productId)
                if (message.isNotBlank()) payload.put("message", message)
            }
            isEvent -> {
                val eventId = post.event?.id ?: post.id
                endpointUrl = "$BASE_URL/api/events/$eventId/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                payload.put("shared_at", isoDate)
                payload.put("item_type", "event")
                payload.put("post_id", post.id)
                payload.put("event_id", eventId)
                if (message.isNotBlank()) payload.put("message", message)
            }
            isStory -> {
                val storyId = post.story?.id ?: post.id
                endpointUrl = "$BASE_URL/api/stories/$storyId/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                if (message.isNotBlank()) payload.put("message", message)
            }
            else -> {
                endpointUrl = "$BASE_URL/api/posts/${post.id}/share"
                payload.put("user_id", userId)
                payload.put("destination", destination)
                payload.put("shared_at", isoDate)
                payload.put("item_type", "post")
                payload.put("post_id", post.id)
                if (message.isNotBlank()) payload.put("message", message)
                payload.put("audience", "Public")
            }
        }

        try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("x-user-id", userId.toString())
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(payload.toString())
                it.flush()
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }.trim()
                conn.disconnect()
                if (responseText.startsWith("{")) {
                    val root = JSONObject(responseText)
                    val success = root.optBoolean("success", true)
                    val sharesCount = when {
                        root.has("shares_count") -> root.optInt("shares_count")
                        root.has("shares") -> root.optInt("shares")
                        else -> post.sharesCount + 1
                    }
                    val postObj = root.optJSONObject("post")
                    val newPost = if (postObj != null) {
                        PostsApiService.parseFeedItemToPostExternal(postObj)
                    } else null

                    return@withContext LiveShareResult(
                        success = success,
                        sharesCount = sharesCount,
                        newPost = newPost
                    )
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        LiveShareResult(success = false, sharesCount = post.sharesCount + 1)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun parseComment(obj: JSONObject, viewerId: Long): Comment? {
        val id = obj.optLong("id", obj.optLong("comment_id", System.currentTimeMillis()))
        val text = obj.optString("text").takeIf { it != "null" }
            ?: obj.optString("content").takeIf { it != "null" }
            ?: ""
        val userId = obj.optLong("user_id", 0L)
        val userObj = obj.optJSONObject("user") ?: obj.optJSONObject("author")

        val authorName = userObj?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("display_name")?.takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("author_name").takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("user_name").takeIf { it.isNotBlank() && it != "null" }
            ?: userObj?.optString("username")?.takeIf { it.isNotBlank() && it != "null" }
            ?: ""

        val username = userObj?.optString("username")?.takeIf { it.isNotBlank() && it != "null" }
            ?: obj.optString("username").takeIf { it.isNotBlank() && it != "null" }
            ?: if (authorName.isNotBlank()) "@${authorName.lowercase().replace(" ", "")}" else ""

        val avatar = userObj?.optString("profile_image_url")?.takeIf { it.isNotBlank() && it != "null" && !it.contains("photo-1535713875002") }
            ?: userObj?.optString("avatar_url")?.takeIf { it.isNotBlank() && it != "null" && !it.contains("photo-1535713875002") }
            ?: obj.optString("author_avatar").takeIf { it.isNotBlank() && it != "null" && !it.contains("photo-1535713875002") }
            ?: obj.optString("profile_image_url").takeIf { it.isNotBlank() && it != "null" && !it.contains("photo-1535713875002") }
            ?: ""

        val isVerified = userObj?.optBoolean("is_verified", false) ?: false
        val author = User(
            id = userId,
            name = authorName.ifBlank { username.removePrefix("@") },
            username = username,
            profileImageUrl = avatar,
            isVerified = isVerified
        )

        val rawDate = obj.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
        val timestamp = PostsApiService.formatRelativeTime(rawDate)
        val likesCount = obj.optInt("likes_count", obj.optInt("likes", 0))
        val isLiked = obj.optBoolean("is_liked", false) || obj.optInt("liked_by_me", 0) == 1
        val imageUrl = obj.optString("image_url").takeIf { it.isNotBlank() && it != "null" }
        val parentCommentId = if (obj.has("parent_comment_id") && !obj.isNull("parent_comment_id")) {
            obj.optLong("parent_comment_id")
        } else if (obj.has("parent_id") && !obj.isNull("parent_id")) {
            obj.optLong("parent_id")
        } else null

        return Comment(
            id = id,
            author = author,
            text = text,
            timestamp = timestamp,
            likesCount = likesCount,
            isLiked = isLiked,
            imageUrl = imageUrl,
            parentCommentId = parentCommentId
        )
    }
}
