package jp.webpdf.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class PdfHistoryEntry(
    val url: String,
    val title: String,
    val savedAt: Long = System.currentTimeMillis(),
)

class PdfHistoryRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("webpdf_pdf_history", Context.MODE_PRIVATE)
    private val key = "pdf_history"

    fun add(url: String, title: String) {
        val current = getAll().toMutableList()
        // 同じ URL は先頭に移動（重複除去）
        current.removeAll { it.url == url }
        current.add(0, PdfHistoryEntry(url, title))
        persist(current)
    }

    fun getAll(): List<PdfHistoryEntry> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val list = mutableListOf<PdfHistoryEntry>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    PdfHistoryEntry(
                        url = obj.getString("url"),
                        title = obj.optString("title", ""),
                        savedAt = obj.optLong("savedAt", 0L),
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

    private fun persist(list: List<PdfHistoryEntry>) {
        val arr = JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("url", entry.url)
            obj.put("title", entry.title)
            obj.put("savedAt", entry.savedAt)
            arr.put(obj)
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
