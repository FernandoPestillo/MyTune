package com.mytune.data.tuning

import android.content.Context
import androidx.datastore.dataStore

// One application-scoped DataStore per file, including across Activity recreation.
private val Context.tuningStore by dataStore(fileName = "tunings_v1_3.store", serializer = TuningSettingsSerializer)
fun tuningRepository(context: Context) = TuningRepository(context.applicationContext.tuningStore)
