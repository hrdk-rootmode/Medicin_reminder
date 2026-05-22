package com.example.medicinreminder.data.api

import android.util.Log
import com.example.medicinreminder.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object OpenFDAClient {
    private const val BASE_URL = "https://api.fda.gov/"
    private const val TAG = "OpenFDAClient"

    val apiKey: String
        get() = BuildConfig.OPEN_FDA_API_KEY.trim()
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    fun getService(): OpenFDAService {
        return retrofit.create(OpenFDAService::class.java)
    }

    fun isConnectedToInternet(context: android.content.Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || 
                            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR))
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connectivity: ${e.message}")
            false
        }
    }
}
