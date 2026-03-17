package com.example.printedit.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SavedUrl(
    val url: String,
    val title: String,
    val savedAt: Long = System.currentTimeMillis()
)

class SavedUrlRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("print_edit_saved_urls", Context.MODE_PRIVATE)
    private val key = "saved_urls"

    fun save(url: String, title: String) {
        val current = getAll().toMutableList()
        // 重複チェック：同じ URL があれば上書き
        current.removeAll { it.url == url }
        current.add(0, SavedUrl(url, title))
        persist(current)
    }

    fun getAll(): List<SavedUrl> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val list = mutableListOf<SavedUrl>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedUrl(
                        url = obj.getString("url"),
                        title = obj.optString("title", ""),
                        savedAt = obj.optLong("savedAt", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun delete(url: String) {
        val current = getAll().toMutableList()
        current.removeAll { it.url == url }
        persist(current)
    }

    fun deleteAll() {
        prefs.edit().remove(key).apply()
    }

    private fun persist(list: List<SavedUrl>) {
        val arr = JSONArray()
        list.forEach { item ->
            val obj = JSONObject()
            obj.put("url", item.url)
            obj.put("title", item.title)
            obj.put("savedAt", item.savedAt)
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
