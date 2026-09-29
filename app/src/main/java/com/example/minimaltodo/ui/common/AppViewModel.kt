package com.example.minimaltodo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.minimaltodo.AppContainer
import com.example.minimaltodo.TodoApplication

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as TodoApplication).container

/** Erstellt ein ViewModel mit Zugriff auf den [AppContainer] (ohne DI-Framework). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    noinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}
