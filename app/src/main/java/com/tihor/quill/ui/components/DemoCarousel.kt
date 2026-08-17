package com.tihor.quill.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.tihor.quill.carousel.CarouselClip
import com.tihor.quill.carousel.CarouselPlayerState
import com.tihor.quill.carousel.CarouselSource
import com.tihor.quill.carousel.CarouselSurface
import com.tihor.quill.copy.CopyKeys as K
import com.tihor.quill.copy.copy
import com.tihor.quill.ui.theme.quill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Build the default asset-backed [CarouselSource] (a remote source can be injected instead). */
@Composable
fun rememberAssetCarouselSource(): CarouselSource {
    val assets = LocalContext.current.applicationContext.assets
    return remember { com.tihor.quill.carousel.AssetCarouselSource(assets) }
}

/**
 * Reusable demo-video carousel (design decisions 1–8): a [HorizontalPager] of 9:16 cards playing muted,
 * looping MP4s from [surface]'s asset folder. **One** [ExoPlayer] serves the whole carousel — it plays
 * only the settled/current page (muted, [Player.REPEAT_MODE_ONE]), pauses during scroll, re-targets on
 * page settle, and is released on dispose/stop. Non-active pages show the clip's first frame as a poster
 * (no per-page players).
 *
 * **Fail-soft:** if the folder is missing or empty the whole section (header included) renders nothing
 * and reserves no space. **Reduced motion** (animator duration scale 0): cards don't autoplay — they
 * show posters with a play-on-tap affordance (decision 7).
 */
@Composable
fun DemoCarousel(
    surface: CarouselSurface,
    modifier: Modifier = Modifier,
    source: CarouselSource = rememberAssetCarouselSource(),
) {
    val clips = remember(surface, source) { source.clips(surface) }
    if (clips.isEmpty()) return // fail-soft empty state — no header, no reserved space

    val context = LocalContext.current
    val reduced = isAnimationDisabled()

    // Home cards are larger with a section header; onboarding cards are capped short so the step's CTA
    // stays visible above the pinned button (decision 5). 9:16 portrait either way.
    val onboarding = surface != CarouselSurface.HOME
    val cardWidth: Dp = if (onboarding) 124.dp else 180.dp
    val cardHeight: Dp = cardWidth * 16f / 9f

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f // decision: muted always
            playWhenReady = false
        }
    }
    // Pure holder decides target/play; keyed on `reduced` so toggling animations rebuilds the policy.
    val holder = remember(reduced) { CarouselPlayerState(autoplay = !reduced) }
    val pagerState = rememberPagerState(pageCount = { clips.size })

    // Release the single player on dispose (decision 4).
    DisposableEffect(player) {
        onDispose {
            holder.onDispose()
            player.release()
        }
    }

    // Lifecycle stop → pause (the app went to background); resume happens naturally on the next settle.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Reduced-motion play latch: reset whenever the settled page changes so each card starts as a poster.
    var userStarted by remember { mutableStateOf(false) }

    // Drive the one real player from pager settle/scroll through the pure holder.
    LaunchedEffect(pagerState, player, clips, reduced) {
        snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
            .collect { (scrolling, settled) ->
                if (scrolling) {
                    holder.onScrollInProgress()
                    player.pause()
                } else {
                    userStarted = false
                    holder.onPageSettled(settled)
                    clips.getOrNull(settled)?.let { clip ->
                        player.setMediaItem(MediaItem.fromUri("asset:///${clip.assetPath}"))
                        player.prepare()
                    }
                    if (holder.playing) player.play() else player.pause()
                }
            }
    }

    Column(modifier.fillMaxWidth()) {
        if (surface == CarouselSurface.HOME) {
            SectionHeader(copy(K.CAROUSEL_HOME_HEADER))
        }
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fixed(cardWidth),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(end = cardWidth * 0.4f), // next-card peek (decision 5)
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val clip = clips[page]
            val isActive = page == pagerState.settledPage
            val caption = copy(surface.captionKey(clip.ordinal))
            val generic = copy(K.CAROUSEL_CARD_A11Y)
            CarouselCard(
                clip = clip,
                width = cardWidth,
                height = cardHeight,
                isActive = isActive,
                player = player,
                showPlay = isActive && reduced && !userStarted,
                caption = caption,
                cardDescription = caption.ifBlank { generic },
                playLabel = copy(K.CAROUSEL_PLAY_A11Y),
                onPlay = {
                    holder.onPlayTapped()
                    userStarted = true
                    player.play()
                },
            )
        }
    }
}

// media3's PlayerSurface / SURFACE_TYPE_TEXTURE_VIEW are @UnstableApi; opt in explicitly (lint-fatal
// UnsafeOptInUsageError otherwise). The surface API is the documented Compose integration point.
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CarouselCard(
    clip: CarouselClip,
    width: Dp,
    height: Dp,
    isActive: Boolean,
    player: ExoPlayer,
    showPlay: Boolean,
    caption: String,
    cardDescription: String,
    playLabel: String,
    onPlay: () -> Unit,
) {
    val poster = rememberPoster(clip.assetPath)
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = cardDescription },
    ) {
        // First-frame poster: the still shown on non-active pages, and the flash-guard beneath the
        // active page's video surface until frames render (decision 4).
        poster?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (isActive) {
            // TEXTURE_VIEW (not SURFACE_VIEW) so the rounded card corners actually clip the video.
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (showPlay) {
            Box(
                Modifier.matchParentSize().clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Color(0x66000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = playLabel,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        if (caption.isNotBlank()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1f to Color(0xCC000000),
                        ),
                    ),
            )
            androidx.compose.material3.Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
}

/** Decode a clip's first frame off the main thread as the poster still. Null on any decode failure. */
@Composable
private fun rememberPoster(assetPath: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetPath) {
        bitmap = withContext(Dispatchers.IO) { decodeFirstFrame(context, assetPath)?.asImageBitmap() }
    }
    return bitmap
}

private fun decodeFirstFrame(context: Context, assetPath: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        val afd = context.assets.openFd(assetPath)
        try {
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } finally {
            afd.close()
        }
    } catch (e: Exception) {
        null // openFd (compressed asset) / decode failures fail soft: no poster, video still plays
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
            // ignore
        }
    }
}

/** True when the system animator duration scale is 0 (animations off) — mirrors InkMeter's check. */
@Composable
private fun isAnimationDisabled(): Boolean {
    val context = LocalContext.current
    return android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
}
