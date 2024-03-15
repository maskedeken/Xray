package io.github.saeeddev94.xray.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.saeeddev94.xray.Xray

@Database(
    entities = [
        Profile::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class XrayDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao

    companion object {
        val instance by lazy {
            Room.databaseBuilder(Xray.application, XrayDatabase::class.java, "xray").build()
        }

        val profileDao get() = instance.profileDao()
    }
}
