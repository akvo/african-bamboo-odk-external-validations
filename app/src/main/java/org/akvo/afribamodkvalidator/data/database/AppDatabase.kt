package org.akvo.afribamodkvalidator.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.akvo.afribamodkvalidator.data.dao.FormMetadataDao
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.data.entity.FormMetadataEntity
import org.akvo.afribamodkvalidator.data.entity.PlotEntity
import org.akvo.afribamodkvalidator.data.entity.SubmissionEntity

/**
 * Room database for AfriBamODKValidator app.
 *
 * Uses the Generic Hybrid Schema strategy:
 * - System fields stored in typed columns for efficient queries
 * - Dynamic form data stored as JSON in rawData column
 *
 * This design supports ANY KoboToolbox form schema without requiring
 * schema-specific database migrations.
 */
@Database(
    entities = [
        SubmissionEntity::class,
        FormMetadataEntity::class,
        PlotEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * DAO for submissions data access.
     */
    abstract fun submissionDao(): SubmissionDao

    /**
     * DAO for form metadata access.
     */
    abstract fun formMetadataDao(): FormMetadataDao

    companion object {
        private const val DATABASE_NAME = "external_odk_db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * Uses double-checked locking for thread safety.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * Build the database instance.
         */
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                // Enable destructive migrations during development
                // TODO: Implement proper migrations for production
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Close the database instance.
         * Primarily used for testing.
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
