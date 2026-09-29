package com.example.autodial.util

/**
 * Detects the specific Safaricom recommendation failure:
 * "Recommendation failed — this customer number has already been recommended the offer today."
 *
 * Must NOT match general failures (insufficient balance, network errors, etc.).
 */
object EngageBotTrigger {

    fun isAlreadyRecommendedToday(response: String?): Boolean {
        if (response.isNullOrBlank()) return false
        val lower = response.lowercase()
            .replace('—', '-')
            .replace('–', '-')

        // Primary: "already recommended" / "already been recommended" with today context
        val alreadyRecommended = lower.contains("already recommended") ||
            lower.contains("already been recommended") ||
            (lower.contains("already") && lower.contains("recommended") && lower.contains("today"))

        if (!alreadyRecommended) return false

        // Prefer explicit today / recommendation-failed framing when present
        val hasToday = lower.contains("today")
        val hasRecommendationFailed = lower.contains("recommendation failed") ||
            lower.contains("recommend") ||
            lower.contains("recommendation")

        return hasToday || hasRecommendationFailed
    }
}
