package com.mediadeck.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mediadeck.app.data.comic.Comic
import com.mediadeck.app.data.comic.ComicDao
import com.mediadeck.app.data.comic.ComicPage
import com.mediadeck.app.data.gallery.GalleryDao
import com.mediadeck.app.data.gallery.GalleryItem
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.data.movie.MovieDao
import com.mediadeck.app.data.settings.AppSettings
import com.mediadeck.app.data.settings.ScannedFolder
import com.mediadeck.app.data.settings.SettingsDao

@Database(
    entities = [
        Comic::class,
        ComicPage::class,
        GalleryItem::class,
        AppSettings::class,
        Movie::class,
        ScannedFolder::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
    abstract fun galleryDao(): GalleryDao
    abstract fun settingsDao(): SettingsDao
    abstract fun movieDao(): MovieDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "comic_reader_database"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("INSERT OR IGNORE INTO app_settings (id) VALUES (1)")
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
