package com.tihor.quill.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy

/** One screen-owned player; the supplied video loops without reloading its item or surface. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun OnboardingVideo(
    visible: Boolean,
    modifier: Modifier = Modifier,
    assetPath: String = "onboarding/final-quill-onboarding-1024-60fps.mp4",
    description: String? = null,
    onReady: () -> Unit = {},
    onError: () -> Unit = {},
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val screenVisible by rememberUpdatedState(visible)
    val readyCallback by rememberUpdatedState(onReady)
    val errorCallback by rememberUpdatedState(onError)
    val player = remember(context, lifecycle, assetPath) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(MediaItem.fromUri("asset:///$assetPath"))
            prepare()
        }
    }

    DisposableEffect(player, lifecycle) {
        fun updatePlayback() {
            player.playWhenReady = screenVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        val observer = LifecycleEventObserver { _, _ -> updatePlayback() }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() = readyCallback()
            override fun onPlayerError(error: PlaybackException) = errorCallback()
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        updatePlayback()
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    // Crossfade keeps the departing Welcome composed briefly: stop as soon as navigation begins.
    LaunchedEffect(player, lifecycle, visible) {
        player.playWhenReady = visible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    val videoDescription = description ?: copy(K.CAROUSEL_CARD_A11Y)
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = modifier.semantics { contentDescription = videoDescription },
    )
}
