package com.example.medicinreminder.data.ocr

data class MedicineOcrSuggestion(
    val title: String = "",
    val dosageText: String = "",
    val timingHint: String = "",
    val timeHint: String = "",
    val batchOrExpiry: String = "",
    val isHighConfidence: Boolean = false
)

object MedicineOcrParser {
    private val dosagePattern = Regex("(?i)\\b\\d+(?:\\.\\d+)?\\s?(mg|mcg|g|ml|iu|tablet|tablets|capsule|capsules|drop|drops|syrup|dose)\\b")
    private val timingPattern = Regex("(?i)\\b(once daily|twice daily|three times daily|morning|night|after food|before food|every \\d+ hours?)\\b")
    private val timePattern = Regex("(?i)\\b(\\d{1,2}):?(\\d{2})\\s?(am|pm)?\\b")
    private val batchExpiryPattern = Regex("(?i)(EXP|Expiry|MFG)[\\s:]*(\\d{2}[/\\-]\\d{2,4})")
    private val pharmaCompanyPattern = Regex("(?i)\\b(pharma|pharmaceutical|laboratories|labs|healthcare|medicine|medicines)\\b")
    private val brandNameLinePattern = Regex("^[A-Za-z][A-Za-z0-9+\\- ]{2,32}$")
    private val medicineKeywords = listOf(
        "mg", "ml", "tablet", "capsule", "syrup", "dose", "dosage",
        "twice", "once", "daily", "oral", "topical", "injection",
        "batch", "mfg", "exp", "rx", "dr.", "pharma", "generic"
    )
    private val ignoredTitleWords = setOf(
        "tablet", "tablets", "capsule", "capsules", "mg", "mcg", "g", "ml", "dose", "take",
        "before", "after", "food", "daily", "morning", "night", "as", "prescribed"
    )

    fun isLikelyMedicineText(recognizedText: String): Boolean {
        val normalized = recognizedText.trim()
        if (normalized.isBlank()) return false

        val lower = normalized.lowercase()
        val lines = normalized.lines().map { it.trim() }.filter { it.isNotBlank() }
        val keywordCount = medicineKeywords.count { lower.contains(it) }
        val hasNumbers = normalized.any { it.isDigit() }
        val hasDosagePattern = dosagePattern.containsMatchIn(normalized)
        val hasBatchOrExpiry = batchExpiryPattern.containsMatchIn(normalized)
        val hasPharmaTerm = pharmaCompanyPattern.containsMatchIn(normalized)
        val hasBrandLikeLine = lines.any { line ->
            brandNameLinePattern.matches(line) &&
                line.none(Char::isLowerCase) &&
                line.any(Char::isLetter)
        }

        var score = 0
        score += keywordCount.coerceAtMost(3)
        if (hasNumbers) score += 1
        if (hasDosagePattern) score += 2
        if (hasBatchOrExpiry) score += 2
        if (hasPharmaTerm) score += 1
        if (hasBrandLikeLine) score += 1

        // Accept labels with strong medicine signals while avoiding obvious non-label text.
        return score >= 2
    }

    fun parse(rawText: String): MedicineOcrSuggestion {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val title = lines.firstOrNull { line ->
            looksLikeMedicineName(line)
        } ?: lines
            .flatMap { line -> line.split(Regex("\\s+")) }
            .filter { token ->
                token.length in 3..32 &&
                    token.any(Char::isLetter) &&
                    token.none(Char::isDigit) &&
                    token.firstOrNull()?.isUpperCase() == true &&
                    token.lowercase() !in ignoredTitleWords
            }
            .maxByOrNull { it.length }
            .orEmpty()

        val dosage = lines.firstNotNullOfOrNull { line ->
            dosagePattern.find(line)?.value
        }.orEmpty()
        val timing = lines.firstOrNull { line -> timingPattern.containsMatchIn(line) }.orEmpty()
        val timeHint = lines.firstOrNull { line -> timePattern.containsMatchIn(line) }.orEmpty()
        val batchExpiry = lines.firstNotNullOfOrNull { line ->
            batchExpiryPattern.find(line)?.value
        }.orEmpty()
        val highConfidence = title.isNotBlank() && dosage.isNotBlank()

        return MedicineOcrSuggestion(
            title = title,
            dosageText = dosage,
            timingHint = timing,
            timeHint = timeHint,
            batchOrExpiry = batchExpiry,
            isHighConfidence = highConfidence
        )
    }

    private fun looksLikeMedicineName(line: String): Boolean {
        val cleaned = line.trim()
        if (cleaned.length !in 3..40) return false
        if (!cleaned.any(Char::isLetter)) return false
        if (dosagePattern.containsMatchIn(cleaned)) return false
        if (timingPattern.containsMatchIn(cleaned)) return false
        if (batchExpiryPattern.containsMatchIn(cleaned)) return false

        val words = cleaned.split(Regex("\\s+"))
        if (words.size > 5) return false
        if (words.any { it.lowercase() in ignoredTitleWords }) return false

        val letterRatio = cleaned.count(Char::isLetter).toDouble() / cleaned.length.toDouble()
        return letterRatio >= 0.55
    }
}