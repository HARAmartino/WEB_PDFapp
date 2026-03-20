package com.example.printedit.data

import android.content.Context
import org.json.JSONObject

class SiteProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("site_profiles_v1", Context.MODE_PRIVATE)

    fun getProfile(domain: String): SiteProfile? {
        if (domain.isBlank()) return null
        val json = prefs.getString(normalize(domain), null) ?: return null
        return try {
            SiteProfile.fromJson(JSONObject(json))
        } catch (_: Exception) { null }
    }

    fun saveProfile(profile: SiteProfile) {
        val key = normalize(profile.domain)
        prefs.edit().putString(key, profile.copy(domain = key).toJson().toString()).apply()
    }

    fun deleteProfile(domain: String) {
        prefs.edit().remove(normalize(domain)).apply()
    }

    fun getAllProfiles(): List<SiteProfile> =
        prefs.all.values.mapNotNull { value ->
            try { SiteProfile.fromJson(JSONObject(value as String)) } catch (_: Exception) { null }
        }.sortedBy { it.domain }

    private fun normalize(domain: String): String = domain.lowercase().removePrefix("www.")
}
