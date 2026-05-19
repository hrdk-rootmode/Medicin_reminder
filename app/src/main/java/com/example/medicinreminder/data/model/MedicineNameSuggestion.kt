package com.example.medicinreminder.data.model

data class MedicineNameSuggestion(
    val name: String,
    val dosageHint: String = "",
    val source: String = ""
)