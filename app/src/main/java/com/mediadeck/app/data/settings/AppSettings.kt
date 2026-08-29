package com.mediadeck.app.data.settings

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val theme: String = "dark",
    val autoHideReaderUi: Boolean = true,
    val parseTagNameFormat: String = "standard",
    val scanFormat: String = "Title - tags",
    val tagSeparator: String = "-",
    val tagDelimiter: String = ",",
    val stripNumericId: Boolean = true,
    val lowercaseTags: Boolean = true,
    val skipDuplicateScan: Boolean = true,
    val showSideScrollbar: Boolean = true,
    val videoThumbnails: Boolean = true,
    val floatingScanStatus: Boolean = true,
    val defaultGallerySort: String = "date_desc",
    val defaultComicSort: String = "name_asc",
    val defaultMovieSort: String = "name_asc",
    val prioritizeLocalScan: Boolean = true,
    val hideOfflineSmb: Boolean = true,
    val language: String = "en",
    val keepScreenOn: Boolean = true,
    val verticalPageGap: String = "none",
    val gridColumns: Int = 2,
    val layoutMode: String = "grid",
    val galleryGridType: String = "uniform",
    val autoManageCache: Boolean = true,
    val cacheSizeLimitMB: Int = 200,
    val cachePurgeTargetMB: Int = 100,
    val defaultReaderMode: String = "vertical",
    val readerVolumeKeysNavigation: Boolean = true,
    val defaultVideoZoomMode: Int = 0,
    val defaultVideoSpeed: Float = 1.0f,
    val defaultVideoOrientation: Int = 0,
    val videoSkipInterval: Int = 10,
    val autoScanOnStart: Boolean = true,
    val libraryFolders: String = "",
    val comicFolders: String = "",
    val galleryFolders: String = "",
    val movieFolders: String = "",
    val smbHost: String = "",
    val smbShare: String = "",
    val smbUser: String = "",
    val smbPass: String = "",
    val smbDomain: String = "",
    val smbPort: String = "445",
    val smbEnableSMB2: Boolean = true,
    val smbDisableSMB1: Boolean = false,
    val smbConnTimeout: Int = 5000,
    val smbSoTimeout: Int = 10000,
    val hideScannedFromGallery: Boolean = false,
    val enablePiP: Boolean = true,
    val smbIsGuest: Boolean = false,
)

@Entity(tableName = "scanned_folders")
data class ScannedFolder(
    @PrimaryKey val folderUri: String,
    val lastModified: Long
)
