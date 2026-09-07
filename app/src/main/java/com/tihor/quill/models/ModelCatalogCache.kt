package com.tihor.quill.models

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Only public model metadata is cached. API keys are never part of the identity or payload. */
class ModelCatalogCache(directory: File, identity: String) {
    private val file = File(directory, "models-" + MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")

    fun read(): List<ModelOption> = runCatching {
        require(file.length() <= 4L * 1024 * 1024)
        Json.parseToJsonElement(file.readText()).jsonArray.map { entry ->
            val item = entry.jsonObject
            ModelOption(item.getValue("id").jsonPrimitive.content, item.getValue("name").jsonPrimitive.content,
                item.getValue("created").jsonPrimitive.long, item["isFree"]?.jsonPrimitive?.booleanOrNull ?: false,
                item["isPaid"]?.jsonPrimitive?.booleanOrNull ?: false)
        }
    }.getOrDefault(emptyList())

    fun write(models: List<ModelOption>) {
        val content = buildJsonArray {
            models.forEach { model -> add(buildJsonObject {
                put("id", model.id); put("name", model.name); put("created", model.created); put("isFree", model.isFree)
                put("isPaid", model.isPaid)
            }) }
        }.toString()
        val temporary = File(file.path + ".tmp")
        temporary.writeText(content)
        if (!temporary.renameTo(file)) temporary.delete()
    }
}
