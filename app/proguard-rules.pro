# ============================================================================
# Quill R8 / ProGuard keep rules (M12)
#
# Audited against the app's real reflective surface (M12 Part 2). The app talks to STT/LLM providers
# over RAW OkHttp (not Retrofit) and (de)serializes with kotlinx.serialization using EXPLICIT
# `Type.serializer()` calls — the R8-friendliest path. These rules are belt-and-suspenders so a
# release ("crashes only in release") build can never strip a generated serializer or a model.
#
# Reflective/serialized surface (cite):
#   kotlinx @Serializable models:
#     - stt/TranscriptionModels.kt   (TranscriptionResponse, ProviderErrorEnvelope, ProviderErrorBody)
#     - cleanup/CleanupModels.kt     (ChatRequest, ChatMessage, ChatResponse, ChatChoice)
#     - copy/CopyCatalog.kt          (CopyCatalog — the Copy Catalog bundle)
#     - settings/CustomWord.kt       (CustomWord)
#   JSON engines: cleanup/CleanupClient.kt, stt/OpenAiCompatibleTranscriptionClient.kt,
#                 copy/CopyRepository.kt, settings/CustomWordsStore.kt  (all `Type.serializer()`).
#   Network: raw okhttp3 (CleanupClient, OpenAiCompatibleTranscriptionClient, KeyValidator).
#   NOT reflective: DataStore Preferences (typed keys), Compose (handled by AGP), the crash report
#                   (plain text, not serialized).
# ============================================================================

# --- kotlinx.serialization (official keep set) -------------------------------
# Serialization reads annotations at runtime for polymorphic/contextual paths.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses

# Keep the synthetic $serializer classes the plugin generates for every @Serializable type.
-keepclassmembers class **$$serializer { *; }

# Keep `Companion` object fields of serializable classes.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on the companion of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$Companion Companion;
}
-keepclassmembers class <2>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects (none today, but cheap + future-proof).
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Quill models (explicit, so a refactor can't silently strip them) --------
-keep @kotlinx.serialization.Serializable class com.tihor.quill.** { *; }
-keepclassmembers class com.tihor.quill.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- OkHttp / Okio -----------------------------------------------------------
# OkHttp ships its own consumer rules; these silence warnings about the optional TLS/HTTP2 providers
# it references reflectively but that we don't bundle.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Retrofit (declared dependency; currently unused in main — raw OkHttp is the live path) --------
# Kept defensively in case a Retrofit interface is introduced; Retrofit needs generic signatures.
-keepattributes Signature,Exceptions
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# --- Coroutines --------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
