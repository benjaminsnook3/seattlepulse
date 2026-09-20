package com.example.newworkspace.di

import com.example.newworkspace.data.SeattleRepositoryImpl
import com.example.newworkspace.domain.repository.SeattleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSeattleRepository(impl: SeattleRepositoryImpl): SeattleRepository
}
