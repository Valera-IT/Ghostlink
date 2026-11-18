package io.github.valerait.ghostlink.core.platform

import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/** TextureView поддерживает круглую обрезку
SurfaceView внутри VideoView — нет на части устройств. */
@Composable
internal fun InlineVideoSurface(
    file: File,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val player = remember(file) { MediaPlayer() }
    val texture =
        remember(file) {
            TextureView(context).apply {
                clipToOutline = true
                outlineProvider =
                    object : ViewOutlineProvider() {
                        override fun getOutline(
                            view: View,
                            outline: Outline,
                        ) = outline.setOval(0, 0, view.width, view.height)
                    }
                var prepared = false
                setOnClickListener {
                    if (prepared) {
                        if (player.isPlaying) player.pause() else player.start()
                    }
                }
                surfaceTextureListener =
                    object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            val output = Surface(surface)
                            player.setSurface(output)
                            output.release()
                            player.setDataSource(file.absolutePath)
                            player.setOnPreparedListener {
                                prepared = true
                                cropVideo(this@apply, it.videoWidth, it.videoHeight)
                                it.start()
                            }
                            player.setOnErrorListener { _, _, _ ->
                                prepared = false
                                true
                            }
                            player.prepareAsync()
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surface: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) {
                            cropVideo(this@apply, player.videoWidth, player.videoHeight)
                            invalidateOutline()
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            runCatching { player.pause() }
                            return true
                        }
                    }
            }
        }
    AndroidView(factory = { texture }, modifier = modifier)
    DisposableEffect(player) {
        onDispose {
            texture.surfaceTextureListener = null
            player.release()
            file.delete()
        }
    }
}

private fun cropVideo(
    view: TextureView,
    width: Int,
    height: Int,
) {
    if (minOf(width, height, view.width, view.height) <= 0) return
    val ratio = (width.toFloat() / height) / (view.width.toFloat() / view.height)
    view.setTransform(
        Matrix().apply {
            setScale(maxOf(1f, ratio), maxOf(1f, 1f / ratio), view.width / 2f, view.height / 2f)
        },
    )
}
