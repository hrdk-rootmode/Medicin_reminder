package com.example.medicinreminder.data.model

data class RxNormResponse(
    val approximateGroup: RxNormApproximateGroup? = null
)

data class RxNormApproximateGroup(
    val candidate: List<RxNormCandidate>? = null
)

data class RxNormCandidate(
    val rxcui: String? = null,
    val score: String? = null,
    val rank: String? = null,
    val name: String? = null,
    val source: String? = null
)