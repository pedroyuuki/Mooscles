package com.mooscles

import android.app.Application
import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** The Android view model: [MoosclesViewModel] with the app's services, plus the installer intents. */
class AppViewModel private constructor(
    private val app: MoosclesApplication,
    platform: AndroidViewModelPlatform,
) : MoosclesViewModel(app.services, platform) {
    constructor(application: Application) : this(application as MoosclesApplication, AndroidViewModelPlatform())

    private val installLaunches = Channel<Intent>(Channel.BUFFERED)
    val installIntents = installLaunches.receiveAsFlow()
    /** Host-test probe: presentations finished, and how many of them ran on the main thread. */
    @VisibleForTesting internal val presentedCount = platform.presentedCount
    @VisibleForTesting internal val presentedOnMainThread = platform.presentedOnMainThread

    /** The application this view model works with, as `AndroidViewModel.getApplication` returned it before. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Application> getApplication(): T = app as T

    override suspend fun install() { app.updates.requestInstall()?.let { installLaunches.send(it) } }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(this[APPLICATION_KEY]!!) }
        }
    }
}
