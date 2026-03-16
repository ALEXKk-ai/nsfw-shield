package com.nsfwshield.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nsfwshield.app.core.profiles.FilterProfile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Main Room database for NSFW Shield.
 * All data is stored locally and can be encrypted via the EncryptedLogStore layer.
 */
@Database(
    entities = [
        FilterProfile::class,
        ActivityLogEntry::class,
        BlockedDomain::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun blockedDomainDao(): BlockedDomainDao
}

/**
 * Hilt module providing database and DAO instances.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "nsfw_shield_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideActivityLogDao(database: AppDatabase): ActivityLogDao = database.activityLogDao()

    @Provides
    fun provideBlockedDomainDao(database: AppDatabase): BlockedDomainDao = database.blockedDomainDao()
}
