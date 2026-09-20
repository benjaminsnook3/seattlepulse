package com.example.newworkspace.di

import android.content.Context
import com.example.newworkspace.notifications.PreferencesSeenAlertsStore
import com.example.newworkspace.notifications.SeattlePulseNotificationManager
import com.example.newworkspace.notifications.SeenAlertsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): SeattlePulseNotificationManager =
        SeattlePulseNotificationManager(context)

    @Provides
    @Singleton
    fun provideSeenAlertsStore(@ApplicationContext context: Context): SeenAlertsStore =
        PreferencesSeenAlertsStore(context)
}
