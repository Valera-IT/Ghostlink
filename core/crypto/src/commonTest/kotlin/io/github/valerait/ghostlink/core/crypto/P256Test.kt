package io.github.valerait.ghostlink.core.crypto

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class P256Test {
    private val gx = "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"
    private val gy = "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"
    private val p = "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff"

    private fun point(
        x: String,
        y: String,
    ) = ("04$x$y").hexToByteArray()

    @Test
    fun generatorIsOnTheCurve() {
        assertTrue(P256.isOnCurve(point(gx, gy)))
    }

    @Test
    fun generatorMirroredByYIsOnTheCurve() {
        // p − Gy: та же x, вторая точка; проверяет вычитание с заёмом.
        val negGy = "b01cbd1c01e58065711814b583f061e9d431cca994cea1313449bf97c840ae0a"
        assertTrue(P256.isOnCurve(point(gx, negGy)))
    }

    @Test
    fun shiftedPointsAreRejected() {
        assertFalse(P256.isOnCurve(point(gx, gy.dropLast(1) + "6")))
        assertFalse(P256.isOnCurve(point(gx.dropLast(1) + "7", gy)))
        assertFalse(P256.isOnCurve(point("00".repeat(32), "00".repeat(32))))
    }

    @Test
    fun coordinatesOutsideTheFieldAreRejected() {
        assertFalse(P256.isOnCurve(point(p, gy)))
        assertFalse(P256.isOnCurve(point(gx, "ff".repeat(32))))
    }
}
