package com.openavplugin.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM app_rules ORDER BY updatedAt DESC")
    fun getAllRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE packageName = :packageName")
    suspend fun getRule(packageName: String): AppRule?

    @Query("SELECT * FROM app_rules WHERE isActive = 1")
    fun getActiveRules(): Flow<List<AppRule>>

    @Query("SELECT * FROM app_rules WHERE isActive = 1 AND (cameraEnabled = 1 OR micEnabled = 1)")
    fun getEnabledRules(): Flow<List<AppRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: AppRule)

    @Update
    suspend fun updateRule(rule: AppRule)

    @Delete
    suspend fun deleteRule(rule: AppRule)

    @Query("DELETE FROM app_rules WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("SELECT * FROM app_rules WHERE packageName LIKE :pattern")
    suspend fun getRulesByPattern(pattern: String): List<AppRule>
}
