package com.mediadeck.app.di

import android.content.Context
import com.mediadeck.app.data.AppRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun repository(): AppRepository

    companion object {
        fun get(context: Context): RepositoryEntryPoint {
            return EntryPointAccessors.fromApplication(context.applicationContext, RepositoryEntryPoint::class.java)
        }
    }
}
