package com.alihan.androidsharescreen.utils

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class AppModule {


    @Provides
    fun provideGson(): Gson =Gson()

    @Provides
    fun provideContext(@ApplicationContext context:Context )=context
}