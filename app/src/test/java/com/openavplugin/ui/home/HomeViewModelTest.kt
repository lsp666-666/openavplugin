package com.openavplugin.ui.home

import com.openavplugin.data.ConfigManager
import com.openavplugin.data.RuntimeMode
import com.openavplugin.data.db.AppRule
import com.openavplugin.data.db.RuleDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var configManager: ConfigManager
    private lateinit var ruleDao: RuleDao
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        configManager = mock(ConfigManager::class.java)
        ruleDao = mock(RuleDao::class.java)

        `when`(configManager.globalCameraEnabled).thenReturn(flowOf(false))
        `when`(configManager.globalMicEnabled).thenReturn(flowOf(false))
        `when`(configManager.runtimeMode).thenReturn(flowOf(RuntimeMode.AUTO))
        `when`(ruleDao.getEnabledRules()).thenReturn(flowOf(emptyList()))

        testScope.runTest {
            viewModel = HomeViewModel(configManager, ruleDao)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has camera disabled`() = testScope.runTest {
        val cameraEnabled = viewModel.globalCameraEnabled.value
        assertEquals(false, cameraEnabled)
    }

    @Test
    fun `initial state has mic disabled`() = testScope.runTest {
        val micEnabled = viewModel.globalMicEnabled.value
        assertEquals(false, micEnabled)
    }

    @Test
    fun `initial state has empty active rules`() = testScope.runTest {
        val rules = viewModel.activeRules.value
        assertTrue(rules.isEmpty())
    }

    @Test
    fun `initial runtime mode is AUTO`() = testScope.runTest {
        val mode = viewModel.runtimeMode.value
        assertEquals(RuntimeMode.AUTO, mode)
    }

    @Test
    fun `toggleGlobalCamera calls configManager`() = testScope.runTest {
        viewModel.toggleGlobalCamera(true)
        verify(configManager).setGlobalCameraEnabled(true)
    }

    @Test
    fun `toggleGlobalMic calls configManager`() = testScope.runTest {
        viewModel.toggleGlobalMic(true)
        verify(configManager).setGlobalMicEnabled(true)
    }

    @Test
    fun `activeRules reflect enabled rules`() = testScope.runTest {
        val enabledRule = AppRule(
            packageName = "com.test.app",
            appName = "Test App",
            cameraEnabled = true,
            micEnabled = false
        )
        `when`(ruleDao.getEnabledRules()).thenReturn(flowOf(listOf(enabledRule)))

        // Re-create viewModel with updated mock
        viewModel = HomeViewModel(configManager, ruleDao)

        val rules = viewModel.activeRules.value
        assertEquals(1, rules.size)
        assertEquals("com.test.app", rules[0].packageName)
    }
}
