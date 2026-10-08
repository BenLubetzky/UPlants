package com.uplants.app

import android.app.Application
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

class UPlantsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Lets ViewModel factories reach the [AppContainer]. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as UPlantsApplication).container
