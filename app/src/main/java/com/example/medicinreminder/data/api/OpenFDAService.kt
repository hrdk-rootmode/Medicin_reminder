package com.example.medicinreminder.data.api

import com.example.medicinreminder.data.model.OpenFDAResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenFDAService {
    // Use drug/label.json for more complete medicine information
    @GET("drug/label.json")
    suspend fun searchDrugsLabel(
        @Query("search") query: String,
        @Query("api_key") apiKey: String,
        @Query("sort") sort: String = "effective_time:desc",
        @Query("limit") limit: Int = 10
    ): OpenFDAResponse

    // Fallback endpoint
    @GET("drug/drugsfda.json")
    suspend fun searchDrugsFDA(
        @Query("search") query: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 5
    ): OpenFDAResponse
}
