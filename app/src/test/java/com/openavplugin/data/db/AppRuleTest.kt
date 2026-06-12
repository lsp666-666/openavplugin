package com.openavplugin.data.db

import org.junit.Assert.*
import org.junit.Test

class AppRuleTest {

    @Test
    fun `default cameraEnabled is false`() {
        val rule = AppRule(packageName = "com.test.app", appName = "Test")
        assertFalse(rule.cameraEnabled)
    }

    @Test
    fun `default micEnabled is false`() {
        val rule = AppRule(packageName = "com.test.app", appName = "Test")
        assertFalse(rule.micEnabled)
    }

    @Test
    fun `default source types are NONE`() {
        val rule = AppRule(packageName = "com.test.app", appName = "Test")
        assertEquals(SourceType.NONE, rule.cameraSourceType)
        assertEquals(SourceType.NONE, rule.micSourceType)
    }

    @Test
    fun `default camera resolution is 1280x720`() {
        val rule = AppRule(packageName = "com.test.app", appName = "Test")
        assertEquals("1280x720", rule.cameraResolution)
    }

    @Test
    fun `default mic sample rate is 44100`() {
        val rule = AppRule(packageName = "com.test.app", appName = "Test")
        assertEquals(44100, rule.micSampleRate)
    }

    @Test
    fun `default mic channels is 1`() {
        val rule = AppRule(packageName = "com.test.app", appName = "Test")
        assertEquals(1, rule.micChannels)
    }

    @Test
    fun `default isActive is true`() {
        val rule = AppRule(packageName = "com.test.app", appName = "Test")
        assertTrue(rule.isActive)
    }

    @Test
    fun `createdAt and updatedAt defaults are zero`() {
        val rule = AppRule(packageName = "com.test.app", appName = "Test")
        assertEquals(0L, rule.createdAt)
        assertEquals(0L, rule.updatedAt)
    }

    @Test
    fun `withTimestamps factory sets timestamps`() {
        val rule = AppRule.withTimestamps(packageName = "com.test.app", appName = "Test")
        assertTrue(rule.createdAt > 0)
        assertTrue(rule.updatedAt > 0)
    }

    @Test
    fun `copy preserves fields`() {
        val rule = AppRule(
            packageName = "com.test.app",
            appName = "Test",
            cameraEnabled = true,
            micEnabled = true,
            cameraSourceType = SourceType.LOCAL_VIDEO,
            cameraResolution = "1920x1080"
        )
        val copy = rule.copy(micEnabled = false)
        assertEquals(rule.packageName, copy.packageName)
        assertEquals(rule.cameraEnabled, copy.cameraEnabled)
        assertFalse(copy.micEnabled)
        assertEquals(rule.cameraSourceType, copy.cameraSourceType)
        assertEquals(rule.cameraResolution, copy.cameraResolution)
    }

    @Test
    fun `SourceType enum has all entries`() {
        val values = SourceType.entries.toList()
        assertEquals(7, values.size)
        assertTrue(values.contains(SourceType.NONE))
        assertTrue(values.contains(SourceType.SILENCE))
        assertTrue(values.contains(SourceType.LOCAL_VIDEO))
        assertTrue(values.contains(SourceType.LOCAL_AUDIO))
        assertTrue(values.contains(SourceType.NETWORK_STREAM))
        assertTrue(values.contains(SourceType.SCREEN_CAPTURE))
        assertTrue(values.contains(SourceType.SYSTEM_AUDIO))
    }
}
