package com.mediadeck.app.data.settings

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettingsDirect(): AppSettings?

    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsSync(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AppSettings)

    @Query("SELECT * FROM scanned_folders")
    suspend fun getAllScannedFolders(): List<ScannedFolder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedFolder(scannedFolder: ScannedFolder)

    @Query("DELETE FROM scanned_folders WHERE folderUri = :uri")
    suspend fun deleteScannedFolder(uri: String)

    @Query("DELETE FROM scanned_folders WHERE folderUri = :prefix OR folderUri LIKE :prefix || '/%'")
    suspend fun deleteScannedFoldersByPrefix(prefix: String)

    @Query("DELETE FROM scanned_folders")
    suspend fun clearAllScannedFolders()
}
