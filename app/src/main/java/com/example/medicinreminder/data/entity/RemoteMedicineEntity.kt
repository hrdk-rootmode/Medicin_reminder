package com.example.medicinreminder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_medicines")
data class RemoteMedicineEntity(
    @PrimaryKey
    val id: String, // Generic name or primary identifier from API
    val displayName: String,
    val brandNames: String = "", // Comma-separated
    val purpose: String = "",
    val dosageAndAdministration: String = "",
    val adverseReactions: String = "",
    val storageAndHandling: String = "",
    val commonUses: String = "", // Newline-separated
    val commonSideEffects: String = "", // Newline-separated
    val warnings: String = "", // Newline-separated
    val storageGuidance: String = "",
    val sourceName: String = "OpenFDA",
    val disclaimer: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
