package com.example.medicinreminder.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.example.medicinreminder.data.model.MedicineNameSuggestion
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object IndiaMedicinesProvider {
    private const val TAG = "IndiaMedicinesProvider"
    private const val ASSET_DB = "india_medicines.db"
    private const val DEST_DIR = "medicine_assets"
    private var db: SQLiteDatabase? = null

    private fun normalize(s: String): String {
        return s.lowercase()
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun ensureDatabaseCopied(context: Context): File? {
        try {
            val destDir = File(context.filesDir, DEST_DIR)
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, ASSET_DB)
            if (!destFile.exists()) {
                context.assets.open(ASSET_DB).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Copied $ASSET_DB to ${destFile.absolutePath}")
            }
            return destFile
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy asset DB: ${e.message}")
            return null
        }
    }

    private fun openDb(dbFile: File): SQLiteDatabase? {
        return try {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Log.w(TAG, "openDb failed: ${e.message}")
            null
        }
    }

    fun getDatabase(context: Context): SQLiteDatabase? {
        if (db != null && db!!.isOpen) return db
        val file = ensureDatabaseCopied(context) ?: return null
        db = openDb(file)
        return db
    }

    fun query(context: Context, q: String, limit: Int = 10): List<MedicineNameSuggestion> {
        val trimmed = q.trim()
        if (trimmed.length < 2) return emptyList()
        val db = getDatabase(context) ?: return emptyList()
        val norm = normalize(trimmed)
        val likeArg = "$norm%"
        val sql = "SELECT name, brand, generic, strength FROM medicines WHERE normalized LIKE ? LIMIT ?"
        val results = mutableListOf<MedicineNameSuggestion>()
        try {
            val cursor = db.rawQuery(sql, arrayOf(likeArg, limit.toString()))
            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(0) ?: ""
                    val brand = it.getString(1) ?: ""
                    val generic = it.getString(2) ?: ""
                    val strength = it.getString(3) ?: ""
                    val dosageHint = strength.ifBlank { "" }
                    results += MedicineNameSuggestion(name = name, dosageHint = dosageHint, source = "IndiaDB")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query failed: ${e.message}")
        }
        return results
    }
}
