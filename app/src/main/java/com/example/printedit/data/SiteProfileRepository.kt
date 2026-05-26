package jp.webpdf.app.data

import android.content.Context
import org.json.JSONObject

class SiteProfileRepository(context: Context) {
    private val prefs = context.getSharedPreferences("site_profiles_v1", Context.MODE_PRIVATE)

    fun getProfile(domain: String): SiteProfile? {
        if (domain.isBlank()) return null
        val key = normalize(domain)
        // 1. ユーザー設定を優先
        prefs.getString(key, null)?.let { json ->
            try {
                return SiteProfile.fromJson(JSONObject(json))
            } catch (_: Exception) { /* fall through to builtin */ }
        }
        // 2. ビルトインプロファイル: forceLoadLazyImages が React/Next.js の hydration を壊す等、
        //    アプリ固有 JS が悪影響を及ぼすことが既知のサイトはデフォルトで安全側に倒す
        return BUILTIN_PROFILES[key]
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

    companion object {
        // Yahoo!ニュース系は IntersectionObserver で記事を遅延ハイドレートしており、
        // forceLoadLazyImagesJs が img.src を data-* から強制書き換えすると React の virtual DOM
        // とズレて再レンダリング時にコンテナが空 (白飛び) になる。skipLazyLoader でこれを抑止する
        private val BUILTIN_PROFILES = mapOf(
            "news.yahoo.co.jp"    to SiteProfile(domain = "news.yahoo.co.jp",    skipLazyLoader = true),
            "finance.yahoo.co.jp" to SiteProfile(domain = "finance.yahoo.co.jp", skipLazyLoader = true),
            "sports.yahoo.co.jp"  to SiteProfile(domain = "sports.yahoo.co.jp",  skipLazyLoader = true),
        )
    }
}
