package org.akvo.afribamodkvalidator.data.database

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for JSON serialization.
 *
 * Enables storing complex objects as JSON strings in the database.
 * Used primarily for the rawData column which stores dynamic form data.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { json.decodeFromString<List<String>>(it) }
    }

    @TypeConverter
    fun fromDoubleList(value: List<Double>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toDoubleList(value: String?): List<Double>? {
        return value?.let { json.decodeFromString<List<Double>>(it) }
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = false
        }
    }
}
