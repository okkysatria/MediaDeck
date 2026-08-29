package com.mediadeck.app.data

import android.content.Context
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
import com.mediadeck.app.util.media.VideoThumbnailHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class AppRepository(
    private val comicDao: ComicDao,
    private val galleryDao: GalleryDao,
    private val settingsDao: SettingsDao,
    private val movieDao: MovieDao,
) {

    data class FolderRemovalSummary(val mediaCount: Int)

    private fun deletePhysicalThumbnail(context: Context, mediaId: Long) {
        if (mediaId <= 0L) return
        try {
            val thumbFolder = File(context.filesDir, "thumbnails")
            if (thumbFolder.exists()) {
                listOf("explore", "view").forEach { variant ->
                    val filename = VideoThumbnailHelper.getCacheFilename(mediaId, variant)
                    val file = File(thumbFolder, filename)
                    if (file.exists()) file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearAllThumbnails(context: Context) {
        try {
            val thumbFolder = File(context.filesDir, "thumbnails")
            if (thumbFolder.exists()) {
                thumbFolder.listFiles()?.forEach { it.delete() }
            }
            VideoThumbnailHelper.clearMemoryCache()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val allComics: Flow<List<Comic>> = comicDao.getAllComics()

    suspend fun getAllComicsDirect(): List<Comic> = comicDao.getAllComicsDirect()

    suspend fun getComicById(id: Long): Comic? = comicDao.getComicById(id)

    fun getComicPages(comicId: Long): Flow<List<ComicPage>> = comicDao.getComicPages(comicId)

    suspend fun getComicPagesDirect(comicId: Long): List<ComicPage> = comicDao.getComicPagesDirect(comicId)

    suspend fun insertComic(comic: Comic): Long = comicDao.insertComic(comic)

    suspend fun insertComics(comics: List<Comic>) = comicDao.insertComics(comics)

    suspend fun insertComicPages(pages: List<ComicPage>) = comicDao.insertComicPages(pages)

    suspend fun updateComic(comic: Comic) = comicDao.updateComic(comic)

    suspend fun deleteComic(context: Context, comic: Comic) {
        deletePhysicalThumbnail(context, comic.id)
        comicDao.deleteComicWithPages(comic.id)
    }

    suspend fun deleteComicById(context: Context, id: Long) {
        deletePhysicalThumbnail(context, id)
        comicDao.deleteComicWithPages(id)
    }

    suspend fun clearAllComics(context: Context) {
        getAllComicsDirect().forEach { comic ->
            deletePhysicalThumbnail(context, comic.id)
        }
        VideoThumbnailHelper.clearMemoryCache()
        comicDao.clearAllComicsAndPages()
        val settings = getSettingsDirect()
        settings.comicFolders.split(",").filter { it.isNotEmpty() }.forEach { uri ->
            settingsDao.deleteScannedFoldersByPrefix(uri.removeSuffix("/"))
        }
    }

    suspend fun resetAllComicHistory() = comicDao.resetAllComicHistory()

    val allGalleryItems: Flow<List<GalleryItem>> = galleryDao.getAllGalleryItems()

    suspend fun getAllGalleryItemsDirect(): List<GalleryItem> = galleryDao.getAllGalleryItemsDirect()

    suspend fun insertGalleryItem(item: GalleryItem): Long = galleryDao.insertGalleryItem(item)

    suspend fun insertGalleryItems(items: List<GalleryItem>): List<Long> = galleryDao.insertGalleryItems(items)

    suspend fun updateGalleryItem(item: GalleryItem) = galleryDao.updateGalleryItem(item)

    suspend fun deleteGalleryItem(context: Context, item: GalleryItem) {
        deletePhysicalThumbnail(context, item.id)
        galleryDao.deleteGalleryItem(item)
    }

    suspend fun deleteGalleryItemsByIds(context: Context, ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { id ->
            deletePhysicalThumbnail(context, id)
        }
        ids.chunked(900).forEach { batch ->
            galleryDao.deleteGalleryItemsByIds(batch)
        }
    }

    suspend fun updateGalleryMetadata(uri: String, duration: Long, width: Int, height: Int, hasThumbnail: Boolean) {
        galleryDao.updateGalleryMetadata(uri, duration, width, height, hasThumbnail)
    }

    suspend fun clearAllGalleryItems(context: Context) {
        getAllGalleryItemsDirect().forEach { item ->
            deletePhysicalThumbnail(context, item.id)
        }
        VideoThumbnailHelper.clearMemoryCache()
        galleryDao.clearAllGalleryItemsTransaction()
        val settings = getSettingsDirect()
        settings.galleryFolders.split(",").filter { it.isNotEmpty() }.forEach { uri ->
            settingsDao.deleteScannedFoldersByPrefix(uri.removeSuffix("/"))
        }
    }

    val appSettings: Flow<AppSettings> = settingsDao.getSettingsFlow().map { it ?: AppSettings() }

    suspend fun getSettingsDirect(): AppSettings = settingsDao.getSettingsDirect() ?: AppSettings()

    suspend fun updateSettings(settings: AppSettings) = settingsDao.insertOrUpdateSettings(settings)

    suspend fun getScannedFolders(): List<ScannedFolder> = settingsDao.getAllScannedFolders()
    suspend fun insertScannedFolder(folder: ScannedFolder) = settingsDao.insertScannedFolder(folder)
    suspend fun deleteScannedFolder(uri: String) = settingsDao.deleteScannedFolder(uri)

    val allMovies: Flow<List<Movie>> = movieDao.getAllMovies()
    suspend fun getAllMoviesDirect(): List<Movie> = movieDao.getAllMoviesDirect()
    suspend fun insertMovie(movie: Movie): Long = movieDao.insertMovie(movie)
    suspend fun insertMovies(movies: List<Movie>): List<Long> = movieDao.insertMovies(movies)
    suspend fun updateMovie(movie: Movie) = movieDao.updateMovie(movie)
    suspend fun deleteMovie(context: Context, movie: Movie) {
        deletePhysicalThumbnail(context, movie.id)
        movieDao.deleteMovie(movie)
    }
    suspend fun deleteMoviesByIds(context: Context, ids: List<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { id ->
            deletePhysicalThumbnail(context, id)
        }
        ids.chunked(900).forEach { batch ->
            movieDao.deleteMoviesByIds(batch)
        }
    }

    suspend fun clearAllMovies(context: Context) {
        getAllMoviesDirect().forEach { movie ->
            deletePhysicalThumbnail(context, movie.id)
        }
        VideoThumbnailHelper.clearMemoryCache()
        movieDao.clearAllMoviesTransaction()
        val settings = getSettingsDirect()
        settings.movieFolders.split(",").filter { it.isNotEmpty() }.forEach { uri ->
            settingsDao.deleteScannedFoldersByPrefix(uri.removeSuffix("/"))
        }
    }
    suspend fun updateMovieMetadata(uri: String, duration: Long, resolution: String, hasThumbnail: Boolean) = movieDao.updateMovieMetadata(uri, duration, resolution, hasThumbnail)
    suspend fun resetAllMovieHistory() = movieDao.resetAllMovieHistory()

    suspend fun getMoviesByFolderName(folderName: String): List<Movie> = movieDao.getMoviesByFolderName(folderName)
    suspend fun getGalleryItemsByFolderName(folderName: String): List<GalleryItem> = galleryDao.getGalleryItemsByFolderName(folderName)
    suspend fun getComicsByFolderName(folderName: String): List<Comic> = comicDao.getComicsByFolderName(folderName)

    suspend fun getMoviesUnderFolder(folderUri: String): List<Movie> =
        movieDao.getMoviesByUriPrefixDirect(folderUri.removeSuffix("/"))

    suspend fun getGalleryItemsUnderFolder(folderUri: String): List<GalleryItem> =
        galleryDao.getGalleryItemsByUriPrefixDirect(folderUri.removeSuffix("/"))

    suspend fun deleteMediaByFolderUri(context: Context, folderUri: String) {
        val normalizedUri = folderUri.removeSuffix("/")

        val moviesInFolder = movieDao.getMoviesByUriPrefixDirect(normalizedUri)
        moviesInFolder.forEach { deletePhysicalThumbnail(context, it.id) }

        val galleryInFolder = galleryDao.getGalleryItemsByUriPrefixDirect(normalizedUri)
        galleryInFolder.forEach { deletePhysicalThumbnail(context, it.id) }

        val comicsInFolder = comicDao.getComicsByFolderUriPrefixDirect(normalizedUri)
        comicsInFolder.forEach { deletePhysicalThumbnail(context, it.id) }

        comicDao.deleteByFolderUriPrefix(normalizedUri)
        galleryDao.deleteByUriPrefix(normalizedUri)
        movieDao.deleteByUriPrefix(normalizedUri)
        settingsDao.deleteScannedFoldersByPrefix(normalizedUri)
    }

    suspend fun getFolderRemovalSummary(folderUri: String): FolderRemovalSummary {
        val normalizedUri = folderUri.removeSuffix("/")
        return FolderRemovalSummary(
            mediaCount = movieDao.getMoviesByUriPrefixDirect(normalizedUri).size +
                galleryDao.getGalleryItemsByUriPrefixDirect(normalizedUri).size +
                comicDao.getComicsByFolderUriPrefixDirect(normalizedUri).size,
        )
    }

    fun getCacheSettings(): Flow<CacheSettings> {
        return settingsDao.getSettingsFlow().map { settings ->
            CacheSettings(
                limitMB = settings?.cacheSizeLimitMB ?: 200,
                targetMB = settings?.cachePurgeTargetMB ?: 100
            )
        }
    }

    suspend fun getCacheSettingsSync(): CacheSettings {
        val settings = settingsDao.getSettingsDirect()
        return CacheSettings(
            limitMB = settings?.cacheSizeLimitMB ?: 200,
            targetMB = settings?.cachePurgeTargetMB ?: 100
        )
    }
}

data class CacheSettings(
    val limitMB: Int,
    val targetMB: Int
)
