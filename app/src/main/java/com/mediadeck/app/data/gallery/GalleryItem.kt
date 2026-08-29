package com.mediadeck.app.data.gallery

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "gallery_items",
    indices = [androidx.room.Index(value = ["uri"], unique = true)]
)
data class GalleryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val folderName: String,
    val isFavorite: Boolean = false,
    val tags: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val duration: Long = 0L,
    val hasThumbnail: Boolean = false,
)
