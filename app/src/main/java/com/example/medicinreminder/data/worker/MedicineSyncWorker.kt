package com.example.medicinreminder.data.worker

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.medicinreminder.data.api.OpenFDAClient
import com.example.medicinreminder.data.entity.RemoteMedicineEntity
import com.example.medicinreminder.data.repository.AppContainer
import com.example.medicinreminder.notifications.TimeOfDayPeriod

class MedicineSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val tag = "MedicineSyncWorker"

    override suspend fun doWork(): Result {
        Log.d(tag, "Starting medicine sync...")
        val appContainer = AppContainer(applicationContext)

        if (!OpenFDAClient.isConnectedToInternet(applicationContext)) {
            Log.w(tag, "No internet connection, skipping sync")
            return Result.retry()
        }

        return try {
            val service = OpenFDAClient.getService()

            val commonDrugs = listOf(
                "paracetamol", "acetaminophen", "ibuprofen", "aspirin",
                "amoxicillin", "lisinopril", "amlodipine", "metformin",
                "levothyroxine", "cetirizine", "loratadine", "omeprazole",
                "atorvastatin", "sertraline", "salbutamol", "albuterol",
                "diclofenac", "vitamin B12", "vitamin D3", "calcium"
            )

            val remoteMedicines = mutableListOf<RemoteMedicineEntity>()
            var successCount = 0
            var failureCount = 0

            for (drug in commonDrugs) {
                try {
                    Log.d(tag, "Fetching data for: $drug")
                    val searchVariants = listOf(
                        "openfda.generic_name:\"$drug\"",
                        "openfda.brand_name:\"$drug\"",
                        "openfda.substance_name:\"$drug\"",
                        "purpose:\"$drug\""
                    )

                    var fetchedForDrug = false
                    for (searchQuery in searchVariants) {
                        val response = service.searchDrugsLabel(
                            query = searchQuery,
                            apiKey = OpenFDAClient.apiKey.takeIf { it.isNotBlank() },
                            limit = 10
                        )

                        if (response.error != null) {
                            Log.w(tag, "API error for $drug using $searchQuery: ${response.error.message}")
                            continue
                        }

                        val mapped = response.results.orEmpty().mapNotNull { drugData ->
                            val genericName = drugData.openfda?.generic_name?.firstOrNull()
                                ?: drugData.generic_name?.firstOrNull()
                                ?: drug
                            val brandNames = (drugData.openfda?.brand_name ?: drugData.brand_name)?.joinToString(", ") ?: ""
                            val warnings = drugData.warnings?.joinToString("\n").orEmpty()
                            val sideEffects = drugData.adverse_reactions?.joinToString("\n").orEmpty()

                            if (genericName.isBlank()) return@mapNotNull null

                            RemoteMedicineEntity(
                                id = genericName.lowercase().replace(" ", "_").replace("/", "_").replace("-", "_"),
                                displayName = genericName,
                                brandNames = brandNames,
                                commonUses = "FDA-approved medicine; use depends on the product label and your prescription.",
                                commonSideEffects = sideEffects.ifBlank { "See product label" },
                                warnings = warnings.ifBlank { "Always consult healthcare provider before use" },
                                storageGuidance = "Store at room temperature unless directed otherwise.",
                                sourceName = "OpenFDA - Official FDA Drug Database (https://api.fda.gov)",
                                disclaimer = "This information is from the official FDA database. Always consult your healthcare provider or pharmacist before use.",
                                lastUpdated = System.currentTimeMillis()
                            )
                        }

                        if (mapped.isNotEmpty()) {
                            remoteMedicines.addAll(mapped)
                            fetchedForDrug = true
                            break
                        }
                    }

                    if (fetchedForDrug) {
                        successCount++
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error fetching $drug: ${e.message}", e)
                    failureCount++
                }
            }

            Log.d(tag, "Sync complete: $successCount medicines fetched, $failureCount failures")

            if (remoteMedicines.isNotEmpty()) {
                appContainer.remoteMedicineRepository.insertMedicines(remoteMedicines)
                Log.d(tag, "Successfully synced ${remoteMedicines.size} medicines from OpenFDA and cached locally")
                Result.success()
            } else {
                Log.w(tag, "No medicines were synced from OpenFDA")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(tag, "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, TimeOfDayPeriod.MORNING.channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(com.example.medicinreminder.R.string.syncing_medicines))
            .setContentText(applicationContext.getString(com.example.medicinreminder.R.string.syncing_medicines_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(9998, notification)
    }
}
