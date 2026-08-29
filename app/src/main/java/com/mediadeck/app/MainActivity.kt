package com.mediadeck.app

import android.os.Bundle
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.viewModels
import com.mediadeck.app.ui.navigation.AppNavigationLayout
import com.mediadeck.app.ui.theme.MediaDeckTheme
import com.mediadeck.app.util.i18n.LocalLanguage
import com.mediadeck.app.viewmodel.ComicViewModel
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.MovieViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import com.mediadeck.app.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

import kotlin.time.Duration.Companion.seconds

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val comicViewModel: ComicViewModel by viewModels()
    private val galleryViewModel: GalleryViewModel by viewModels()
    private val movieViewModel: MovieViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val scannerViewModel: ScannerViewModel by viewModels()

    companion object {
        const val ACTION_PIP_CONTROL = "com.mediadeck.app.PIP_CONTROL"
        const val EXTRA_CONTROL_TYPE = "control_type"
        const val CONTROL_TYPE_PAUSE = 1
        const val CONTROL_TYPE_PLAY = 2
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_CONTROL) {
                val controlType = intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)
                if (controlType == CONTROL_TYPE_PAUSE) {
                    movieViewModel.setPlayerPlaying(false)
                } else if (controlType == CONTROL_TYPE_PLAY) {
                    movieViewModel.setPlayerPlaying(true)
                }
                updatePipParams()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        scannerViewModel.autoScanLibrary()

        movieViewModel.setInPipMode(isInPictureInPictureMode)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val filter = IntentFilter(ACTION_PIP_CONTROL)
            ContextCompat.registerReceiver(this, pipReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

            lifecycleScope.launch {
                lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    movieViewModel.isPlayerPlaying.collect {
                        if (isInPictureInPictureMode) {
                            updatePipParams()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    settingsViewModel.isSmbOnline.collect { isOnline ->
                        comicViewModel.setSmbOnline(isOnline)
                        galleryViewModel.setSmbOnline(isOnline)
                        movieViewModel.setSmbOnline(isOnline)
                    }
                }

                while (true) {
                    settingsViewModel.checkSmbStatus()
                    kotlinx.coroutines.delay(10.seconds) 
                }
            }
        }

        setContent {
            val settings by settingsViewModel.appSettings.collectAsState()

            CompositionLocalProvider(LocalLanguage provides settings.language) {
                MediaDeckTheme(themeName = settings.theme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigationLayout(
                            comicViewModel = comicViewModel,
                            galleryViewModel = galleryViewModel,
                            movieViewModel = movieViewModel,
                            settingsViewModel = settingsViewModel,
                            scannerViewModel = scannerViewModel
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: Exception) { }
    }

    override fun onUserLeaveHint() {
        val settings = settingsViewModel.appSettings.value
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && settings.enablePiP) {
            val isPipSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            val isPlaying = movieViewModel.activeMovie.value != null
            val isPickingFile = movieViewModel.isPickingFile.value

            if (isPipSupported && isPlaying && !isPickingFile) {
                try {
                    val params = getPipParams()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        super.onUserLeaveHint()
    }

    private fun updatePipParams() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setPictureInPictureParams(getPipParams())
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
    private fun getPipParams(): android.app.PictureInPictureParams {
        val isPlaying = movieViewModel.isPlayerPlaying.value
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val title = if (isPlaying) "Pause" else "Play"
        val controlType = if (isPlaying) CONTROL_TYPE_PAUSE else CONTROL_TYPE_PLAY

        val intent = Intent(ACTION_PIP_CONTROL).apply {
            setPackage(packageName)
            putExtra(EXTRA_CONTROL_TYPE, controlType)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            controlType,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val action = RemoteAction(
            Icon.createWithResource(this, iconRes),
            title,
            title,
            pendingIntent
        )

        return android.app.PictureInPictureParams.Builder()
            .setActions(listOf(action))
            .build()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            movieViewModel.setInPipMode(isInPictureInPictureMode)
        }
    }
}
