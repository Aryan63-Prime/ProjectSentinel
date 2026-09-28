package com.sentinel.host.data.di

import android.content.Context
import androidx.room.Room
import com.sentinel.host.data.db.LocationDao
import com.sentinel.host.data.db.SentinelDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSentinelDatabase(
        @ApplicationContext context: Context
    ): SentinelDatabase {
        return Room.databaseBuilder(
            context,
            SentinelDatabase::class.java,
            "sentinel_telemetry.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideLocationDao(database: SentinelDatabase): LocationDao {
        return database.locationDao()
    }
}
