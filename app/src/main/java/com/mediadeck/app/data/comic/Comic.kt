package com.mediadeck.app.data.comic

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "comics",
    indices = [Index(value = ["folderUri"], unique = true)]
)
data class Comic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val folderUri: String,
    val coverUri: String,
    val parentFolderName: String = "",
    val lastReadTime: Long = 0,
    val currentPage: Int = 0,
    val scrollOffset: Int = 0,
    val totalPages: Int = 0,
    val isFavorite: Boolean = false,
    val isReadLater: Boolean = false,
    val isCompleted: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis(),
    val tags: String = "",
)

@Entity(
    tableName = "comic_pages",
    foreignKeys = [ForeignKey(
        entity = Comic::class,
        parentColumns = ["id"],
        childColumns = ["comicId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("comicId")]
)
data class ComicPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val comicId: Long,
    val pageIndex: Int,
    val pageUri: String,
    val pageName: String
)
