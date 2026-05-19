package com.example.medicinreminder.data.model

data class OpenFDAResponse(
    val results: List<OpenFDADrug>? = null,
    val error: OpenFDAError? = null
)

data class OpenFDADrug(
    val openfda: OpenFDAInfo? = null,
    val brand_name: List<String>? = null,
    val generic_name: List<String>? = null,
    val route: List<String>? = null,
    val purpose: List<String>? = null,
    val dosage_and_administration: List<String>? = null,
    val storage_and_handling: List<String>? = null,
    val warnings: List<String>? = null,
    val adverse_reactions: List<String>? = null
)

data class OpenFDAInfo(
    val brand_name: List<String>? = null,
    val generic_name: List<String>? = null,
    val spl_id: List<String>? = null,
    val manufacturer_name: List<String>? = null
)

data class OpenFDAError(
    val code: String? = null,
    val message: String? = null
)
