package com.mediadeck.app.data.movie

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    @Query("SELECT * FROM movies ORDER BY title ASC")
    fun getAllMovies(): Flow<List<Movie>>

    @Query("SELECT * FROM movies")
    suspend fun getAllMoviesDirect(): List<Movie>

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun getMovieById(id: Long): Movie?

    @Query("SELECT * FROM movies WHERE folderName = :folderName")
    suspend fun getMoviesByFolderName(folderName: String): List<Movie>

    @Query("SELECT * FROM movies WHERE uri = :prefix OR uri LIKE :prefix || '/%'")
    suspend fun getMoviesByUriPrefixDirect(prefix: String): List<Movie>

    @Query("SELECT * FROM movies WHERE uri = :uri LIMIT 1")
    suspend fun getMovieByUri(uri: String): Movie?

    @Query("SELECT * FROM movies WHERE title = :title AND size = :size LIMIT 1")
    suspend fun getMovieByTitleAndSize(title: String, size: Long): Movie?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: Movie): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<Movie>): List<Long>

    @Update
    suspend fun updateMovie(movie: Movie)

    @Delete
    suspend fun deleteMovie(movie: Movie)

    @Query("DELETE FROM movies WHERE id = :id")
    suspend fun deleteMovieById(id: Long)

    @Query("DELETE FROM movies WHERE id IN (:ids)")
    suspend fun deleteMoviesByIds(ids: List<Long>)

    @Query("DELETE FROM movies")
    suspend fun clearAllMovies()

    @Transaction
    suspend fun clearAllMoviesTransaction() {
        clearAllMovies()
    }

    @Query("UPDATE movies SET duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END, resolution = CASE WHEN :resolution != '' THEN :resolution ELSE resolution END, hasThumbnail = :hasThumbnail WHERE uri = :uri")
    suspend fun updateMovieMetadata(uri: String, duration: Long, resolution: String, hasThumbnail: Boolean)

    @Query("UPDATE movies SET lastPlayedPosition = 0")
    suspend fun resetAllMovieHistory()

    @Query("DELETE FROM movies WHERE uri = :prefix OR uri LIKE :prefix || '/%'")
    suspend fun deleteByUriPrefix(prefix: String)
}
