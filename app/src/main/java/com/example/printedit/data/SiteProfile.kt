package com.example.printedit.data

import org.json.JSONObject

/** サイトごとのウェブビュー挙動を上書きするプロファイル */
enum class UserAgentMode(val label: String) {
    GLOBAL("グローバル設定に従う"),
    MOBILE("モバイル（スマートフォン）"),
    DESKTOP("PC（デスクトップ）")
}

data class SiteProfile(
    val domain: String,
    /** ユーザーエージェントの上書き */
    val userAgent: UserAgentMode = UserAgentMode.GLOBAL,
    /** メニュー表示修正 JS を強制注入 */
    val forceMenuFix: Boolean = false,
    /** 遅延画像読み込み JS をスキップ（IO パッチが悪影響を及ぼすサイト用） */
    val skipLazyLoader: Boolean = false,
    /** メディアの自動再生を許可（mediaPlaybackRequiresUserGesture を false に） */
    val allowAutoplay: Boolean = false,
    /** このサイトの広告ブロックを無効化（ホワイトリスト） */
    val disableAdBlock: Boolean = false,
    /** ページ読み込み後に注入するカスタム CSS */
    val customCss: String = "",
    /** ページ読み込み後に実行するカスタム JavaScript */
    val customJs: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("domain", domain)
        put("userAgent", userAgent.name)
        put("forceMenuFix", forceMenuFix)
        put("skipLazyLoader", skipLazyLoader)
        put("allowAutoplay", allowAutoplay)
        put("disableAdBlock", disableAdBlock)
        put("customCss", customCss)
        put("customJs", customJs)
    }

    companion object {
        fun fromJson(json: JSONObject): SiteProfile = SiteProfile(
            domain = json.getString("domain"),
            userAgent = try {
                UserAgentMode.valueOf(json.optString("userAgent", "GLOBAL"))
            } catch (_: IllegalArgumentException) { UserAgentMode.GLOBAL },
            forceMenuFix = json.optBoolean("forceMenuFix", false),
            skipLazyLoader = json.optBoolean("skipLazyLoader", false),
            allowAutoplay = json.optBoolean("allowAutoplay", false),
            disableAdBlock = json.optBoolean("disableAdBlock", false),
            customCss = json.optString("customCss", ""),
            customJs = json.optString("customJs", ""),
        )
    }
}
