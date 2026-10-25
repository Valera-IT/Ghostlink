package io.github.valerait.ghostlink.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImagePickerTest {
    private val avatar = ImageRequest(maxSidePx = 256, maxBytes = 1000, square = true)

    @Test
    fun keepsBestQualityThatFits() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val jpeg =
            fitJpeg(avatar) { side, quality ->
                calls += side to quality
                ByteArray(if (quality <= 70) 900 else 1500)
            }
        assertEquals(900, jpeg?.size)
        assertEquals(listOf(256 to 90, 256 to 80, 256 to 70), calls)
    }

    @Test
    fun shrinksWhenLowQualityIsStillTooBig() {
        val sides = mutableSetOf<Int>()
        val jpeg =
            fitJpeg(avatar) { side, _ ->
                sides += side
                ByteArray(if (side <= 144) 500 else 5000)
            }
        assertEquals(500, jpeg?.size)
        assertEquals(setOf(256, 192, 144), sides)
    }

    @Test
    fun givesUpBelowMinimumSide() {
        assertNull(fitJpeg(avatar) { _, _ -> ByteArray(5000) })
    }

    @Test
    fun squareCropNeverUpscales() {
        assertEquals(256 to 256, fitSize(4000, 3000, 256, square = true))
        assertEquals(100 to 100, fitSize(100, 300, 256, square = true))
    }

    @Test
    fun keepsAspectRatioForNonSquare() {
        assertEquals(1280 to 960, fitSize(4000, 3000, 1280, square = false))
        assertEquals(800 to 600, fitSize(800, 600, 1280, square = false))
    }

    @Test
    fun severalSizesFromOnePictureReportTheirPixelSize() {
        val photo = ImageRequest(maxSidePx = 1280, maxBytes = 1000)
        val thumb = ImageRequest(maxSidePx = 320, maxBytes = 100)
        val images =
            fitJpegs(
                listOf(photo, thumb),
                // Полное фото влезает только уменьшенным до 960.
                encode = { request, side, _ -> ByteArray(if (request == photo && side > 960) 5000 else side / 10) },
                sizeOf = { _, side -> fitSize(4000, 3000, side, square = false) },
            )!!
        assertEquals(listOf(96, 32), images.map { it.jpeg.size })
        assertEquals(listOf(960 to 720, 320 to 240), images.map { it.width to it.height })
    }

    @Test
    fun severalSizesFailIfAnyDoesNotFit() {
        val requests = listOf(ImageRequest(maxSidePx = 1280, maxBytes = 1000), ImageRequest(maxSidePx = 320, maxBytes = 1))
        assertNull(fitJpegs(requests, encode = { _, _, _ -> ByteArray(10) }, sizeOf = { _, side -> side to side }))
    }
}
