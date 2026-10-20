package com.mytune.data.preferences

import androidx.datastore.core.DataStoreFactory
import com.mytune.data.tuning.*
import com.mytune.domain.model.*
import com.mytune.domain.tuning.TuningValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun `every preference survives a real DataStore close and reopen without touching tuning library`() = runTest {
        val file=temp.root.resolve("appearance.store")
        var scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        fun repository()=PreferencesRepository(DataStoreFactory.create(AppPreferencesSerializer,scope=scope,produceFile={file}))
        var repo=repository()
        val tuningFile=temp.root.resolve("tunings.store")
        val tuningRepo=TuningRepository(DataStoreFactory.create(TuningSettingsSerializer,scope=scope,produceFile={tuningFile}))
        val custom=TuningValidator.custom("custom-test","Drop pessoal",listOf(36,43,48,53,57,62))
        tuningRepo.save(custom)
        val tuningBytes=tuningFile.readBytes()
        assertEquals(AppPreferences(),repo.preferences.first())
        coroutineScope {
            launch {repo.headstock(HeadstockType.SIX_INLINE)}
            launch {repo.theme(ThemeMode.SYSTEM)}
            launch {repo.haptics(false)}
            launch {repo.stringAnimation(false)}
        }
        val expected=AppPreferences(HeadstockType.SIX_INLINE,ThemeMode.SYSTEM,false,false)
        assertEquals(expected,repo.preferences.first())
        assertArrayEquals(tuningBytes,tuningFile.readBytes())
        scope.cancel();scope.coroutineContext.job.join()
        scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler));repo=repository()
        try {
            assertEquals(expected,repo.preferences.first())
            repo.theme(ThemeMode.LIGHT)
            assertEquals(expected.copy(theme=ThemeMode.LIGHT),repo.preferences.first())
        } finally {scope.cancel();scope.coroutineContext.job.join()}
    }
    @Test fun `legacy model identifiers keep all existing preferences and new reverse restores`() = runTest {
        for(id in listOf("THREE_PER_SIDE","SIX_INLINE","SIX_INLINE_REVERSE")) {
            val raw="mytune-appearance-v1\nheadstock=$id\ntheme=LIGHT\nhaptics=false\nstringAnimation=false\n"
            val preferences=AppPreferencesSerializer.readFrom(ByteArrayInputStream(raw.toByteArray()))
            assertEquals(HeadstockType.valueOf(id),preferences.headstock)
            assertEquals(ThemeMode.LIGHT,preferences.theme);assertFalse(preferences.haptics);assertFalse(preferences.stringAnimation)
            val output=ByteArrayOutputStream();AppPreferencesSerializer.writeTo(preferences,output)
            assertEquals(raw,output.toString("UTF-8"))
        }
        val file=temp.root.resolve("reverse.store")
        var scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        var repo=PreferencesRepository(DataStoreFactory.create(AppPreferencesSerializer,scope=scope,produceFile={file}))
        repo.headstock(HeadstockType.SIX_INLINE_REVERSE);repo.haptics(false)
        scope.cancel();scope.coroutineContext.job.join()
        scope=CoroutineScope(SupervisorJob()+StandardTestDispatcher(testScheduler))
        repo=PreferencesRepository(DataStoreFactory.create(AppPreferencesSerializer,scope=scope,produceFile={file}))
        try {assertEquals(AppPreferences(headstock=HeadstockType.SIX_INLINE_REVERSE,haptics=false),repo.preferences.first())}
        finally {scope.cancel();scope.coroutineContext.job.join()}
    }
    @Test fun `malformed fields fall back individually and valid fields survive`() = runTest {
        val restored=AppPreferencesSerializer.readFrom(ByteArrayInputStream(
            "garbage\nheadstock=UNKNOWN\ntheme=LIGHT\nhaptics=invalid\nstringAnimation=false\n".toByteArray()))
        assertEquals(AppPreferences(theme=ThemeMode.LIGHT,stringAnimation=false),restored)
        val out=ByteArrayOutputStream();AppPreferencesSerializer.writeTo(restored,out)
        assertEquals(restored,AppPreferencesSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }
}
