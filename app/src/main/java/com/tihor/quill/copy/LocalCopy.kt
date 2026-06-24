package com.tihor.quill.copy

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.tihor.quill.ui.theme.QuillTheme

/** Process-wide [CopyRepository] (parses the baked-in catalog once). */
object Copy {
    @Volatile
    private var instance: CopyRepository? = null

    fun repository(context: Context): CopyRepository =
        instance ?: synchronized(this) {
            instance ?: CopyRepository(context.applicationContext).also { instance = it }
        }
}

val LocalCopy = staticCompositionLocalOf<CopyRepository> { error("CopyRepository not provided") }

/** Read UI copy by key in a composable (spec §2.3). No string literals in composables. */
@Composable
@ReadOnlyComposable
fun copy(key: String): String = LocalCopy.current.get(key)

@Composable
@ReadOnlyComposable
fun copy(key: String, vararg args: Any?): String = LocalCopy.current.get(key, *args)

/** App root every Activity wraps its content in: provides the Copy Catalog + the Ink&Signal theme. */
@Composable
fun QuillRoot(content: @Composable () -> Unit) {
    val context = LocalContext.current
    CompositionLocalProvider(LocalCopy provides Copy.repository(context)) {
        QuillTheme { content() }
    }
}
