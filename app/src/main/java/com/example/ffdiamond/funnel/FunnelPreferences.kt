package com.example.ffdiamond.funnel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.funnelStore by preferencesDataStore(name = "funnel_prefs")

object FunnelPreferences {

    private val COMPLETED = booleanPreferencesKey("funnel_completed_v1")
    private val STEP = stringPreferencesKey("funnel_step")
    private val LANGUAGE = stringPreferencesKey("language")
    private val GENDER = stringPreferencesKey("gender")
    private val AGE = stringPreferencesKey("age")
    private val CATEGORIES = stringPreferencesKey("categories")
    private val WATCH = stringPreferencesKey("watch")
    private val INTERESTS = stringPreferencesKey("interests")

    fun isCompletedBlocking(context: Context): Boolean =
        blockingIo { isCompleted(context) }

    suspend fun isCompleted(context: Context): Boolean =
        context.funnelStore.data.map { it[COMPLETED] == true }.first()

    suspend fun setCompleted(context: Context, completed: Boolean = true) {
        context.funnelStore.edit { it[COMPLETED] = completed }
    }

    fun stepBlocking(context: Context): FunnelStep = blockingIo {
        val name = context.funnelStore.data.map { it[STEP].orEmpty() }.first()
        val step = runCatching { FunnelStep.valueOf(name) }.getOrNull()
            ?: FunnelStep.SET_DEFAULT
        if (step == FunnelStep.SPLASH) FunnelStep.SET_DEFAULT else step
    }

    fun savedLanguageBlocking(context: Context): String = blockingIo {
        context.funnelStore.data.map { it[LANGUAGE].orEmpty() }.first()
    }

    fun languageCodeBlocking(context: Context): String =
        AppLocale.normalize(savedLanguageBlocking(context))

    suspend fun saveStep(context: Context, step: FunnelStep) {
        context.funnelStore.edit { it[STEP] = step.name }
    }

    fun resetProgressBlocking(context: Context) = blockingIo {
        context.funnelStore.edit { it.clear() }
    }

    suspend fun saveSingle(context: Context, key: ChoiceKey, value: String) {
        context.funnelStore.edit { prefs ->
            prefs[key.storeKey] = value
        }
    }

    suspend fun saveMulti(context: Context, key: ChoiceKey, values: Set<String>) {
        context.funnelStore.edit { prefs ->
            prefs[key.storeKey] = values.joinToString("\u001f")
        }
    }

    suspend fun read(context: Context, key: ChoiceKey): Set<String> {
        val raw = context.funnelStore.data.map { it[key.storeKey].orEmpty() }.first()
        if (raw.isEmpty()) return emptySet()
        return raw.split("\u001f").filter { it.isNotBlank() }.toSet()
    }

    enum class ChoiceKey(val storeKey: androidx.datastore.preferences.core.Preferences.Key<String>) {
        LANGUAGE(FunnelPreferences.LANGUAGE),
        GENDER(FunnelPreferences.GENDER),
        AGE(FunnelPreferences.AGE),
        CATEGORIES(FunnelPreferences.CATEGORIES),
        WATCH(FunnelPreferences.WATCH),
        INTERESTS(FunnelPreferences.INTERESTS)
    }

    private fun <T> blockingIo(block: suspend () -> T): T =
        runBlocking(Dispatchers.IO) { block() }
}
