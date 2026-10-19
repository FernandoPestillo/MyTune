package com.mytune.data.tuning

import androidx.datastore.core.DataStoreFactory
import com.mytune.domain.tuning.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TuningRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun `real DataStore creates edits restores and deletes active custom tuning`() = runTest {
        val file = temp.root.resolve("tunings.store")
        var scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        fun repository() = TuningRepository(DataStoreFactory.create(TuningSettingsSerializer, scope = scope, produceFile = { file }))
        var repo = repository()
        val custom = TuningValidator.custom("custom-one", "Minha ♯ afinação", listOf(36,43,48,53,57,62))
        assertEquals(TuningPresets.standard, repo.settings.first().active)
        assertEquals(custom, repo.save(custom).active)
        val edited = custom.copy(name = "Nome novo", strings = custom.strings.map { if (it.number == 6) it.copy(midi = 38, name = "D") else it })
        repo.save(edited)
        scope.cancel(); scope.coroutineContext.job.join()
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)); repo = repository()
        assertEquals(edited, repo.settings.first().active)
        assertEquals(listOf(edited), repo.settings.first().customTunings)
        assertEquals(TuningPresets.standard, repo.delete(edited.id).active)
        scope.cancel(); scope.coroutineContext.job.join()
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)); repo = repository()
        assertTrue(repo.settings.first().customTunings.isEmpty())
        assertEquals(TuningPresets.standard, repo.settings.first().active)
        scope.cancel(); scope.coroutineContext.job.join()
    }
    @Test fun `selection persists and concurrent transactions do not lose unrelated custom items`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val store = DataStoreFactory.create(TuningSettingsSerializer, scope = scope, produceFile = { temp.root.resolve("race.store") })
        val repo = TuningRepository(store)
        try {
            coroutineScope { repeat(12) { i -> launch { repo.save(TuningValidator.custom("custom-$i", "Afinação $i", listOf(36,43,48,53,57,62))) } } }
            assertEquals(12, repo.settings.first().customTunings.size)
            repo.select("drop-d"); repo.select("drop-c")
            assertEquals("drop-c", repo.settings.first().selectedId)
            assertEquals(14, repo.settings.first().revision)
            try { repo.delete("drop-c"); fail("preset deletion") } catch (_: IllegalArgumentException) { }
            try { repo.save(TuningPresets.standard); fail("preset edit") } catch (_: IllegalArgumentException) { }
            assertEquals(12, repo.settings.first().customTunings.size)
        } finally { scope.cancel(); scope.coroutineContext.job.join() }
    }
    @Test fun `one invalid record is quarantined while valid tunings and selection survive rewriting`() = runTest {
        val custom = TuningValidator.custom("custom-good", "Válida | ç", listOf(36,43,48,53,57,62))
        val output = ByteArrayOutputStream()
        TuningSettingsSerializer.writeTo(TuningSettings(custom.id, listOf(custom)), output)
        val damaged = output.toString("UTF-8") + "custom|broken|%%%%|36,43\ncustom|%%bad%%|bmFtZQ|36,43,48,53,57,62\nunknown|keep\n"
        val restored = TuningSettingsSerializer.readFrom(ByteArrayInputStream(damaged.toByteArray()))
        assertEquals(custom, restored.active); assertEquals(listOf(custom), restored.customTunings)
        assertTrue(restored.hasRecoveryWarning); assertEquals(3, restored.recoveredRecords.size)
        val rewritten = ByteArrayOutputStream(); TuningSettingsSerializer.writeTo(restored, rewritten)
        val reopened = TuningSettingsSerializer.readFrom(ByteArrayInputStream(rewritten.toByteArray()))
        assertEquals(restored, reopened)
        assertFalse(rewritten.toString("UTF-8").contains("e-standard")) // No preset copies.
    }
    @Test fun `corrupt header missing selection and incomplete notes fall back without startup failure`() = runTest {
        val restored = TuningSettingsSerializer.readFrom(ByteArrayInputStream("garbage\nselected|eA\nrevision|oops\ncustom|eA|eA|1,2\n".toByteArray()))
        assertEquals(TuningPresets.standard, restored.active)
        assertTrue(restored.customTunings.isEmpty()); assertTrue(restored.hasRecoveryWarning)
        assertTrue(restored.recoveredRecords.isNotEmpty())
    }
}
