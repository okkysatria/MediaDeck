package com.mediadeck.app.util.smb

import android.content.Context
import com.mediadeck.app.data.AppDatabase
import com.mediadeck.app.data.settings.AppSettings
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Properties

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.mediadeck.app.data.settings.SettingsDao

object SmbConnectionManager {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmbManagerEntryPoint {
        fun settingsDao(): SettingsDao
        fun smbCredentialStore(): SmbCredentialStore
    }

    private val mutex = Mutex()
    @Volatile private var cachedContext: CIFSContext? = null
    @Volatile private var lastSettingsHash: Int = 0
    private var successfulAuthType: Int = -1

    private const val AUTH_USER = 0
    private const val AUTH_GUEST_UPPER = 1
    private const val AUTH_ANON = 2
    private const val AUTH_GUEST_LOWER = 3

    fun getCachedContext(): CIFSContext? = cachedContext

    suspend fun getContext(context: Context, providedSettings: AppSettings? = null): CIFSContext {
        val databaseSettings = providedSettings ?: withContext(Dispatchers.IO) {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                SmbManagerEntryPoint::class.java
            )
            entryPoint.settingsDao().getSettingsSync()
        } ?: AppSettings()

        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            SmbManagerEntryPoint::class.java
        )
        val credentialStore = entryPoint.smbCredentialStore()
        val password = credentialStore.getPassword()
        val settings = databaseSettings.copy(smbPass = password)

        val configHash = calculateConfigHash(settings)
        val currentCached = cachedContext
        if (currentCached != null && configHash == lastSettingsHash) {
            return currentCached
        }

        return mutex.withLock {
            if (cachedContext != null && configHash == lastSettingsHash) {
                return@withLock cachedContext!!
            }

            val baseContext = createBaseContext(settings)
            val authTries = getAuthPriority(settings)

            var lastError: Exception? = null
            for (authType in authTries) {
                try {
                    val auth = createAuthenticator(authType, settings)
                    val testContext = baseContext.withCredentials(auth)

                    cachedContext = testContext
                    lastSettingsHash = configHash
                    successfulAuthType = authType
                    return@withLock testContext
                } catch (e: Exception) {
                    lastError = e
                }
            }

            throw lastError ?: Exception("Failed to initialize SMB context")
        }
    }

    suspend fun invalidateAndRetry(context: Context): CIFSContext {
        mutex.withLock {
            cachedContext = null
            lastSettingsHash = 0
            successfulAuthType = -1
        }
        return getContext(context)
    }


    private fun getAuthPriority(settings: AppSettings): List<Int> {
        if (successfulAuthType != -1) {
            val list = mutableListOf(successfulAuthType)
            val others = listOf(AUTH_USER, AUTH_GUEST_UPPER, AUTH_ANON, AUTH_GUEST_LOWER).filter { it != successfulAuthType }
            list.addAll(others)
            return list
        }

        val tries = mutableListOf<Int>()
        if (settings.smbIsGuest) {
            tries.add(AUTH_GUEST_UPPER)
            tries.add(AUTH_ANON)
            tries.add(AUTH_GUEST_LOWER)
            tries.add(AUTH_USER)
        } else {
            if (settings.smbUser.isNotEmpty()) {
                tries.add(AUTH_USER)
            }
            tries.add(AUTH_GUEST_UPPER)
            tries.add(AUTH_ANON)
            tries.add(AUTH_GUEST_LOWER)
        }
        return tries
    }

    private fun createAuthenticator(type: Int, settings: AppSettings): NtlmPasswordAuthenticator {
        return when (type) {
            AUTH_USER -> NtlmPasswordAuthenticator(settings.smbDomain.takeIf { it.isNotEmpty() }, settings.smbUser, settings.smbPass)
            AUTH_GUEST_UPPER -> NtlmPasswordAuthenticator(null, "GUEST", "")
            AUTH_GUEST_LOWER -> NtlmPasswordAuthenticator(null, "guest", "")
            else -> NtlmPasswordAuthenticator("", "", "")
        }
    }

    private fun createBaseContext(settings: AppSettings): CIFSContext {
        val prop = Properties()
        prop.setProperty("jcifs.smb.client.enableSMB2", "true")
        prop.setProperty("jcifs.smb.client.disableSMB1", "true")
        prop.setProperty("jcifs.smb.client.minVersion", "SMB202")
        prop.setProperty("jcifs.smb.client.maxVersion", "SMB311")
        
        prop.setProperty("jcifs.smb.client.dfs.disabled", "true")
        prop.setProperty("jcifs.smb.client.connTimeout", settings.smbConnTimeout.toString())
        prop.setProperty("jcifs.smb.client.soTimeout", "35000")
        prop.setProperty("jcifs.smb.client.responseTimeout", "30000")

        prop.setProperty("jcifs.smb.client.rcv_buf_size", "4194304") 
        prop.setProperty("jcifs.smb.client.snd_buf_size", "1048576")
        prop.setProperty("jcifs.smb.client.maxMpxCount", "256")      
        prop.setProperty("jcifs.smb.client.signingPreferred", "false")
        prop.setProperty("jcifs.smb.client.signingEnforced", "false")
        prop.setProperty("jcifs.smb.client.useRawNTLM", "true")
        prop.setProperty("jcifs.smb.client.useBatching", "true")
        prop.setProperty("jcifs.smb.client.nativeBuffer", "true")

        val config = PropertyConfiguration(prop)
        return BaseContext(config)
    }

    private fun calculateConfigHash(settings: AppSettings): Int {
        return listOf(
            settings.smbHost, settings.smbUser, settings.smbPass, settings.smbDomain,
            settings.smbPort, settings.smbEnableSMB2, settings.smbDisableSMB1,
            settings.smbConnTimeout, settings.smbSoTimeout, settings.smbIsGuest
        ).hashCode()
    }

    suspend fun getSmbFile(context: Context, url: String, providedSettings: AppSettings? = null): SmbFile {
        val cifsContext = getContext(context, providedSettings)
        return try {
            SmbFile(url, cifsContext)
        } catch (e: Exception) {
            if (e is SmbAuthException) {
                val retryContext = invalidateAndRetry(context)
                SmbFile(url, retryContext)
            } else {
                throw e
            }
        }
    }
}
