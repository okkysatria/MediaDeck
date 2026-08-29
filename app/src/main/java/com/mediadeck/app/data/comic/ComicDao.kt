package com.mediadeck.app.data.comic

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicDao {
    @Query("SELECT * FROM comics ORDER BY title ASC")
    fun getAllComics(): Flow<List<Comic>>

    @Query("SELECT * FROM comics")
    suspend fun getAllComicsDirect(): List<Comic>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun getComicById(id: Long): Comic?

    @Query("SELECT * FROM comic_pages WHERE comicId = :comicId ORDER BY pageIndex ASC")
    fun getComicPages(comicId: Long): Flow<List<ComicPage>>

    @Query("SELECT * FROM comic_pages WHERE comicId = :comicId ORDER BY pageIndex ASC")
    suspend fun getComicPagesDirect(comicId: Long): List<ComicPage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComic(comic: Comic): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComics(comics: List<Comic>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComicPages(pages: List<ComicPage>)

    @Update
    suspend fun updateComic(comic: Comic)

    @Delete
    suspend fun deleteComic(comic: Comic)

    @Query("DELETE FROM comics WHERE id = :id")
    suspend fun deleteComicById(id: Long)

    @Transaction
    suspend fun deleteComicWithPages(comicId: Long) {
        deleteComicPages(comicId)
        deleteComicById(comicId)
    }

    @Query("DELETE FROM comic_pages WHERE comicId = :comicId")
    suspend fun deleteComicPages(comicId: Long)

    @Query("DELETE FROM comics")
    suspend fun clearAllComics()

    @Query("DELETE FROM comic_pages")
    suspend fun clearAllComicPages()

    @Transaction
    suspend fun clearAllComicsAndPages() {
        clearAllComics()
        clearAllComicPages()
    }

    @Query("UPDATE comics SET currentPage = 0, lastReadTime = 0")
    suspend fun resetAllComicHistory()

    @Query("SELECT * FROM comics WHERE folderUri = :prefix OR folderUri LIKE :prefix || '/%'")
    suspend fun getComicsByFolderUriPrefixDirect(prefix: String): List<Comic>

    @Query("DELETE FROM comics WHERE folderUri = :prefix OR folderUri LIKE :prefix || '/%'")
    suspend fun deleteByFolderUriPrefix(prefix: String)

    @Query("SELECT * FROM comics WHERE parentFolderName = :folderName")
    fun getComicsByFolderName(folderName: String): List<Comic>
}
