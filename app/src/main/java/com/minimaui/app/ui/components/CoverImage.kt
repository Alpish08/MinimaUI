package com.minimaui.app.ui.components

import androidx.compose.runtime.getValue
import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CacheBytes = 40 * 1024 * 1024

private val coverCache = object : LruCache<String, ImageBitmap>(CacheBytes) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/**
 * Loads the user's cover image for a tile, decoded off the main thread at roughly [targetPx] on its
 * short side (never the full photo) and kept in a small memory cache. Null while loading or if there
 * is no cover, so callers just keep showing the blank tile.
 */
@Composable
fun rememberCover(uri: String?, targetPx: Int): ImageBitmap? {
    val context = LocalContext.current
    val key = "$uri@$targetPx"
    val cover by produceState(initialValue = uri?.let { coverCache.get(key) }, uri, targetPx) {
        value = when (uri) {
            null -> null
            else -> coverCache.get(key) ?: withContext(Dispatchers.IO) { decode(context, uri, targetPx) }
                ?.also { coverCache.put(key, it) }
        }
    }
    return cover
}

/** Decodes the cover into the cache ahead of time, so it shows at once when its tile comes on screen. */
suspend fun prefetchCover(context: Context, uri: String?, targetPx: Int) {
    if (uri == null) return
    val key = "$uri@$targetPx"
    if (coverCache.get(key) != null) return
    withContext(Dispatchers.IO) { decode(context, uri, targetPx) }?.let { coverCache.put(key, it) }
}

private fun decode(context: Context, uri: String, targetPx: Int): ImageBitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, Uri.parse(uri))
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val shortSide = minOf(info.size.width, info.size.height)
        decoder.setTargetSampleSize((shortSide / targetPx).coerceAtLeast(1))
    }.asImageBitmap()
}.getOrNull()
