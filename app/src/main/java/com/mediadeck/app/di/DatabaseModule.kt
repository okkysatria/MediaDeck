package com.mediadeck.app.di

import android.content.Context
import com.mediadeck.app.data.AppDatabase
import com.mediadeck.app.data.AppRepository
import com.mediadeck.app.data.comic.ComicDao
import com.mediadeck.app.data.gallery.GalleryDao
import com.mediadeck.app.data.movie.MovieDao
import com.mediadeck.app.data.settings.SettingsDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideComicDao(database: AppDatabase): ComicDao = database.comicDao()

    @Provides
    fun provideGalleryDao(database: AppDatabase): GalleryDao = database.galleryDao()

    @Provides
    fun provideMovieDao(database: AppDatabase): MovieDao = database.movieDao()

    @Provides
    fun provideSettingsDao(database: AppDatabase): SettingsDao = database.settingsDao()

    @Provides
    @Singleton
    fun provideAppRepository(
        comicDao: ComicDao,
        galleryDao: GalleryDao,
        settingsDao: SettingsDao,
        movieDao: MovieDao
    ): AppRepository {
        return AppRepository(comicDao, galleryDao, settingsDao, movieDao)
    }
}
