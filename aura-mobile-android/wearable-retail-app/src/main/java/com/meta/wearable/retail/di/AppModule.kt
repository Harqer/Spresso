package com.meta.wearable.retail.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.meta.wearable.retail.RetailSessionManager
import com.meta.wearable.retail.ui.ProductRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideProductRepository(): ProductRepository = ProductRepository()

    @Provides
    @Singleton
    fun provideRetailSessionManager(@ApplicationContext context: Context): RetailSessionManager =
        RetailSessionManager(context)
}
