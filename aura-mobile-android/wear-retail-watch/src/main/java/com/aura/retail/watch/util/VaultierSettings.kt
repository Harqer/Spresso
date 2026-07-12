package com.aura.retail.watch.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Modern Jetpack DataStore implementation for Wear OS settings.
 */
class VaultierSettings(
    private val context: Context,
    private val name: String,
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = name)

    fun getString(
        key: String,
        defaultValue: String,
    ): Flow<String> {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] ?: defaultValue }
    }

    suspend fun putString(
        key: String,
        value: String,
    ) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }

    fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Flow<Boolean> {
        val prefKey = booleanPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] ?: defaultValue }
    }

    suspend fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        val prefKey = booleanPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }
}
