package com.openavplugin.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])

class RuleDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: RuleDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.ruleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertRule_and_getAllRules() = runBlocking {
        val rule = AppRule(
            packageName = "com.example.app",
            appName = "Test App",
            cameraEnabled = true,
            micEnabled = false
        )
        dao.insertRule(rule)

        val allRules = dao.getAllRules().first()
        assertEquals(1, allRules.size)
        assertEquals("com.example.app", allRules[0].packageName)
        assertTrue(allRules[0].cameraEnabled)
        assertFalse(allRules[0].micEnabled)
    }

    @Test
    fun insertRule_replaces_existing() = runBlocking {
        val rule1 = AppRule(packageName = "com.example.app", appName = "App", cameraEnabled = false)
        val rule2 = AppRule(packageName = "com.example.app", appName = "App", cameraEnabled = true)
        dao.insertRule(rule1)
        dao.insertRule(rule2)

        val allRules = dao.getAllRules().first()
        assertEquals(1, allRules.size)
        assertTrue(allRules[0].cameraEnabled)
    }

    @Test
    fun getRule_returns_correct_rule() = runBlocking {
        val rule = AppRule(
            packageName = "com.example.app",
            appName = "Test App",
            cameraSourceType = SourceType.LOCAL_VIDEO,
            cameraResolution = "1920x1080"
        )
        dao.insertRule(rule)

        val result = dao.getRule("com.example.app")
        assertNotNull(result)
        assertEquals("1920x1080", result!!.cameraResolution)
        assertEquals(SourceType.LOCAL_VIDEO, result.cameraSourceType)
    }

    @Test
    fun getRule_returns_null_for_missing() = runBlocking {
        val result = dao.getRule("com.missing.app")
        assertNull(result)
    }

    @Test
    fun deleteRule_removes_entry() = runBlocking {
        val rule = AppRule(packageName = "com.example.app", appName = "App")
        dao.insertRule(rule)
        dao.deleteRule(rule)

        val allRules = dao.getAllRules().first()
        assertTrue(allRules.isEmpty())
    }

    @Test
    fun deleteByPackageName_removes_entry() = runBlocking {
        val rule = AppRule(packageName = "com.example.app", appName = "App")
        dao.insertRule(rule)
        dao.deleteByPackageName("com.example.app")

        val allRules = dao.getAllRules().first()
        assertTrue(allRules.isEmpty())
    }

    @Test
    fun getActiveRules_returns_only_active() = runBlocking {
        val active = AppRule(packageName = "com.active.app", appName = "Active", isActive = true)
        val inactive = AppRule(packageName = "com.inactive.app", appName = "Inactive", isActive = false)
        dao.insertRule(active)
        dao.insertRule(inactive)

        val activeRules = dao.getActiveRules().first()
        assertEquals(1, activeRules.size)
        assertEquals("com.active.app", activeRules[0].packageName)
    }

    @Test
    fun getEnabledRules_returns_only_enabled() = runBlocking {
        val bothEnabled = AppRule(
            packageName = "com.full.app", appName = "Full",
            cameraEnabled = true, micEnabled = true
        )
        val cameraOnly = AppRule(
            packageName = "com.cam.app", appName = "CamOnly",
            cameraEnabled = true, micEnabled = false
        )
        val disabled = AppRule(
            packageName = "com.off.app", appName = "Off",
            cameraEnabled = false, micEnabled = false
        )
        dao.insertRule(bothEnabled)
        dao.insertRule(cameraOnly)
        dao.insertRule(disabled)

        val enabledRules = dao.getEnabledRules().first()
        assertEquals(2, enabledRules.size)
        val packages = enabledRules.map { it.packageName }.toSet()
        assertTrue(packages.contains("com.full.app"))
        assertTrue(packages.contains("com.cam.app"))
        assertFalse(packages.contains("com.off.app"))
    }

    @Test
    fun getRulesByPattern_matches_package_name() = runBlocking {
        val app1 = AppRule(packageName = "com.google.app", appName = "Google")
        val app2 = AppRule(packageName = "com.example.app", appName = "Example")
        val app3 = AppRule(packageName = "org.test.app", appName = "Test")
        dao.insertRule(app1)
        dao.insertRule(app2)
        dao.insertRule(app3)

        val result = dao.getRulesByPattern("%google%")
        assertEquals(1, result.size)
        assertEquals("com.google.app", result[0].packageName)
    }

    @Test
    fun updateRule_modifies_existing() = runBlocking {
        val rule = AppRule(
            packageName = "com.example.app", appName = "App",
            cameraEnabled = false, micEnabled = false
        )
        dao.insertRule(rule)

        val updated = rule.copy(cameraEnabled = true, micEnabled = true)
        dao.updateRule(updated)

        val result = dao.getRule("com.example.app")
        assertNotNull(result)
        assertTrue(result!!.cameraEnabled)
        assertTrue(result.micEnabled)
    }

    @Test
    fun insert_multiple_rules_and_order_by_updatedAt() = runBlocking {
        val old = AppRule(
            packageName = "com.old.app", appName = "Old",
            updatedAt = 1000L
        )
        val recent = AppRule(
            packageName = "com.recent.app", appName = "Recent",
            updatedAt = 2000L
        )
        dao.insertRule(old)
        // Give a slight delay to ensure ordering
        dao.insertRule(recent)

        val allRules = dao.getAllRules().first()
        assertEquals(2, allRules.size)
        // Should be most recent first
        assertEquals("com.recent.app", allRules[0].packageName)
    }
}
