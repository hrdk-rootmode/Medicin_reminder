package com.example.medicinreminder.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private const val LANGUAGE_PREFERENCES_NAME = "language_preferences"
private val Context.languagePreferencesDataStore by preferencesDataStore(name = LANGUAGE_PREFERENCES_NAME)

object LanguagePreferences {
    private val LANGUAGE_TAG = stringPreferencesKey("app_language_tag")

    suspend fun getLanguageTag(context: Context): String? {
        val preferences = context.languagePreferencesDataStore.data.first()
        return preferences[LANGUAGE_TAG]
    }

    suspend fun setLanguageTag(context: Context, languageTag: String?) {
        context.languagePreferencesDataStore.edit { preferences ->
            if (languageTag.isNullOrBlank()) {
                preferences.remove(LANGUAGE_TAG)
            } else {
                preferences[LANGUAGE_TAG] = languageTag
            }
        }
    }
}