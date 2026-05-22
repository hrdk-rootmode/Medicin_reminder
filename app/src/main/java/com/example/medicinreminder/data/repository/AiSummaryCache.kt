package com.example.medicinreminder.data.repository

import android.content.Context
import com.example.medicinreminder.data.model.MedicineInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AiSummaryCache(private val context: Context) {
    private val dir: File
        get() = File(context.filesDir, "ai_summaries").apply { if (!exists()) mkdirs() }

    private fun key(id: String, languageTag: String) = "${id}_${languageTag}.json"

    fun save(id: String, languageTag: String, info: MedicineInfo) {
        val file = File(dir, key(id, languageTag))
        val json = JSONObject().apply {
            put("displayName", info.displayName)
            put("commonUses", JSONArray(info.commonUses))
            put("commonSideEffects", JSONArray(info.commonSideEffects))
            put("warnings", JSONArray(info.warnings))
            put("storageGuidance", info.storageGuidance)
            put("sourceName", info.sourceName)
            put("disclaimer", info.disclaimer)
        }
        file.writeText(json.toString())
    }

    fun load(id: String, languageTag: String): MedicineInfo? {
        val file = File(dir, key(id, languageTag))
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            MedicineInfo(
                displayName = json.optString("displayName").ifBlank { "" },
                commonUses = json.optJSONArray("commonUses")?.toList() ?: emptyList(),
                commonSideEffects = json.optJSONArray("commonSideEffects")?.toList() ?: emptyList(),
                warnings = json.optJSONArray("warnings")?.toList() ?: emptyList(),
                storageGuidance = json.optString("storageGuidance").orEmpty(),
                sourceName = json.optString("sourceName").orEmpty(),
                disclaimer = json.optString("disclaimer").orEmpty()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun org.json.JSONArray.toList(): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until this.length()) {
            val v = this.optString(i).trim()
            if (v.isNotBlank()) out += v
        }
        return out
    }
}
