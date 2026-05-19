package com.example.medicinreminder.data.repository

import android.content.Context
import android.util.Log
import com.example.medicinreminder.data.model.MedicineInfo
import com.example.medicinreminder.data.model.MedicineNameSuggestion
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray

interface MedicineInfoRepository {
    suspend fun lookup(query: String): MedicineInfo?
    suspend fun suggestNames(query: String, limit: Int = 8): List<MedicineNameSuggestion>
}

class LocalMedicineInfoRepository(
    private val context: Context,
    private val remoteMedicineRepository: RemoteMedicineRepository? = null
) : MedicineInfoRepository {
    private data class Entry(
        val displayName: String,
        val aliases: List<String>,
        val info: MedicineInfo
    )

    private val supportedMedicines: List<Entry> = loadEntries()

    override suspend fun lookup(query: String): MedicineInfo? {
        val normalized = normalize(query)
        if (normalized.isBlank()) return null

        // Check remote cache first if available
        remoteMedicineRepository?.let { repo ->
            try {
                val remoteMedicine = repo.getMedicineById(normalized)
                    ?: repo.searchMedicines(query).firstOrNull()?.firstOrNull()
                if (remoteMedicine != null) {
                    return MedicineInfo(
                        displayName = remoteMedicine.displayName,
                        commonUses = remoteMedicine.commonUses.split("\n").filter { it.isNotBlank() },
                        commonSideEffects = remoteMedicine.commonSideEffects.split("\n").filter { it.isNotBlank() },
                        warnings = remoteMedicine.warnings.split("\n").filter { it.isNotBlank() },
                        storageGuidance = remoteMedicine.storageGuidance,
                        sourceName = remoteMedicine.sourceName,
                        disclaimer = remoteMedicine.disclaimer
                    )
                }
            } catch (e: Exception) {
                // If remote lookup fails, continue to local fallback
                Log.d("MedicineInfoRepository", "Remote lookup failed, using local fallback: ${e.message}")
            }
        }

        // Fallback to local dataset
        val exactMatch = supportedMedicines.firstOrNull { entry ->
            val candidateKeys = listOf(entry.displayName) + entry.aliases
            candidateKeys.any { normalize(it) == normalized }
        }
        if (exactMatch != null) return exactMatch.info

        val containsMatch = supportedMedicines.firstOrNull { entry ->
            val candidateKeys = listOf(entry.displayName) + entry.aliases
            candidateKeys.any { key ->
                val normalizedKey = normalize(key)
                normalized.contains(normalizedKey) || normalizedKey.contains(normalized)
            }
        }
        if (containsMatch != null) return containsMatch.info

        val tokenized = normalized.split(" ", ",", "+", "-", "(", ")", "/")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return supportedMedicines.firstOrNull { entry ->
            val candidateKeys = listOf(entry.displayName) + entry.aliases
            candidateKeys.any { key ->
                val normalizedKey = normalize(key)
                tokenized.any { token -> token.length >= 4 && normalizedKey.contains(token) }
            }
        }?.info
    }

    override suspend fun suggestNames(query: String, limit: Int): List<MedicineNameSuggestion> {
        val normalized = normalize(query)
        if (normalized.length < 2) return emptyList()

        val localSuggestions = buildList {
            supportedMedicines.forEach { entry ->
                val candidateKeys = listOf(entry.displayName) + entry.aliases
                val score = candidateKeys.maxOfOrNull { key -> scoreMatch(normalized, normalize(key)) } ?: 0
                if (score > 0) {
                    add(
                        Triple(
                            entry.displayName,
                            score,
                            entry.info.commonUses.firstOrNull().orEmpty()
                        )
                    )
                }
            }
        }
            .sortedByDescending { it.second }
            .map { MedicineNameSuggestion(name = it.first, source = "Local") }

        val remoteSuggestions = remoteMedicineRepository
            ?.searchMedicines(query)
            ?.firstOrNull()
            .orEmpty()
            .flatMap { entity ->
                buildList {
                    if (entity.displayName.isNotBlank()) {
                        add(
                            MedicineNameSuggestion(
                                name = entity.displayName,
                                dosageHint = extractDosageHint(entity),
                                source = "Cached"
                            )
                        )
                    }
                    entity.brandNames
                        .split(',')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                        .forEach { brand ->
                            add(
                                MedicineNameSuggestion(
                                    name = brand,
                                    dosageHint = extractDosageHint(entity),
                                    source = "Cached"
                                )
                            )
                        }
                }
            }

        return (localSuggestions + remoteSuggestions)
            .filter { scoreMatch(normalized, normalize(it.name)) > 0 }
            .sortedByDescending { scoreMatch(normalized, normalize(it.name)) }
            .distinctBy { normalize(it.name) }
            .take(limit)
    }

    private fun loadEntries(): List<Entry> {
        val assetEntries = runCatching {
            context.assets.open("medicine_info.json").bufferedReader().use { reader ->
                parseEntries(JSONArray(reader.readText()))
            }
        }.getOrNull()

        if (!assetEntries.isNullOrEmpty()) return assetEntries

        return defaultEntries()
    }

    private fun parseEntries(array: JSONArray): List<Entry> {
        val results = mutableListOf<Entry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val displayName = item.optString("displayName").trim()
            if (displayName.isBlank()) continue

            val aliases = buildList {
                val aliasArray = item.optJSONArray("aliases")
                if (aliasArray != null) {
                    for (aliasIndex in 0 until aliasArray.length()) {
                        val alias = aliasArray.optString(aliasIndex).trim()
                        if (alias.isNotBlank()) add(alias)
                    }
                }
            }

            results += Entry(
                displayName = displayName,
                aliases = aliases,
                info = MedicineInfo(
                    displayName = displayName,
                    commonUses = readStringList(item.optJSONArray("commonUses")),
                    commonSideEffects = readStringList(item.optJSONArray("commonSideEffects")),
                    warnings = readStringList(item.optJSONArray("warnings")),
                    storageGuidance = item.optString("storageGuidance").ifBlank { "Follow the product label or pharmacist guidance." },
                    sourceName = item.optString("sourceName").ifBlank { "Local trusted dataset" },
                    disclaimer = item.optString("disclaimer").ifBlank { "General information only. Confirm with your doctor or pharmacist before use." }
                )
            )
        }
        return results
    }

    private fun readStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) values += value
        }
        return values
    }

    private fun defaultEntries(): List<Entry> = listOf(
        Entry(
            displayName = "Paracetamol",
            aliases = listOf("acetaminophen", "tylenol", "crocin"),
            info = MedicineInfo(
                displayName = "Paracetamol",
                commonUses = listOf("Fever reduction", "Mild to moderate pain relief"),
                commonSideEffects = listOf("Nausea", "Rash", "Rare liver irritation if overdosed"),
                warnings = listOf("Do not exceed the recommended daily dose", "Ask a doctor if liver disease is present"),
                storageGuidance = "Store in a cool, dry place away from direct sunlight.",
                sourceName = "NIH MedlinePlus and FDA label references",
                disclaimer = "General information only. Confirm with your doctor or pharmacist before use."
            )
        ),
        Entry(
            displayName = "Ibuprofen",
            aliases = listOf("advil", "motrin"),
            info = MedicineInfo(
                displayName = "Ibuprofen",
                commonUses = listOf("Pain relief", "Inflammation reduction", "Fever reduction"),
                commonSideEffects = listOf("Stomach upset", "Heartburn", "Dizziness"),
                warnings = listOf("Take with food if possible", "Avoid if you have a history of stomach ulcer unless prescribed"),
                storageGuidance = "Keep tightly closed at room temperature.",
                sourceName = "DailyMed and NHS medicines guidance",
                disclaimer = "General information only. Confirm with your doctor or pharmacist before use."
            )
        ),
        Entry(
            displayName = "Amoxicillin",
            aliases = listOf("amoxil"),
            info = MedicineInfo(
                displayName = "Amoxicillin",
                commonUses = listOf("Bacterial infection treatment"),
                commonSideEffects = listOf("Diarrhea", "Nausea", "Rash"),
                warnings = listOf("Complete the full prescribed course", "Seek help for allergic reaction signs"),
                storageGuidance = "Store at room temperature as directed on the label.",
                sourceName = "DailyMed and MedlinePlus",
                disclaimer = "General information only. Confirm with your doctor or pharmacist before use."
            )
        )
    )

    private fun normalize(value: String): String {
        return value.lowercase()
            .trim()
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
    }

    private fun scoreMatch(query: String, candidate: String): Int {
        if (query.isBlank() || candidate.isBlank()) return 0
        return when {
            candidate == query -> 120
            candidate.startsWith(query) -> 100
            candidate.contains(" $query") -> 85
            candidate.contains(query) -> 70
            query.split(" ").any { token -> token.length >= 3 && candidate.contains(token) } -> 45
            else -> 0
        }
    }

    private fun extractDosageHint(entity: com.example.medicinreminder.data.entity.RemoteMedicineEntity): String {
        val dosageRegex = Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s?(mg|mcg|g|ml|iu)\\b")
        val combined = listOf(
            entity.dosageAndAdministration,
            entity.commonUses,
            entity.commonSideEffects,
            entity.displayName
        ).joinToString("\n")
        return dosageRegex.find(combined)?.value.orEmpty()
    }
}