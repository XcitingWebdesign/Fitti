package com.fitti.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fitti_settings", Context.MODE_PRIVATE)

    var repsMin: Int
        get() = prefs.getInt(KEY_REPS_MIN, 8)
        set(value) = prefs.edit().putInt(KEY_REPS_MIN, value).apply()

    var repsMax: Int
        get() = prefs.getInt(KEY_REPS_MAX, 12)
        set(value) = prefs.edit().putInt(KEY_REPS_MAX, value).apply()

    var sets: Int
        get() = prefs.getInt(KEY_SETS, 2)
        set(value) = prefs.edit().putInt(KEY_SETS, value).apply()

    var restSeconds: Int
        get() = prefs.getInt(KEY_REST_SECONDS, 60)
        set(value) = prefs.edit().putInt(KEY_REST_SECONDS, value).apply()

    var progressionStepKg: Double
        get() = prefs.getFloat(KEY_PROGRESSION_STEP, 2.5f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_PROGRESSION_STEP, value.toFloat()).apply()

    var heightCm: Int
        get() = prefs.getInt(KEY_HEIGHT_CM, 0)
        set(value) = prefs.edit().putInt(KEY_HEIGHT_CM, value).apply()

    var goal: String
        get() = prefs.getString(KEY_GOAL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GOAL, value).apply()

    var claudeApiKey: String
        get() = prefs.getString(KEY_CLAUDE_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLAUDE_API_KEY, value).apply()

    var claudeSonnetModel: String
        get() = prefs.getString(KEY_CLAUDE_SONNET_MODEL, DEFAULT_SONNET_MODEL)
            ?.ifBlank { DEFAULT_SONNET_MODEL } ?: DEFAULT_SONNET_MODEL
        set(value) = prefs.edit().putString(KEY_CLAUDE_SONNET_MODEL, value).apply()

    var claudeOpusModel: String
        get() = prefs.getString(KEY_CLAUDE_OPUS_MODEL, DEFAULT_OPUS_MODEL)
            ?.ifBlank { DEFAULT_OPUS_MODEL } ?: DEFAULT_OPUS_MODEL
        set(value) = prefs.edit().putString(KEY_CLAUDE_OPUS_MODEL, value).apply()

    fun getSeenAchievements(): Set<String> =
        prefs.getStringSet(KEY_SEEN_ACHIEVEMENTS, emptySet())?.toSet() ?: emptySet()

    fun markAchievementSeen(code: String) {
        val current = getSeenAchievements().toMutableSet()
        current += code
        prefs.edit().putStringSet(KEY_SEEN_ACHIEVEMENTS, current).apply()
    }

    var dailyReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_REMINDER, true)
        set(value) = prefs.edit().putBoolean(KEY_DAILY_REMINDER, value).apply()

    var dailyReminderHour: Int
        get() = prefs.getInt(KEY_DAILY_HOUR, 20)
        set(value) = prefs.edit().putInt(KEY_DAILY_HOUR, value).apply()

    var monthlyMeasurementReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONTHLY_MEAS, true)
        set(value) = prefs.edit().putBoolean(KEY_MONTHLY_MEAS, value).apply()

    var weeklyCoachingReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEEKLY_COACH, true)
        set(value) = prefs.edit().putBoolean(KEY_WEEKLY_COACH, value).apply()

    companion object {
        private const val KEY_REPS_MIN = "reps_min"
        private const val KEY_REPS_MAX = "reps_max"
        private const val KEY_SETS = "sets"
        private const val KEY_REST_SECONDS = "rest_seconds"
        private const val KEY_PROGRESSION_STEP = "progression_step"
        private const val KEY_HEIGHT_CM = "height_cm"
        private const val KEY_GOAL = "goal"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_CLAUDE_SONNET_MODEL = "claude_sonnet_model"
        private const val KEY_CLAUDE_OPUS_MODEL = "claude_opus_model"
        private const val KEY_SEEN_ACHIEVEMENTS = "seen_achievements"
        private const val KEY_DAILY_REMINDER = "notif_daily_enabled"
        private const val KEY_DAILY_HOUR = "notif_daily_hour"
        private const val KEY_MONTHLY_MEAS = "notif_monthly_meas_enabled"
        private const val KEY_WEEKLY_COACH = "notif_weekly_coach_enabled"

        const val DEFAULT_SONNET_MODEL = "claude-sonnet-4-6"
        const val DEFAULT_OPUS_MODEL = "claude-opus-4-7"
    }
}
