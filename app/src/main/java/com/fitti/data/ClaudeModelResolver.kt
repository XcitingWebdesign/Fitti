package com.fitti.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loest "automatisch neuestes Modell" ueber die Models-API auf
 * (GET /v1/models). Ergebnis wird pro App-Prozess gecacht; schlaegt die
 * Abfrage fehl, gilt der Fallback aus SettingsRepository.
 */
object ClaudeModelResolver {

    const val FAMILY_SONNET = "sonnet"
    const val FAMILY_OPUS = "opus"

    private val mutex = Mutex()
    private var cache: Map<String, String>? = null

    suspend fun resolve(configured: String, family: String, apiKey: String): String {
        if (configured.isNotBlank()) return configured
        val fallback = if (family == FAMILY_OPUS) SettingsRepository.DEFAULT_OPUS_MODEL
        else SettingsRepository.DEFAULT_SONNET_MODEL
        val latest = mutex.withLock {
            cache ?: fetchLatest(apiKey)?.also { cache = it }
        }
        return latest?.get(family) ?: fallback
    }

    private suspend fun fetchLatest(apiKey: String): Map<String, String>? =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL("https://api.anthropic.com/v1/models?limit=1000")
                    .openConnection() as HttpURLConnection
                try {
                    connection.setRequestProperty("x-api-key", apiKey)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    if (connection.responseCode != 200) return@withContext null
                    val body = connection.inputStream.use { it.bufferedReader().readText() }
                    pickLatest(body)
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                null
            }
        }

    /** Neuestes Modell (created_at) je Familie aus der /v1/models-Antwort. */
    fun pickLatest(modelsJson: String): Map<String, String> {
        val data = JSONObject(modelsJson).optJSONArray("data") ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        for (family in listOf(FAMILY_SONNET, FAMILY_OPUS)) {
            var bestId: String? = null
            var bestCreated = ""
            for (i in 0 until data.length()) {
                val model = data.getJSONObject(i)
                val id = model.optString("id")
                if (!id.startsWith("claude-$family-")) continue
                // RFC-3339-Zeitstempel in UTC sind lexikalisch sortierbar.
                val created = model.optString("created_at")
                if (bestId == null || created > bestCreated) {
                    bestId = id
                    bestCreated = created
                }
            }
            bestId?.let { result[family] = it }
        }
        return result
    }
}
