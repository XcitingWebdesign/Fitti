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

    companion object {
        private const val KEY_REPS_MIN = "reps_min"
        private const val KEY_REPS_MAX = "reps_max"
        private const val KEY_SETS = "sets"
        private const val KEY_REST_SECONDS = "rest_seconds"
        private const val KEY_PROGRESSION_STEP = "progression_step"
        private const val KEY_HEIGHT_CM = "height_cm"
        private const val KEY_GOAL = "goal"
    }
}
