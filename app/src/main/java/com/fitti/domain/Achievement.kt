package com.fitti.domain

/**
 * Milestone-style achievements computed from session history.
 * Codes are stable identifiers persisted to SharedPreferences to track "seen" state.
 */
data class Achievement(
    val code: String,
    val emoji: String,
    val title: String,
    val description: String
) {
    companion object {
        const val FIRST_SESSION = "FIRST_SESSION"
        const val SESSIONS_10 = "SESSIONS_10"
        const val SESSIONS_25 = "SESSIONS_25"
        const val SESSIONS_50 = "SESSIONS_50"
        const val SESSIONS_100 = "SESSIONS_100"
        const val STREAK_4 = "STREAK_4"
        const val STREAK_12 = "STREAK_12"
        const val STRENGTH_DOUBLED = "STRENGTH_DOUBLED"
        const val ALL_GROUPS_WEEK = "ALL_GROUPS_WEEK"
    }
}
