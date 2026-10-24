package io.github.valerait.ghostlink.core.platform

import io.github.valerait.ghostlink.core.model.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmThisDeviceTest {
    @Test
    fun `platform comes from os name`() {
        assertEquals(Platform.MacOs, jvmPlatform("Mac OS X"))
        assertEquals(Platform.Windows, jvmPlatform("Windows 11"))
        assertEquals(Platform.Linux, jvmPlatform("Linux"))
        assertEquals(Platform.Unknown, jvmPlatform("FreeBSD"))
    }
}
