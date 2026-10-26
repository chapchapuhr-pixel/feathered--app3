package com.unera.social.data

import android.content.Context
import com.unera.social.model.User
import org.json.JSONObject

/**
 * UserSessionManager
 *
 * Implements persistent session storage matching Feathered web LS_USER_KEY ('user').
 * Saves, restores, and clears logged-in user profile from local SharedPreferences.
 */
object UserSessionManager {

    private const val PREFS_NAME = "feathered_prefs"
    private const val LS_USER_KEY = "user"

    fun saveUser(context: Context, user: User) {
        if (user.id <= 0L) return
        try {
            val json = JSONObject().apply {
                put("id", user.id)
                put("name", user.name)
                put("username", user.username)
                put("email", user.email)
                put("profile_image_url", user.profileImageUrl)
                put("cover_image_url", user.coverImageUrl)
                put("bio", user.bio)
                put("location", user.location)
                put("work", user.work)
                put("education", user.education)
                put("website", user.website)
                put("followers_count", user.followersCount)
                put("following_count", user.followingCount)
                put("is_verified", user.isVerified)
                put("is_online", user.isOnline)
                put("role", user.role)
                put("birthDate", user.birthDate ?: "")
                put("gender", user.gender)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(LS_USER_KEY, json.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSavedUser(context: Context): User? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(LS_USER_KEY, null) ?: return null
            if (jsonStr.isBlank()) return null
            val obj = JSONObject(jsonStr)
            val id = obj.optLong("id", 0L)
            if (id <= 0L) return null

            val username = obj.optString("username").trim().ifBlank { "user$id" }
            val name = obj.optString("name").trim().ifBlank { username }
            val email = obj.optString("email").trim()
            val avatar = obj.optString("profile_image_url").trim()
            val cover = obj.optString("cover_image_url").trim()
            val bio = obj.optString("bio").trim()
            val location = obj.optString("location").trim()
            val work = obj.optString("work").trim()
            val education = obj.optString("education").trim()
            val website = obj.optString("website").trim()
            val followersCount = obj.optInt("followers_count", 0)
            val followingCount = obj.optInt("following_count", 0)
            val isVerified = obj.optBoolean("is_verified", false)
            val isOnline = obj.optBoolean("is_online", true)
            val role = obj.optString("role", "user")
            val birthDate = obj.optString("birthDate").trim().takeIf { it.isNotBlank() }
            val gender = obj.optString("gender", "Male")

            return User(
                id = id,
                name = name,
                username = username,
                email = email,
                profileImageUrl = avatar.ifBlank { "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=800" },
                coverImageUrl = cover.ifBlank { "https://images.unsplash.com/photo-1579546929518-9e396f3cc809?w=1200" },
                bio = bio,
                location = location,
                work = work,
                education = education,
                website = website,
                followersCount = followersCount,
                followingCount = followingCount,
                isVerified = isVerified,
                isOnline = isOnline,
                role = role,
                birthDate = birthDate,
                gender = gender
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun clearUser(context: Context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(LS_USER_KEY)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
