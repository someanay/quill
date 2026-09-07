package com.tihor.quill.models

import com.tihor.quill.settings.SttProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class ModelUse { DICTATION, CLEANUP }
data class ModelOption(val id: String, val name: String = id, val created: Long = 0)
data class ModelPage(val models: List<ModelOption>, val nextPage: String? = null)

/** Provider catalogs are suggestions, never a reason to silently replace a saved selection. */
object ModelCatalog {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    fun endpoint(provider: SttProvider, use: ModelUse, customBaseUrl: String = ""): HttpUrl {
        val base = (if (provider == SttProvider.CUSTOM) customBaseUrl else provider.baseUrl).trimEnd('/').toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null)
        return base.newBuilder().apply { if (provider == SttProvider.GEMINI) addPathSegment("v1beta") }.addPathSegment("models").apply {
            if (provider == SttProvider.OPENROUTER && use == ModelUse.DICTATION) addQueryParameter("output_modalities", "transcription")
            if (provider == SttProvider.GEMINI) addQueryParameter("pageSize", "1000")
        }.build()
    }

    suspend fun fetch(provider: SttProvider, use: ModelUse, key: () -> String?, customBaseUrl: String = "", httpClient: OkHttpClient = client): List<ModelOption> = withContext(Dispatchers.IO) {
        val url = endpoint(provider, use, customBaseUrl)
        val token = key().orEmpty()
        require(token.isNotBlank() || provider == SttProvider.OPENROUTER)
        val models = mutableListOf<ModelOption>()
        val seen = mutableSetOf<String>()
        var next: String? = null
        do {
            ensureActive()
            val pageUrl = url.newBuilder().apply { next?.let { addQueryParameter("pageToken", it) } }.build()
            val request = Request.Builder().url(pageUrl).apply {
                if (token.isNotBlank()) header(if (provider == SttProvider.GEMINI) "x-goog-api-key" else "Authorization",
                    if (provider == SttProvider.GEMINI) token else "Bearer $token")
            }.build()
            val page = httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Catalog request failed" }
                val source = requireNotNull(response.body).source()
                source.request(4L * 1024 * 1024 + 1)
                require(source.buffer.size <= 4L * 1024 * 1024)
                parse(provider, use, source.readUtf8())
            }
            models += page.models
            next = page.nextPage
            if (next != null) check(seen.add(next!!) && seen.size < 20) { "Invalid catalog pagination" }
        } while (next != null)
        ensureActive()
        check(models.isNotEmpty()) { "No compatible models returned" }
        rank(models, use)
    }

    fun parse(provider: SttProvider, use: ModelUse, raw: String): ModelPage {
        val root = Json.parseToJsonElement(raw).jsonObject
        val items = root[if (provider == SttProvider.GEMINI) "models" else "data"] as? JsonArray
            ?: throw IllegalArgumentException("Missing catalog")
        val models = items.mapNotNull { item ->
            val row = item as? JsonObject ?: return@mapNotNull null
            val id = (row[if (provider == SttProvider.GEMINI) "name" else "id"] as? JsonPrimitive)?.contentOrNull
                ?.removePrefix("models/")?.takeIf { it.isNotBlank() && it.length <= 250 } ?: return@mapNotNull null
            val lower = id.lowercase()
            val outputs = (row["architecture"] as? JsonObject)?.get("output_modalities") as? JsonArray
            val methods = row["supportedGenerationMethods"] as? JsonArray
            val speech = lower.contains("whisper") || lower.contains("transcribe") || lower.contains("transcription")
            val compatible = when {
                use == ModelUse.CLEANUP -> !speech &&
                    listOf("guard", "safety", "tts", "orpheus", "playai", "speech", "audio", "embed", "compound", "realtime", "image", "live", "robotics", "computer-use").none { it in lower } &&
                    (provider != SttProvider.GEMINI || (lower.startsWith("gemini-") && methods?.any { it.jsonPrimitive.content == "generateContent" } == true)) &&
                    (provider != SttProvider.OPENROUTER || outputs?.any { it.jsonPrimitive.content == "text" } == true)
                provider == SttProvider.OPENROUTER -> outputs?.any { it.jsonPrimitive.content == "transcription" } == true
                provider == SttProvider.GEMINI -> lower.startsWith("gemini-") && methods?.any { it.jsonPrimitive.content == "generateContent" } == true &&
                    listOf("live", "tts", "image", "robotics", "computer-use", "embedding").none { it in lower }
                provider == SttProvider.CUSTOM -> true // Compatible servers do not share capability metadata.
                else -> speech && "diariz" !in lower && "realtime" !in lower
            }
            if (!compatible || (row["active"] as? JsonPrimitive)?.booleanOrNull == false) null
            else ModelOption(id, (row["displayName"] ?: row["name"])?.jsonPrimitive?.contentOrNull ?: id,
                (row["created"] as? JsonPrimitive)?.longOrNull ?: 0)
        }
        return ModelPage(rank(models, use), (root["nextPageToken"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() })
    }

    /** Suitability for short dictation/cleanup first; release recency breaks ties. No quality benchmark implied. */
    fun rank(models: List<ModelOption>, use: ModelUse): List<ModelOption> = models.distinctBy { it.id }
        .sortedWith(compareBy<ModelOption> {
            val id = it.id.lowercase()
            (if (listOf("preview", "experimental", "exp-", "reasoning").any { word -> word in id }) 20 else 0) +
                (if (use == ModelUse.DICTATION && listOf("turbo", "mini", "flash").any { word -> word in id }) 0
                 else if (use == ModelUse.CLEANUP && listOf("versatile", "instant", "instruct").any { word -> word in id }) 0 else 5)
        }.thenByDescending { it.created }.thenByDescending { option ->
            Regex("\\d+(?:\\.\\d+)?").find(option.id)?.value?.toDoubleOrNull() ?: 0.0
        }.thenBy { it.id })
}
