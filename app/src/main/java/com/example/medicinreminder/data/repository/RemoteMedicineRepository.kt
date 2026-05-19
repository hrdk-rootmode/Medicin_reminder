package com.example.medicinreminder.data.repository

import android.content.Context
import com.example.medicinreminder.data.api.OpenFDAClient
import com.example.medicinreminder.data.api.RxNormClient
import com.example.medicinreminder.data.dao.RemoteMedicineDao
import com.example.medicinreminder.data.entity.RemoteMedicineEntity
import com.example.medicinreminder.data.model.MedicineNameSuggestion
import com.example.medicinreminder.data.model.OpenFDADrug
import com.example.medicinreminder.data.model.OpenFdaMedicineInfo
import com.example.medicinreminder.data.model.RxNormCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import com.example.medicinreminder.data.local.IndiaMedicinesProvider

class RemoteMedicineRepository(private val remoteMedicineDao: RemoteMedicineDao) {
    private val dosageHintRegex = Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s?(mg|mcg|g|ml|iu)\\b")

    fun getAllRemoteMedicines(): Flow<List<RemoteMedicineEntity>> {
        return remoteMedicineDao.getAllRemoteMedicines()
    }

    fun searchMedicines(query: String): Flow<List<RemoteMedicineEntity>> {
        return remoteMedicineDao.searchMedicines(query)
    }

    suspend fun getMedicineById(medicineId: String): RemoteMedicineEntity? {
        return withContext(Dispatchers.IO) {
            remoteMedicineDao.getMedicineById(medicineId)
        }
    }

    suspend fun getCachedOpenFdaMedicineInfo(query: String): OpenFdaMedicineInfo? {
        return withContext(Dispatchers.IO) {
            val normalizedId = normalizeQuery(query)
            val byId = remoteMedicineDao.getMedicineById(normalizedId)
            if (byId != null) return@withContext byId.toOpenFdaMedicineInfo()

            val bySearch = remoteMedicineDao.searchMedicines(query).firstOrNull()?.firstOrNull()
            bySearch?.toOpenFdaMedicineInfo()
        }
    }

    suspend fun fetchOpenFdaMedicineInfo(query: String): OpenFdaMedicineInfo? {
        return withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext null

            val service = OpenFDAClient.getService()
            val searchVariants = listOf(
                "openfda.generic_name:\"$trimmed\"",
                "openfda.brand_name:\"$trimmed\"",
                "openfda.substance_name:\"$trimmed\"",
                "purpose:\"$trimmed\""
            )

            for (searchQuery in searchVariants) {
                val response = runCatching {
                    service.searchDrugsLabel(
                        query = searchQuery,
                        apiKey = OpenFDAClient.API_KEY,
                        sort = "effective_time:desc",
                        limit = 1
                    )
                }.getOrNull() ?: continue

                if (response.error != null) continue

                val drug = response.results?.firstOrNull() ?: continue
                val medicineInfo = drug.toOpenFdaMedicineInfo(trimmed)
                cacheOpenFdaMedicineInfo(trimmed, medicineInfo)
                return@withContext medicineInfo
            }

            getCachedOpenFdaMedicineInfo(query)
        }
    }

    suspend fun fetchOpenFdaSuggestions(query: String, limit: Int = 8): List<MedicineNameSuggestion> {
        return withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val service = OpenFDAClient.getService()
            val searchVariants = listOf(
                "openfda.generic_name:\"$trimmed\"",
                "openfda.brand_name:\"$trimmed\"",
                "openfda.substance_name:\"$trimmed\""
            )

            val results = mutableListOf<MedicineNameSuggestion>()

            for (searchQuery in searchVariants) {
                val response = runCatching {
                    service.searchDrugsLabel(
                        query = searchQuery,
                        apiKey = OpenFDAClient.API_KEY,
                        sort = "effective_time:desc",
                        limit = limit
                    )
                }.getOrNull() ?: continue

                if (response.error != null) continue

                response.results.orEmpty().forEach { drug ->
                    results += drug.toNameSuggestions()
                }

                if (results.size >= limit * 2) break
            }

            results
                .filter { it.name.isNotBlank() }
                .distinctBy { it.name.lowercase() }
                .take(limit)
        }
    }

    suspend fun fetchGlobalMedicineSuggestions(query: String, limit: Int = 10): List<MedicineNameSuggestion> {
        return withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val suggestions = mutableListOf<MedicineNameSuggestion>()

            runCatching {
                fetchOpenFdaSuggestions(trimmed, limit)
            }.getOrDefault(emptyList())
                .forEach { suggestions += it }

            runCatching {
                RxNormClient.getService().approximateTerm(term = trimmed, maxEntries = limit, option = 1)
            }.getOrNull()
                ?.approximateGroup
                ?.candidate
                .orEmpty()
                .mapNotNull { it.toMedicineSuggestion() }
                .forEach { suggestions += it }

            suggestions
                .filter { it.name.isNotBlank() }
                .distinctBy { it.name.lowercase() }
                .take(limit)
        }
    }

    fun getSuggestions(context: Context, query: String, limit: Int = 10): Flow<List<MedicineNameSuggestion>> = flow {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            emit(emptyList())
            return@flow
        }

        val suggestions = mutableListOf<MedicineNameSuggestion>()

        // 1) Local India FTS DB (instant, offline)
        val local = runCatching { IndiaMedicinesProvider.query(context, trimmed, limit) }.getOrDefault(emptyList())
        suggestions += local

        // 2) Online RxNorm/RxTerms fallback (only if connected)
        if (OpenFDAClient.isConnectedToInternet(context)) {
            val online = runCatching {
                RxNormClient.getService().approximateTerm(term = trimmed, maxEntries = limit, option = 1)
            }.getOrNull()

            val onlineCandidates = online
                ?.approximateGroup
                ?.candidate
                .orEmpty()
                .mapNotNull { it.toMedicineSuggestion() }

            // Add only those not already present (preserve local-first ordering)
            onlineCandidates.filter { oc -> suggestions.none { it.name.equals(oc.name, true) } }
                .forEach { suggestions += it }
        }

        // Emit merged list (local results already first)
        emit(suggestions.take(limit))
    }

    private suspend fun cacheOpenFdaMedicineInfo(query: String, medicineInfo: OpenFdaMedicineInfo) {
        val entity = RemoteMedicineEntity(
            id = normalizeQuery(query),
            displayName = medicineInfo.displayName,
            purpose = medicineInfo.purpose,
            dosageAndAdministration = medicineInfo.dosageAndAdministration,
            adverseReactions = medicineInfo.adverseReactions,
            storageAndHandling = medicineInfo.storageAndHandling,
            commonUses = medicineInfo.purpose,
            commonSideEffects = medicineInfo.adverseReactions,
            warnings = medicineInfo.warnings,
            storageGuidance = medicineInfo.storageAndHandling,
            sourceName = medicineInfo.sourceName,
            disclaimer = "Cached OpenFDA medicine information.",
            lastUpdated = medicineInfo.lastUpdated
        )
        remoteMedicineDao.insertMedicine(entity)
    }

    private fun RemoteMedicineEntity.toOpenFdaMedicineInfo(): OpenFdaMedicineInfo {
        return OpenFdaMedicineInfo(
            displayName = displayName,
            purpose = purpose.ifBlank { commonUses },
            warnings = warnings,
            dosageAndAdministration = dosageAndAdministration,
            adverseReactions = adverseReactions.ifBlank { commonSideEffects },
            storageAndHandling = storageAndHandling.ifBlank { storageGuidance },
            sourceName = sourceName,
            lastUpdated = lastUpdated
        )
    }

    private fun OpenFDADrug.toOpenFdaMedicineInfo(fallbackName: String): OpenFdaMedicineInfo {
        val displayName = openfda?.generic_name?.firstOrNull()
            ?: generic_name?.firstOrNull()
            ?: fallbackName

        return OpenFdaMedicineInfo(
            displayName = displayName,
            purpose = purpose?.joinToString("\n").orEmpty(),
            warnings = warnings?.joinToString("\n").orEmpty(),
            dosageAndAdministration = dosage_and_administration?.joinToString("\n").orEmpty(),
            adverseReactions = adverse_reactions?.joinToString("\n").orEmpty(),
            storageAndHandling = storage_and_handling?.joinToString("\n").orEmpty(),
            sourceName = "OpenFDA",
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun OpenFDADrug.toNameSuggestions(): List<MedicineNameSuggestion> {
        val dosageHint = dosageHintRegex
            .find(dosage_and_administration?.joinToString(" ").orEmpty())
            ?.value
            .orEmpty()

        val names = buildList {
            openfda?.brand_name.orEmpty().forEach { add(it) }
            openfda?.generic_name.orEmpty().forEach { add(it) }
            brand_name.orEmpty().forEach { add(it) }
            generic_name.orEmpty().forEach { add(it) }
        }
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinct()

        return names.map { name ->
            MedicineNameSuggestion(
                name = name,
                dosageHint = dosageHint,
                source = "OpenFDA"
            )
        }
    }

    private fun RxNormCandidate.toMedicineSuggestion(): MedicineNameSuggestion? {
        val nameValue = name?.trim().orEmpty()
        if (nameValue.isBlank()) return null
        return MedicineNameSuggestion(
            name = nameValue,
            dosageHint = extractDosageFromName(nameValue),
            source = "RxNorm"
        )
    }

    private fun extractDosageFromName(value: String): String {
        return dosageHintRegex.find(value)?.value.orEmpty()
    }

    private fun normalizeQuery(value: String): String {
        return value.lowercase()
            .trim()
            .replace(Regex("[^a-z0-9]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    suspend fun insertMedicines(medicines: List<RemoteMedicineEntity>): List<Long> {
        return withContext(Dispatchers.IO) {
            remoteMedicineDao.insertMedicines(medicines)
        }
    }

    suspend fun getMedicineCount(): Int {
        return withContext(Dispatchers.IO) {
            remoteMedicineDao.getMedicineCount()
        }
    }

    suspend fun clearAllMedicines() {
        withContext(Dispatchers.IO) {
            remoteMedicineDao.deleteAll()
        }
    }
}
