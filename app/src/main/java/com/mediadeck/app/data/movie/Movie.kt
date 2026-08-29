package com.mediadeck.app.data.movie

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "movies",
    indices = [androidx.room.Index(value = ["uri"], unique = true)]
)
data class Movie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val uri: String,
    val size: Long = 0,
    val duration: Long = 0,
    val resolution: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayedPosition: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val folderName: String = "",
    val isFavorite: Boolean = false,
    val subtitleUri: String? = null,
    val audioTrackIndex: Int = -1,
    val subtitleTrackIndex: Int = -1,
    val orientation: Int = 0,
    val zoomMode: Int = 0,
    val tags: String = "",
    val hasThumbnail: Boolean = false,
)
