package com.example.medicinreminder.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.medicinreminder.data.dao.*
import com.example.medicinreminder.data.entity.*

@Database(
    entities = [
        MedicineEntity::class,
        ReminderScheduleEntity::class,
        DoseLogEntity::class,
        UserEntitlementEntity::class,
        RemoteMedicineEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun reminderScheduleDao(): ReminderScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun userEntitlementDao(): UserEntitlementDao
    abstract fun remoteMedicineDao(): RemoteMedicineDao

    companion object {
        // Adds per-medicine reminder repeat configuration without losing existing user data.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE reminder_schedules ADD COLUMN alert_repeat_count INTEGER NOT NULL DEFAULT 2"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE remote_medicines ADD COLUMN purpose TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE remote_medicines ADD COLUMN dosageAndAdministration TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE remote_medicines ADD COLUMN adverseReactions TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE remote_medicines ADD COLUMN storageAndHandling TEXT NOT NULL DEFAULT ''")
            }
        }

        // Adds per-schedule spoken reminder control without deleting user schedules.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminder_schedules ADD COLUMN spoken_reminder_enabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medicine_reminder_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
