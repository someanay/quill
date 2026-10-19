package com.tihor.quill.stt

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/** Cancel the upload/body read with its dictation job; always close even an unclaimed response. */
internal suspend fun <T> Call.executeCancellable(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(error))
        }

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching { response.use { if (continuation.isActive) read(it) else return } }
            if (continuation.isActive) continuation.resumeWith(result)
        }
    })
}
