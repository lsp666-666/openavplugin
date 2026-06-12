package com.openavplugin.ui.apps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.openavplugin.data.db.AppRule
import com.openavplugin.data.db.RuleDao
import com.openavplugin.data.db.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class AppConfigViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var ruleDao: RuleDao
    private lateinit var context: Context
    private lateinit var viewModel: AppConfigViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ruleDao = mock(RuleDao::class.java)
        context = mock(Context::class.java)

        testScope.runTest {
            viewModel = AppConfigViewModel(ruleDao, context)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial app rule is null`() = testScope.runTest {
        assertNull(viewModel.appRule.value)
    }

    @Test
    fun `loadAppRule fetches from dao`() = testScope.runTest {
        val rule = AppRule(
            packageName = "com.test.app",
            appName = "Test",
            cameraEnabled = true,
            micEnabled = false
        )
        `when`(ruleDao.getRule("com.test.app")).thenReturn(rule)

        viewModel.loadAppRule("com.test.app")

        val loaded = viewModel.appRule.value
        assertNotNull(loaded)
        assertEquals("com.test.app", loaded!!.packageName)
        assertTrue(loaded.cameraEnabled)
        assertFalse(loaded.micEnabled)
    }

    @Test
    fun `loadAppRule handles null from dao`() = testScope.runTest {
        `when`(ruleDao.getRule("com.missing.app")).thenReturn(null)

        viewModel.loadAppRule("com.missing.app")

        assertNull(viewModel.appRule.value)
    }

    @Test
    fun `toggleCamera enables camera with LOCAL_VIDEO source`() = testScope.runTest {
        `when`(ruleDao.getRule("com.test.app")).thenReturn(null)

        viewModel.toggleCamera("com.test.app", "Test", true)

        verify(ruleDao).insertRule(any<AppRule>())
        val saved = viewModel.appRule.value
        assertNotNull(saved)
        assertTrue(saved!!.cameraEnabled)
        assertEquals(SourceType.LOCAL_VIDEO, saved.cameraSourceType)
    }

    @Test
    fun `toggleCamera disables camera with NONE source`() = testScope.runTest {
        val existing = AppRule(
            packageName = "com.test.app", appName = "Test",
            cameraEnabled = true, cameraSourceType = SourceType.LOCAL_VIDEO
        )
        `when`(ruleDao.getRule("com.test.app")).thenReturn(existing)

        viewModel.toggleCamera("com.test.app", "Test", false)

        verify(ruleDao).insertRule(any<AppRule>())
        val saved = viewModel.appRule.value
        assertNotNull(saved)
        assertFalse(saved!!.cameraEnabled)
    }

    @Test
    fun `toggleMicrophone enables mic with SILENCE source`() = testScope.runTest {
        `when`(ruleDao.getRule("com.test.app")).thenReturn(null)

        viewModel.toggleMicrophone("com.test.app", "Test", true)

        verify(ruleDao).insertRule(any<AppRule>())
        val saved = viewModel.appRule.value
        assertNotNull(saved)
        assertTrue(saved!!.micEnabled)
        assertEquals(SourceType.SILENCE, saved.micSourceType)
    }

    @Test
    fun `toggleMicrophone disables mic with NONE source`() = testScope.runTest {
        val existing = AppRule(
            packageName = "com.test.app", appName = "Test",
            micEnabled = true, micSourceType = SourceType.SILENCE
        )
        `when`(ruleDao.getRule("com.test.app")).thenReturn(existing)

        viewModel.toggleMicrophone("com.test.app", "Test", false)

        verify(ruleDao).insertRule(any<AppRule>())
        val saved = viewModel.appRule.value
        assertNotNull(saved)
        assertFalse(saved!!.micEnabled)
    }

    @Test
    fun `saveOrUpdateRule inserts and updates state`() = testScope.runTest {
        val rule = AppRule(
            packageName = "com.test.app", appName = "Test",
            cameraEnabled = true, micEnabled = true
        )

        viewModel.saveOrUpdateRule(rule)

        verify(ruleDao).insertRule(rule)
        val saved = viewModel.appRule.value
        assertEquals("com.test.app", saved!!.packageName)
    }
}
