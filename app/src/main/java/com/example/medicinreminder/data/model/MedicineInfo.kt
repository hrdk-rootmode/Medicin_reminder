package com.example.medicinreminder.data.model

data class MedicineInfo(
    val displayName: String,
    val commonUses: List<String>,
    val commonSideEffects: List<String>,
    val warnings: List<String>,
    val storageGuidance: String,
    val sourceName: String,
    val disclaimer: String
)