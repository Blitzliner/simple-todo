package com.example.minimaltodo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

@Database(entities = [TodoEntity::class], version = AppDatabase.VERSION, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    companion object {
        const val NAME = "minimal_todo.db"
        const val VERSION = 1

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Bewusst KEIN fallbackToDestructiveMigration(): Daten dürfen nie verloren gehen.
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/**
 * Alle Schema-Migrationen. Bei einer neuen DB-Version:
 * 1. [AppDatabase.VERSION] erhöhen
 * 2. hier eine Migration(alt, neu) ergänzen (z. B. für Erinnerungen oder Anhänge)
 * 3. Schema-JSON in app/schemas wird automatisch exportiert und kann per MigrationTestHelper getestet werden.
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
