package com.tihor.quill.copy

import kotlinx.serialization.Serializable

/** The Copy Catalog bundle schema (spec §2.3). */
@Serializable
data class CopyCatalog(
    val schemaVersion: Int = 1,
    val revision: Int = 0,
    val locale: String = "en",
    val updatedAt: String = "",
    val strings: Map<String, String> = emptyMap(),
)
