package com.openavplugin.ui.settings

import com.openavplugin.data.ConfigManager
import com.openavplugin.data.RuntimeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var configManager: ConfigManager
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        configManager = mock(ConfigManager::class.java)

        `when`(configManager.runtimeMode).thenReturn(flowOf(RuntimeMode.AUTO))
        `when`(configManager.logLevel).thenReturn(flowOf("INFO"))
        `when`(configManager.bootAutoStart).thenReturn(flowOf(true))

        testScope.runTest {
            viewModel = SettingsViewModel(configManager)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial runtime mode is AUTO`() = testScope.runTest {
        assertEquals(RuntimeMode.AUTO, viewModel.runtimeMode.value)
    }

    @Test
    fun `initial log level is INFO`() = testScope.runTest {
        assertEquals("INFO", viewModel.logLevel.value)
    }

    @Test
    fun `initial boot auto-start is enabled`() = testScope.runTest {
        assertTrue(viewModel.bootAutoStart.value)
    }

    @Test
    fun `setRuntimeMode delegates to configManager`() = testScope.runTest {
        viewModel.setRuntimeMode(RuntimeMode.LSPOSED)
        verify(configManager).setRuntimeMode(RuntimeMode.LSPOSED)
    }

    @Test
    fun `setRuntimeMode_ROOT delegates to configManager`() = testScope.runTest {
        viewModel.setRuntimeMode(RuntimeMode.ROOT)
        verify(configManager).setRuntimeMode(RuntimeMode.ROOT)
    }

    @Test
    fun `setLogLevel delegates to configManager`() = testScope.runTest {
        viewModel.setLogLevel("DEBUG")
        verify(configManager).setLogLevel("DEBUG")
    }

    @Test
    fun `setBootAutoStart delegates to configManager`() = testScope.runTest {
        viewModel.setBootAutoStart(false)
        verify(configManager).setBootAutoStart(false)
    }

    @Test
    fun `runtime mode reflects flow changes`() = testScope.runTest {
        `when`(configManager.runtimeMode).thenReturn(flowOf(RuntimeMode.ROOT))
        viewModel = SettingsViewModel(configManager)
        assertEquals(RuntimeMode.ROOT, viewModel.runtimeMode.value)
    }
}
