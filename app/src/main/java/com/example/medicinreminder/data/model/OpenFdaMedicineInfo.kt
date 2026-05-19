package com.example.medicinreminder.data.model

data class OpenFdaMedicineInfo(
    val displayName: String,
    val purpose: String = "",
    val warnings: String = "",
    val dosageAndAdministration: String = "",
    val adverseReactions: String = "",
    val storageAndHandling: String = "",
    val sourceName: String = "OpenFDA",
    val lastUpdated: Long = System.currentTimeMillis()
)