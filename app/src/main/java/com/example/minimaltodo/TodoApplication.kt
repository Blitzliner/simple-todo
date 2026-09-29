package com.example.minimaltodo

import android.app.Application
import android.content.Context
import androidx.room.withTransaction
import com.example.minimaltodo.data.local.AppDatabase
import com.example.minimaltodo.data.preferences.DataStoreSettingsRepository
import com.example.minimaltodo.data.preferences.SettingsRepository
import com.example.minimaltodo.data.repository.TodoRepository
import com.example.minimaltodo.data.repository.TransactionRunner
import com.example.minimaltodo.util.DocumentIo

class TodoApplication : Application() {
    /** Einfache manuelle Dependency Injection – kein DI-Framework nötig. */
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val todoRepository: TodoRepository by lazy {
        TodoRepository(
            dao = database.todoDao(),
            transaction = object : TransactionRunner {
                override suspend fun <R> run(block: suspend () -> R): R = database.withTransaction { block() }
            },
        )
    }

    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(appContext) }

    val documentIo: DocumentIo by lazy { DocumentIo(appContext.contentResolver) }
}
