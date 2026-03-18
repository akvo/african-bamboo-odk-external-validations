package org.akvo.afribamodkvalidator.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.akvo.afribamodkvalidator.data.dao.FormMetadataDao
import org.akvo.afribamodkvalidator.data.dao.PlotDao
import org.akvo.afribamodkvalidator.data.dao.PlotWarningDao
import org.akvo.afribamodkvalidator.data.dao.SubmissionDao
import org.akvo.afribamodkvalidator.data.entity.FormMetadataEntity
import org.akvo.afribamodkvalidator.data.entity.PlotEntity
import org.akvo.afribamodkvalidator.data.entity.PlotWarningEntity
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
        PlotEntity::class,
        PlotWarningEntity::class
    ],
    version = 7,
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

    /**
     * DAO for plot data access.
     */
    abstract fun plotDao(): PlotDao

    /**
     * DAO for plot warning data access.
     */
    abstract fun plotWarningDao(): PlotWarningDao
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
         * Migration from v5 to v6: adds the plot_warnings table.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `plot_warnings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `plotSubmissionUuid` TEXT NOT NULL,
                        `warningType` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `shortText` TEXT NOT NULL,
                        `value` REAL NOT NULL,
                        `fieldSynced` INTEGER NOT NULL DEFAULT 0,
                        `notesSynced` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plot_warnings_plotSubmissionUuid` ON `plot_warnings` (`plotSubmissionUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plot_warnings_warningType` ON `plot_warnings` (`warningType`)")
            }
        }

        /**
         * Migration from v6 to v7: adds polygonFields column to form_metadata.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE form_metadata ADD COLUMN polygonFields TEXT")
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
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
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
