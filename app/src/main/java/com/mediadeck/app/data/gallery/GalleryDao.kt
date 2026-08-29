package com.mediadeck.app.data.gallery

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GalleryDao {
    @Query("SELECT * FROM gallery_items ORDER BY dateAdded DESC")
    fun getAllGalleryItems(): Flow<List<GalleryItem>>

    @Query("SELECT * FROM gallery_items")
    suspend fun getAllGalleryItemsDirect(): List<GalleryItem>

    @Query("SELECT * FROM gallery_items WHERE id = :id LIMIT 1")
    suspend fun getGalleryItemById(id: Long): GalleryItem?

    @Query("SELECT * FROM gallery_items WHERE folderName = :folderName")
    suspend fun getGalleryItemsByFolderName(folderName: String): List<GalleryItem>

    @Query("SELECT * FROM gallery_items WHERE uri = :prefix OR uri LIKE :prefix || '/%'")
    suspend fun getGalleryItemsByUriPrefixDirect(prefix: String): List<GalleryItem>

    @Query("SELECT * FROM gallery_items WHERE name = :name AND size = :size LIMIT 1")
    suspend fun getGalleryItemByNameAndSize(name: String, size: Long): GalleryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGalleryItem(item: GalleryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGalleryItems(items: List<GalleryItem>): List<Long>

    @Update
    suspend fun updateGalleryItem(item: GalleryItem)

    @Delete
    suspend fun deleteGalleryItem(item: GalleryItem)

    @Query("DELETE FROM gallery_items WHERE id IN (:ids)")
    suspend fun deleteGalleryItemsByIds(ids: List<Long>)

    @Query("DELETE FROM gallery_items")
    suspend fun clearAllGalleryItems()

    @Transaction
    suspend fun clearAllGalleryItemsTransaction() {
        clearAllGalleryItems()
    }

    @Query("DELETE FROM gallery_items WHERE folderName = :folderName")
    suspend fun deleteByFolderName(folderName: String)

    @Query("DELETE FROM gallery_items WHERE uri = :prefix OR uri LIKE :prefix || '/%'")
    suspend fun deleteByUriPrefix(prefix: String)

    @Query("UPDATE gallery_items SET duration = CASE WHEN :duration > 0 THEN :duration ELSE duration END, width = CASE WHEN :width > 0 THEN :width ELSE width END, height = CASE WHEN :height > 0 THEN :height ELSE height END, hasThumbnail = :hasThumbnail WHERE uri = :uri")
    suspend fun updateGalleryMetadata(uri: String, duration: Long, width: Int, height: Int, hasThumbnail: Boolean)
}
