package com.mytune.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.mytune.domain.model.*
import java.io.InputStream
import java.io.OutputStream

/** Independent from the v1.3 tuning library; uses the existing typed DataStore dependency. */
object AppPreferencesSerializer : Serializer<AppPreferences> {
    override val defaultValue = AppPreferences()
    override suspend fun readFrom(input: InputStream): AppPreferences {
        val fields = input.bufferedReader().readLines().mapNotNull {
            val pair = it.split('=', limit = 2)
            if (pair.size == 2) pair[0] to pair[1] else null
        }.toMap()
        return AppPreferences(
            headstock = HeadstockType.entries.firstOrNull { it.name == fields["headstock"] } ?: defaultValue.headstock,
            theme = ThemeMode.entries.firstOrNull { it.name == fields["theme"] } ?: defaultValue.theme,
            haptics = fields["haptics"]?.toBooleanStrictOrNull() ?: defaultValue.haptics,
            stringAnimation = fields["stringAnimation"]?.toBooleanStrictOrNull() ?: defaultValue.stringAnimation,
        )
    }
    override suspend fun writeTo(t: AppPreferences, output: OutputStream) {
        output.write(("mytune-appearance-v1\nheadstock=${t.headstock.name}\ntheme=${t.theme.name}\n" +
            "haptics=${t.haptics}\nstringAnimation=${t.stringAnimation}\n").toByteArray(Charsets.UTF_8))
    }
}

class PreferencesRepository(private val store: DataStore<AppPreferences>) {
    val preferences = store.data
    suspend fun headstock(value: HeadstockType) = store.updateData { it.copy(headstock = value) }
    suspend fun theme(value: ThemeMode) = store.updateData { it.copy(theme = value) }
    suspend fun haptics(value: Boolean) = store.updateData { it.copy(haptics = value) }
    suspend fun stringAnimation(value: Boolean) = store.updateData { it.copy(stringAnimation = value) }
}

private val Context.appearanceStore by dataStore("appearance_v1_5.store", AppPreferencesSerializer)
fun preferencesRepository(context: Context) = PreferencesRepository(context.applicationContext.appearanceStore)
