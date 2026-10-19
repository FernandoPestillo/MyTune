package com.mytune.presentation

import androidx.datastore.core.DataStore
import androidx.lifecycle.*
import com.mytune.data.preferences.PreferencesRepository
import com.mytune.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val owner=ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { owner.clear(); Dispatchers.resetMain() }
    private class Store(var readFailure:Boolean=false):DataStore<AppPreferences> {
        val value=MutableStateFlow(AppPreferences())
        var writeFailure=false
        override val data:Flow<AppPreferences> get()=if(readFailure) flow {throw IOException("read")} else value
        override suspend fun updateData(transform:suspend(AppPreferences)->AppPreferences):AppPreferences {
            if(writeFailure)throw IOException("write")
            return transform(value.value).also{value.value=it}
        }
    }
    private fun model(store:Store):PreferencesViewModel {
        val vm=PreferencesViewModel(PreferencesRepository(store))
        return ViewModelProvider(owner,object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun<T:ViewModel> create(modelClass:Class<T>):T=vm as T
        })[PreferencesViewModel::class.java]
    }
    @Test fun `rapid writes preserve all fields and publish committed preferences`()=runTest(dispatcher) {
        val vm=model(Store());runCurrent();assertTrue(vm.state.value.loaded)
        vm.headstock(HeadstockType.SIX_INLINE);vm.theme(ThemeMode.LIGHT);vm.haptics(false);vm.stringAnimation(false)
        advanceUntilIdle()
        assertEquals(AppPreferences(HeadstockType.SIX_INLINE,ThemeMode.LIGHT,false,false),vm.state.value.preferences)
        assertFalse(vm.state.value.saving);assertNull(vm.state.value.error)
    }
    @Test fun `disk write failure keeps old preference and a subsequent action recovers`()=runTest(dispatcher) {
        val store=Store();val vm=model(store);runCurrent()
        store.writeFailure=true;vm.theme(ThemeMode.LIGHT);advanceUntilIdle()
        assertEquals(ThemeMode.DARK,vm.state.value.preferences.theme);assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.saving)
        store.writeFailure=false;vm.theme(ThemeMode.LIGHT);advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT,vm.state.value.preferences.theme);assertNull(vm.state.value.error)
    }
    @Test fun `read error is visible and successful write updates UI after collector failed`()=runTest(dispatcher) {
        val vm=model(Store(readFailure=true));runCurrent()
        assertTrue(vm.state.value.loaded);assertNotNull(vm.state.value.error)
        vm.headstock(HeadstockType.SIX_INLINE);advanceUntilIdle()
        assertEquals(HeadstockType.SIX_INLINE,vm.state.value.preferences.headstock)
        assertNull(vm.state.value.error)
    }
}
