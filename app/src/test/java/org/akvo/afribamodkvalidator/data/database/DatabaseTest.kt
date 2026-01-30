package org.akvo.afribamodkvalidator.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Base test class for Room database tests.
 * Provides an in-memory database instance for testing.
 */
@RunWith(AndroidJUnit4::class)
abstract class DatabaseTest {

    val database: AppDatabase by lazy {
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setupBase() {
        // Set main dispatcher to test dispatcher for Room
        Dispatchers.setMain(testDispatcher)
        // Ensure database is initialized
        database
    }

    @After
    fun tearDownBase() {
        // Close database
        try {
            database.close()
        } catch (e: Exception) {
            // Already closed
        }
        // Reset main dispatcher
        Dispatchers.resetMain()
    }
}
