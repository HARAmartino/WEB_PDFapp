package com.example.printedit.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class Preset(
    val name: String,
    val selectors: List<String>,
    val imageAdjusted: Boolean = false,
    val textOnly: Boolean = false,
    val grayscale: Boolean = false,
    val removeBackground: Boolean = false,
    val adsRemoved: Boolean = false
)

class PresetRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("print_edit_presets", Context.MODE_PRIVATE)
    private val presetsKey = "saved_presets"

    fun savePreset(
        name: String, 
        selectors: List<String>, 
        imageAdjusted: Boolean,
        textOnly: Boolean,
        grayscale: Boolean,
        removeBackground: Boolean,
        adsRemoved: Boolean = false
    ) {
        val currentPresets = getPresets().toMutableList()
        // Update existing if name matches, or add new
        val existingIndex = currentPresets.indexOfFirst { it.name == name }
        val newPreset = Preset(name, selectors, imageAdjusted, textOnly, grayscale, removeBackground, adsRemoved)
        
        if (existingIndex >= 0) {
            currentPresets[existingIndex] = newPreset
        } else {
            currentPresets.add(newPreset)
        }
        savePresetsList(currentPresets)
    }

    fun getPresets(): List<Preset> {
        val jsonString = prefs.getString(presetsKey, null) ?: return emptyList()
        val presets = mutableListOf<Preset>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val selectorsArray = obj.getJSONArray("selectors")
                val selectors = mutableListOf<String>()
                for (j in 0 until selectorsArray.length()) {
                    selectors.add(selectorsArray.getString(j))
                }
                
                // Fallback for old presets
                val imageAdjusted = if (obj.has("imageAdjusted")) obj.getBoolean("imageAdjusted") else false
                val textOnly = if (obj.has("textOnly")) obj.getBoolean("textOnly") else false
                val grayscale = if (obj.has("grayscale")) obj.getBoolean("grayscale") else false
                val removeBackground = if (obj.has("removeBackground")) obj.getBoolean("removeBackground") else false
                val adsRemoved = if (obj.has("adsRemoved")) obj.getBoolean("adsRemoved") else false
                
                presets.add(Preset(name, selectors, imageAdjusted, textOnly, grayscale, removeBackground, adsRemoved))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return presets
    }

    fun deletePreset(name: String) {
        val currentPresets = getPresets().toMutableList()
        currentPresets.removeIf { it.name == name }
        savePresetsList(currentPresets)
    }

    private fun savePresetsList(presets: List<Preset>) {
        val jsonArray = JSONArray()
        presets.forEach { preset ->
            val obj = JSONObject()
            obj.put("name", preset.name)
            obj.put("imageAdjusted", preset.imageAdjusted)
            obj.put("textOnly", preset.textOnly)
            obj.put("grayscale", preset.grayscale)
            obj.put("removeBackground", preset.removeBackground)
            obj.put("adsRemoved", preset.adsRemoved)
            
            val selectorsArray = JSONArray()
            preset.selectors.forEach { selectorsArray.put(it) }
            obj.put("selectors", selectorsArray)
            jsonArray.put(obj)
        }
        prefs.edit().putString(presetsKey, jsonArray.toString()).apply()
    }
}
