package com.example.printedit.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("print_edit_settings", Context.MODE_PRIVATE)

    // Keys
    private val KEY_AGGRESSIVE_AD_BLOCK = "aggressive_ad_block"
    private val KEY_MENU_FIX = "menu_fix"
    private val KEY_AUTO_IMAGE_ADJUST = "auto_image_adjust"
    private val KEY_DESKTOP_MODE = "desktop_mode"
    private val KEY_MENU_ACTIONS = "menu_actions"

    // Default enabled actions
    private val defaultMenuActions = setOf(
        "action_remove_ads",
        "action_presets",
        "action_adjust_images",
        "action_remove_elements",
        "action_undo",
        "action_text_only",
        "action_grayscale",
        "action_remove_background"
    )

    // Toggles
    var aggressiveAdBlock: Boolean
        get() = prefs.getBoolean(KEY_AGGRESSIVE_AD_BLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_AGGRESSIVE_AD_BLOCK, value).apply()

    var menuFixEnabled: Boolean
        get() = prefs.getBoolean(KEY_MENU_FIX, true)
        set(value) = prefs.edit().putBoolean(KEY_MENU_FIX, value).apply()

    var autoImageAdjust: Boolean
        get() = prefs.getBoolean(KEY_AUTO_IMAGE_ADJUST, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_IMAGE_ADJUST, value).apply()

    var desktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_MODE, value).apply()

    // Menu Customization
    var menuActions: Set<String>
        get() = prefs.getStringSet(KEY_MENU_ACTIONS, defaultMenuActions) ?: defaultMenuActions
        set(value) = prefs.edit().putStringSet(KEY_MENU_ACTIONS, value).apply()
}
