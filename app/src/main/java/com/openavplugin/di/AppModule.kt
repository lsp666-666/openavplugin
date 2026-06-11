package com.openavplugin.di

import android.content.Context
import com.openavplugin.data.ConfigManager
import com.openavplugin.data.db.AppDatabase
import com.openavplugin.data.db.RuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideRuleDao(database: AppDatabase): RuleDao {
        return database.ruleDao()
    }

    @Provides
    @Singleton
    fun provideConfigManager(@ApplicationContext context: Context): ConfigManager {
        return ConfigManager(context)
    }
}
