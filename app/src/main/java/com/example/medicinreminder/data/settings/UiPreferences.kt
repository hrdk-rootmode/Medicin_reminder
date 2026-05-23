package com.example.medicinreminder.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private const val UI_PREFERENCES_NAME = "ui_preferences"
private val Context.uiPreferencesDataStore by preferencesDataStore(name = UI_PREFERENCES_NAME)

object UiPreferences {
    private val FLOATING_BUTTON_ANCHOR_RIGHT = booleanPreferencesKey("floating_button_anchor_right")

    suspend fun getFloatingButtonAnchorRight(context: Context): Boolean? {
        val prefs = context.uiPreferencesDataStore.data.first()
        return prefs[FLOATING_BUTTON_ANCHOR_RIGHT]
    }

    suspend fun setFloatingButtonAnchorRight(context: Context, right: Boolean) {
        context.uiPreferencesDataStore.edit { prefs ->
            prefs[FLOATING_BUTTON_ANCHOR_RIGHT] = right
        }
    }
}
