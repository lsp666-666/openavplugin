package com.openavplugin.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ConfigManagerTest {

    private lateinit var configManager: ConfigManager
    private val mockContext = mock(android.content.Context::class.java)

    @Before
    fun setUp() {
        configManager = ConfigManager(mockContext)
    }

    @Test
    fun defaultGlobalCameraEnabled_isFalse() = runBlocking {
        val result = configManager.globalCameraEnabled.first()
        assertEquals(false, result)
    }

    @Test
    fun defaultGlobalMicEnabled_isFalse() = runBlocking {
        val result = configManager.globalMicEnabled.first()
        assertEquals(false, result)
    }

    @Test
    fun defaultRuntimeMode_isAuto() = runBlocking {
        val result = configManager.runtimeMode.first()
        assertEquals(RuntimeMode.AUTO, result)
    }

    @Test
    fun defaultLogLevel_isInfo() = runBlocking {
        val result = configManager.logLevel.first()
        assertEquals("INFO", result)
    }

    @Test
    fun defaultBootAutoStart_isTrue() = runBlocking {
        val result = configManager.bootAutoStart.first()
        assertEquals(true, result)
    }

    @Test
    fun runtimeMode_enum_has_all_entries() {
        val entries = RuntimeMode.entries.toList()
        assertEquals(3, entries.size)
        assertTrue(entries.contains(RuntimeMode.AUTO))
        assertTrue(entries.contains(RuntimeMode.LSPOSED))
        assertTrue(entries.contains(RuntimeMode.ROOT))
    }
}
