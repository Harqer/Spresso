package com.meta.wearable.retail.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Modern Jetpack DataStore implementation of a property file.
 * Replaces legacy java.util.Properties with a reactive, coroutine-safe system.
 */
class VaultierSettings(private val context: Context, private val name: String) {

    // Delegate for DataStore creation
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = name)

    /**
     * Retrieves a string value reactively.
     */
    fun getString(key: String, defaultValue: String): Flow<String> {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[prefKey] ?: defaultValue
        }
    }

    /**
     * Persists a string value asynchronously.
     */
    suspend fun putString(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }

    /**
     * Retrieves an integer value reactively.
     */
    fun getInt(key: String, defaultValue: Int): Flow<Int> {
        val prefKey = intPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[prefKey] ?: defaultValue
        }
    }

    /**
     * Persists an integer value asynchronously.
     */
    suspend fun putInt(key: String, value: Int) {
        val prefKey = intPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }
}
