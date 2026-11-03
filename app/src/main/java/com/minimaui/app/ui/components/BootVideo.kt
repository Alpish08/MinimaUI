package com.minimaui.app.ui.components

import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The user's own boot animation: the video at [uri], played once, centred (never stretched) on black. [onEnded] is called when it
 * has played through (failed = false) or could not be played at all (failed = true). While [playing] is false the video is paused,
 * which keeps its last frame (and silences it) while the cover fades away. The sound follows [volume] (0..1).
 */
@Composable
fun BootVideo(uri: String, playing: Boolean, volume: Float, onEnded: (failed: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val player = remember { MediaPlayer() }
    var ratio by remember { mutableFloatStateOf(16f / 9f) }
    val ended by rememberUpdatedState(onEnded)
    val playingNow by rememberUpdatedState(playing)
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }
    LaunchedEffect(playing) {
        if (!playing) runCatching { if (player.isPlaying) player.pause() }
    }
    LaunchedEffect(volume) { runCatching { player.setVolume(volume, volume) } }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.aspectRatio(ratio),
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null

                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            try {
                                surface = Surface(texture)
                                player.setSurface(surface)
                                player.setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                        .build()
                                )
                                player.setDataSource(context, Uri.parse(uri))
                                player.setOnVideoSizeChangedListener { _, w, h -> if (w > 0 && h > 0) ratio = w.toFloat() / h }
                                player.setOnPreparedListener { if (playingNow) it.start() }
                                player.setOnCompletionListener { ended(false) }
                                player.setOnErrorListener { _, _, _ -> ended(true); true }
                                player.prepareAsync()
                            } catch (_: Exception) {
                                ended(true)
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            }
        )
    }
}
