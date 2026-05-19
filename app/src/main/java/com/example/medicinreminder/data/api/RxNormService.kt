package com.example.medicinreminder.data.api

import com.example.medicinreminder.data.model.RxNormResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface RxNormService {
    @GET("REST/approximateTerm.json")
    suspend fun approximateTerm(
        @Query("term") term: String,
        @Query("maxEntries") maxEntries: Int = 12,
        @Query("option") option: Int = 1
    ): RxNormResponse
}