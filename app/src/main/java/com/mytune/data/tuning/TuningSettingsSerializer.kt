package com.mytune.data.tuning

import androidx.datastore.core.Serializer
import com.mytune.domain.model.GuitarTuning
import com.mytune.domain.tuning.*
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/** Versioned UTF-8 records with Base64 fields. A bad row never invalidates another tuning.
 * No reflection, database, JSON runtime or generated serialization code is needed for six ints.
 */
object TuningSettingsSerializer : Serializer<TuningSettings> {
    override val defaultValue = TuningSettings()
    private const val HEADER = "mytune-tunings-v1"
    private fun encode(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decode(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

    override suspend fun readFrom(input: InputStream): TuningSettings {
        val lines = input.bufferedReader(Charsets.UTF_8).readLines()
        if (lines.isEmpty()) return defaultValue
        val custom = mutableListOf<GuitarTuning>()
        val recovered = mutableListOf<String>()
        var selected = TuningPresets.standard.id
        var revision = 0L
        var warning = lines.first() != HEADER
        for (line in lines) {
            if (line == HEADER || line.isBlank()) continue
            try {
                val fields = line.split('|')
                when (fields.first()) {
                    "selected" -> { require(fields.size == 2); selected = decode(fields[1]) }
                    "revision" -> { require(fields.size == 2); revision = fields[1].toLong().also { require(it >= 0) } }
                    "custom" -> {
                        require(fields.size == 4)
                        val tuning = TuningValidator.custom(decode(fields[1]), decode(fields[2]), fields[3].split(',').map { it.toInt() })
                        require(TuningValidator.error(tuning) == null && custom.none { it.id == tuning.id })
                        custom += tuning
                    }
                    "recovered" -> { require(fields.size == 2); recovered += decode(fields[1]); warning = true }
                    else -> { recovered += line; warning = true }
                }
            } catch (_: IllegalArgumentException) { recovered += line; warning = true }
        }
        if (TuningPresets.find(selected) == null && custom.none { it.id == selected }) {
            recovered += "selected|${encode(selected)}"
            selected = TuningPresets.standard.id
            warning = true
        }
        return TuningSettings(selected, custom.toList(), revision, recovered.distinct(), warning)
    }

    override suspend fun writeTo(t: TuningSettings, output: OutputStream) {
        val text = buildString {
            appendLine(HEADER)
            appendLine("revision|${t.revision}")
            appendLine("selected|${encode(t.selectedId)}")
            for (custom in t.customTunings) {
                require(custom.isCustom && TuningValidator.error(custom) == null)
                appendLine("custom|${encode(custom.id)}|${encode(custom.name)}|${custom.strings.sortedByDescending { it.number }.joinToString(",") { it.midi.toString() }}")
            }
            for (record in t.recoveredRecords) appendLine("recovered|${encode(record)}")
        }
        output.write(text.toByteArray(Charsets.UTF_8))
    }
}
