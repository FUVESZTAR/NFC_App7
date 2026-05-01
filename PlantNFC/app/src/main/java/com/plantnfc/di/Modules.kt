package com.plantnfc.di

import android.content.Context
import androidx.room.Room
import com.plantnfc.data.local.PlantNfcDatabase
import com.plantnfc.data.local.dao.NfcRecordDao
import com.plantnfc.data.local.dao.PlantDao
import com.plantnfc.data.repository.NfcRecordRepositoryImpl
import com.plantnfc.data.repository.PlantRepositoryImpl
import com.plantnfc.domain.repository.NfcRecordRepository
import com.plantnfc.domain.repository.PlantRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PlantNfcDatabase =
        Room.databaseBuilder(ctx, PlantNfcDatabase::class.java, "plant_nfc.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePlantDao(db: PlantNfcDatabase): PlantDao = db.plantDao()
    @Provides fun provideNfcRecordDao(db: PlantNfcDatabase): NfcRecordDao = db.nfcRecordDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun bindPlantRepo(impl: PlantRepositoryImpl): PlantRepository
    @Binds @Singleton abstract fun bindNfcRecordRepo(impl: NfcRecordRepositoryImpl): NfcRecordRepository
}
