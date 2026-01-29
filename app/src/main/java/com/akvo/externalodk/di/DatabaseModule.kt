package com.akvo.externalodk.di

import android.content.Context
import com.akvo.externalodk.data.dao.FormMetadataDao
import com.akvo.externalodk.data.dao.SubmissionDao
import com.akvo.externalodk.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provides the singleton AppDatabase instance.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    /**
     * Provides the SubmissionDao from the database.
     */
    @Provides
    @Singleton
    fun provideSubmissionDao(database: AppDatabase): SubmissionDao {
        return database.submissionDao()
    }

    /**
     * Provides the FormMetadataDao from the database.
     */
    @Provides
    @Singleton
    fun provideFormMetadataDao(database: AppDatabase): FormMetadataDao {
        return database.formMetadataDao()
    }
}
