@file:OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
package com.mediadeck.app.ui.screens
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.TypedValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mediadeck.app.R
import com.mediadeck.app.data.movie.Movie
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.util.media.MediaFolderIdentity
import com.mediadeck.app.util.smb.SmbContentProvider
import com.mediadeck.app.util.smb.SmbDataSource
import com.mediadeck.app.util.scan.ScannerStateManager
import com.mediadeck.app.viewmodel.MovieViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
enum class PlaybackMode { OFF, ONE, ALL }
data class TrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val language: String,
    val label: String?,
    val isSelected: Boolean = false,
    val details: String? = null,
    val externalSubtitleUri: String? = null,
)
private data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)
private object PlayerDefaults {
    const val CONTROLS_AUTO_HIDE_MS = 4000L
    const val POSITION_POLL_INTERVAL_MS = 500L
    const val GESTURE_INDICATOR_VISIBLE_MS = 1200L
    const val SEEK_STEP_MS = 10_000L
    const val RESUME_PROMPT_THRESHOLD_MS = 1000L
    const val DRAG_SEEK_RANGE_SECONDS = 120
    const val SMB_MIN_BUFFER_MS = 5_000
    const val SMB_MAX_BUFFER_MS = 60_000
    const val LOCAL_MIN_BUFFER_MS = 15_000
    const val LOCAL_MAX_BUFFER_MS = 50_000
    const val BUFFER_FOR_PLAYBACK_MS = 2_500
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
}
private enum class GestureType { NONE, VOLUME, BRIGHTNESS, SEEK, DOUBLE_TAP_LEFT, DOUBLE_TAP_RIGHT }
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MovieViewModel,
    movie: Movie,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity
    val settings by viewModel.appSettings.collectAsState()
    val isInPip by viewModel.isInPipMode.collectAsState()
    val isPlayerPlayingState by viewModel.isPlayerPlaying.collectAsState()
    DisposableEffect(Unit) {
        ScannerStateManager.setMediaActive(true)
        onDispose {
            ScannerStateManager.setMediaActive(false)
        }
    }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val latestPlayer = rememberUpdatedState(player)
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var playbackMode by remember { mutableStateOf(PlaybackMode.OFF) }
    var playbackSpeed by remember {
        mutableFloatStateOf(if (movie.playbackSpeed != 1.0f) movie.playbackSpeed else settings.defaultVideoSpeed)
    }
    var orientationMode by remember {
        mutableIntStateOf(if (movie.orientation != 0) movie.orientation else settings.defaultVideoOrientation)
    }
    var zoomMode by remember {
        mutableIntStateOf(if (movie.zoomMode != 0) movie.zoomMode else settings.defaultVideoZoomMode)
    }
    var showController by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    SideEffect {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        if (showController && !isLocked && !isInPip) {
            controller?.show(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
    var gestureType by remember { mutableStateOf(GestureType.NONE) }
    var gestureVolumeVal by remember { mutableIntStateOf(0) }
    var gestureBrightnessVal by remember { mutableIntStateOf(0) }
    var gestureSeekTime by remember { mutableLongStateOf(0L) }
    var gestureSeekDelta by remember { mutableIntStateOf(0) }
    var doubleTapDelta by remember { mutableIntStateOf(0) }
    var showResumeDialog by remember { mutableStateOf(movie.lastPlayedPosition > PlayerDefaults.RESUME_PROMPT_THRESHOLD_MS) }
    var audioTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showPlayerMenu by remember { mutableStateOf(false) }
    var selectedAudioTrack by remember { mutableIntStateOf(movie.audioTrackIndex) }
    var selectedSubtitleTrack by remember { mutableIntStateOf(movie.subtitleTrackIndex) }
    var activeSubtitleUri by remember { mutableStateOf(movie.subtitleUri) }
    val latestAudioTrack by rememberUpdatedState(selectedAudioTrack)
    val latestSubtitleTrack by rememberUpdatedState(selectedSubtitleTrack)
    var subtitleSize by remember { mutableFloatStateOf(16f) }
    var subtitleDelayMs by remember { mutableLongStateOf(0L) }
    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var delayedSubtitleText by remember { mutableStateOf<String?>(null) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    val moviesList by viewModel.allMovies.collectAsState()
    val videoPlaylist = remember(moviesList, movie) {
        val currentFolder = MediaFolderIdentity.key(movie.folderUri, movie.uri)
        moviesList.filter { MediaFolderIdentity.key(it.folderUri, it.uri) == currentFolder }
    }
    val currentMovieIndex = remember(videoPlaylist, movie) {
        videoPlaylist.indexOfFirst { it.id == movie.id }
    }
    val latestPlaybackMode by rememberUpdatedState(playbackMode)
    val latestVideoPlaylist by rememberUpdatedState(videoPlaylist)
    val latestCurrentMovieIndex by rememberUpdatedState(currentMovieIndex)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    LaunchedEffect(movie.id) {
        selectedAudioTrack = movie.audioTrackIndex
        selectedSubtitleTrack = movie.subtitleTrackIndex
        activeSubtitleUri = movie.subtitleUri
        playbackSpeed = if (movie.playbackSpeed != 1.0f) movie.playbackSpeed else settings.defaultVideoSpeed
        orientationMode = if (movie.orientation != 0) movie.orientation else settings.defaultVideoOrientation
        zoomMode = if (movie.zoomMode != 0) movie.zoomMode else settings.defaultVideoZoomMode
        showResumeDialog = movie.lastPlayedPosition > PlayerDefaults.RESUME_PROMPT_THRESHOLD_MS
        currentPosition = 0L
        duration = 0L
        isPlaying = false
        playbackState = Player.STATE_IDLE
        audioTracks = emptyList()
        subtitleTracks = emptyList()
        showAudioDialog = false
        showSubtitleDialog = false
        subtitleDelayMs = 0L
    }
    fun saveProgress(position: Long = player?.currentPosition ?: 0L) {
        viewModel.updateMovieSettings(
            movie = movie,
            position = position,
            speed = playbackSpeed,
            subtitleUri = activeSubtitleUri,
            audioIdx = selectedAudioTrack,
            subIdx = selectedSubtitleTrack,
            orientation = orientationMode,
            zoomMode = zoomMode,
        )
    }
    val latestSaveProgress = rememberUpdatedState<(Long) -> Unit> { position -> saveProgress(position) }
    val selectedSubtitleInfo = subtitleTracks.getOrNull(selectedSubtitleTrack)
    val isExternalSubtitleSelected = selectedSubtitleInfo?.externalSubtitleUri != null
    LaunchedEffect(activeSubtitleUri) {
        subtitleCues = activeSubtitleUri?.let { uri ->
            withContext(Dispatchers.IO) { loadSubtitleCues(context, Uri.parse(uri)) }
        }.orEmpty()
    }
    LaunchedEffect(player, subtitleCues, subtitleDelayMs, isExternalSubtitleSelected, isPlaying, currentPosition) {
        if (player != null && subtitleDelayMs != 0L && isExternalSubtitleSelected && subtitleCues.isNotEmpty()) {
            fun updateSubtitle() {
                val subtitlePosition = (player?.currentPosition ?: 0L) - subtitleDelayMs
                delayedSubtitleText = findSubtitleCue(subtitleCues, subtitlePosition)?.text
            }
            if (isPlaying) {
                while (true) {
                    updateSubtitle()
                    delay(100)
                }
            } else {
                updateSubtitle()
            }
        } else delayedSubtitleText = null
    }
    val canAdjustSubtitleDelay = isExternalSubtitleSelected && subtitleCues.isNotEmpty()
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        viewModel.setIsPickingFile(false)
        uri?.let { pickedUri ->
            activeSubtitleUri = pickedUri.toString()
            selectedSubtitleTrack = -1
            try {
                context.contentResolver.takePersistableUriPermission(pickedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            player?.currentMediaItem?.let { currentItem ->
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(pickedUri)
                    .setMimeType(getMimeType(context, pickedUri))
                    .setLabel(getDocumentDisplayName(context, pickedUri).substringBeforeLast('.'))
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                val newItem = currentItem.buildUpon()
                    .setSubtitleConfigurations(listOf(subtitleConfig))
                    .build()
                val resumePosition = player?.currentPosition ?: 0L
                player?.let { currentPlayer ->
                    currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                    currentPlayer.setMediaItem(newItem, resumePosition)
                    currentPlayer.prepare()
                }
            }
            viewModel.updateMovieSettings(
                movie = movie,
                position = player?.currentPosition ?: 0L,
                speed = playbackSpeed,
                subtitleUri = pickedUri.toString(),
                audioIdx = selectedAudioTrack,
                subIdx = selectedSubtitleTrack,
                orientation = orientationMode,
                zoomMode = zoomMode,
            )
        }
    }
    LaunchedEffect(isInPip) {
        if (isInPip) {
            showAudioDialog = false
            showSubtitleDialog = false
            showController = false
        }
    }
    LaunchedEffect(showController, isInPip) {
        if (!showController || isInPip) showPlayerMenu = false
    }
    LaunchedEffect(orientationMode) {
        activity?.requestedOrientation = when (orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                latestPlayer.value?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(player, isPlayerPlayingState, showResumeDialog) {
        player?.playWhenReady = isPlayerPlayingState && !showResumeDialog
    }
    LaunchedEffect(player, playbackSpeed) {
        player?.setPlaybackSpeed(playbackSpeed)
    }
    LaunchedEffect(player, playbackMode, videoPlaylist.size) {
        player?.repeatMode = when {
            playbackMode == PlaybackMode.ONE -> Player.REPEAT_MODE_ONE
            playbackMode == PlaybackMode.ALL && videoPlaylist.size <= 1 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    DisposableEffect(movie.id, movie.uri) {
        val exoPlayer = buildExoPlayer(
            context = context,
            movie = movie,
            playbackSpeed = playbackSpeed,
            smbBufferMs = settings.smbNetworkCachingMs,
            isSubtitleUriAccessible = { uri -> viewModel.isUriAccessible(context, uri) },
        )
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                viewModel.setPlayerPlaying(playWhenReady)
            }
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                when (state) {
                    Player.STATE_READY -> {
                        duration = exoPlayer.duration
                        val externalSubtitle = exoPlayer.currentMediaItem?.localConfiguration?.subtitleConfigurations?.lastOrNull()
                        val (audios, subs) = exoPlayer.currentTracks.toTrackLists(
                            externalSubtitle?.uri?.toString(),
                            externalSubtitle?.label,
                        )
                        audioTracks = audios
                        subtitleTracks = subs
                        applyTrackOverride(exoPlayer, exoPlayer.currentTracks, audios, latestAudioTrack, C.TRACK_TYPE_AUDIO)
                        applyTrackOverride(exoPlayer, exoPlayer.currentTracks, subs, latestSubtitleTrack, C.TRACK_TYPE_TEXT)
                        if (latestSubtitleTrack < 0) {
                            val activeSubtitle = subs.indexOfFirst { it.isSelected }
                            if (activeSubtitle >= 0) selectedSubtitleTrack = activeSubtitle
                        }
                    }
                    Player.STATE_ENDED -> when (latestPlaybackMode) {
                        PlaybackMode.ONE -> Unit
                        PlaybackMode.ALL -> {
                            val playlist = latestVideoPlaylist
                            if (playlist.size > 1) {
                                val nextIndex = (latestCurrentMovieIndex + 1) % playlist.size
                                viewModel.openMovie(playlist[nextIndex])
                            }
                        }
                        PlaybackMode.OFF -> Unit 
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        player = exoPlayer
        onDispose {
            player?.let {
                latestSaveProgress.value(it.currentPosition)
                it.removeListener(listener)
                it.release()
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player?.let { currentPosition = it.currentPosition }
            delay(PlayerDefaults.POSITION_POLL_INTERVAL_MS.milliseconds)
        }
    }
    LaunchedEffect(showController, isPlaying, isLocked, showPlayerMenu, showAudioDialog, showSubtitleDialog) {
        if (showController && isPlaying && !isLocked && !showPlayerMenu && !showAudioDialog && !showSubtitleDialog) {
            delay(PlayerDefaults.CONTROLS_AUTO_HIDE_MS.milliseconds)
            if (showController && isPlaying && !isLocked && !showPlayerMenu && !showAudioDialog && !showSubtitleDialog) {
                showController = false
            }
        }
    }
    LaunchedEffect(isLocked, showUnlockButton) {
        if (isLocked && showUnlockButton) {
            delay(PlayerDefaults.CONTROLS_AUTO_HIDE_MS.milliseconds)
            showUnlockButton = false
        }
    }
    BackHandler {
        if (isLocked) return@BackHandler
        saveProgress()
        onClose()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        player?.let { p ->
            PlayerSurface(
                player = p,
                zoomMode = zoomMode,
                subtitleSize = subtitleSize,
                hideNativeSubtitles = subtitleDelayMs != 0L && isExternalSubtitleSelected && subtitleCues.isNotEmpty(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerTapGestures(
                    isLocked = isLocked,
                    skipIntervalSeconds = settings.videoSkipInterval,
                    onToggleController = {
                        if (isLocked) {
                            showUnlockButton = !showUnlockButton
                        } else {
                            showController = !showController
                        }
                    },
                    onDoubleTapSeek = { deltaSeconds, isLeft ->
                        scope.launch {
                            player?.let { p ->
                                doubleTapDelta += if (isLeft) -settings.videoSkipInterval else settings.videoSkipInterval
                                gestureType = if (isLeft) GestureType.DOUBLE_TAP_LEFT else GestureType.DOUBLE_TAP_RIGHT
                                val newPosition = (p.currentPosition + deltaSeconds * 1000L).coerceIn(0L, duration)
                                p.seekTo(newPosition)
                                currentPosition = newPosition
                                delay(PlayerDefaults.GESTURE_INDICATOR_VISIBLE_MS.milliseconds)
                                doubleTapDelta = 0
                                gestureType = GestureType.NONE
                            }
                        }
                    },
                )
                .playerDragGestures(
                    isLocked = isLocked,
                    activity = activity,
                    audioManager = audioManager,
                    maxVolume = maxVolume,
                    currentPositionMs = { player?.currentPosition ?: 0L },
                    durationMs = { duration },
                    onGestureTypeChange = { gestureType = it },
                    onBrightnessChange = { gestureBrightnessVal = it },
                    onVolumeChange = { gestureVolumeVal = it },
                    onSeekPreview = { seekTimeMs, deltaSeconds ->
                        gestureSeekTime = seekTimeMs
                        gestureSeekDelta = deltaSeconds
                        currentPosition = seekTimeMs
                    },
                    onSeekCommit = {
                        player?.seekTo(gestureSeekTime)
                        currentPosition = gestureSeekTime
                    },
                    onSeekCancel = {
                        gestureSeekDelta = 0
                        currentPosition = player?.currentPosition ?: currentPosition
                    },
                ),
        )
        val isBuffering = playbackState == Player.STATE_BUFFERING
        val isIdle = playbackState == Player.STATE_IDLE
        val isEnded = playbackState == Player.STATE_ENDED
        val showLoading = (isBuffering || (isIdle && player != null)) && !showResumeDialog
        if ((player == null || showLoading) && !isEnded) {
            LoadingOverlay()
        }
        if (isEnded) {
            EndedOverlay(
                onReplay = {
                    player?.seekTo(0)
                    player?.prepare()
                    player?.play()
                },
            )
        }
        GestureIndicatorOverlay(
            gestureType = gestureType,
            isVisible = gestureType != GestureType.NONE && !isInPip,
            volumePercent = gestureVolumeVal,
            brightnessPercent = gestureBrightnessVal,
            seekTimeMs = gestureSeekTime,
            seekDeltaSeconds = gestureSeekDelta,
            doubleTapDeltaSeconds = doubleTapDelta,
            modifier = Modifier.align(Alignment.Center),
        )
        if (delayedSubtitleText != null) {
            Text(
                text = delayedSubtitleText.orEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.92f)
                    .padding(bottom = 88.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color.White,
                fontSize = subtitleSize.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isLocked && showUnlockButton && !isInPip,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .padding(top = 4.dp, end = 12.dp),
            ) {
                UnlockButton(
                    onUnlock = {
                        isLocked = false
                        showUnlockButton = false
                        showController = true
                    },
                    modifier = Modifier,
                )
            }
            AnimatedVisibility(
                visible = showController && !isLocked && !isInPip,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                TopControlBar(
                    title = movie.title,
                    onClose = onClose,
                    onLock = {
                        isLocked = true
                        showUnlockButton = true
                    },
                    onShowSubtitles = { showSubtitleDialog = true },
                    onShowAudioTracks = { showAudioDialog = true },
                    onMenuExpandedChange = { showPlayerMenu = it },
                    playbackSpeed = playbackSpeed,
                    onPlaybackSpeedChange = { speed ->
                        playbackSpeed = speed
                        player?.setPlaybackSpeed(speed)
                    },
                )
            }
            AnimatedVisibility(
                visible = showController && !isLocked && !isInPip,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomControlBar(
                    currentPosition = currentPosition,
                    duration = duration,
                    zoomMode = zoomMode,
                    onZoomModeChange = { zoomMode = it },
                    isDraggingSlider = isDraggingSlider,
                    dragPosition = dragPosition,
                    onSliderDrag = { fraction ->
                        isDraggingSlider = true
                        dragPosition = (fraction * duration).toLong()
                    },
                    onSliderDragFinished = {
                        player?.seekTo(dragPosition)
                        currentPosition = dragPosition
                        isDraggingSlider = false
                    },
                    playbackMode = playbackMode,
                    onPlaybackModeChange = { playbackMode = it },
                    hasPlaylist = videoPlaylist.size > 1,
                    onPrevious = {
                        val prevIndex = (currentMovieIndex - 1 + videoPlaylist.size) % videoPlaylist.size
                        viewModel.openMovie(videoPlaylist[prevIndex])
                    },
                    onNext = {
                        val nextIndex = (currentMovieIndex + 1) % videoPlaylist.size
                        viewModel.openMovie(videoPlaylist[nextIndex])
                    },
                    isPlaying = isPlaying,
                    onTogglePlayPause = { if (isPlaying) player?.pause() else player?.play() },
                    onSeekBack = { player?.let { it.seekTo((it.currentPosition - PlayerDefaults.SEEK_STEP_MS).coerceAtLeast(0)) } },
                    onSeekForward = { player?.let { it.seekTo((it.currentPosition + PlayerDefaults.SEEK_STEP_MS).coerceAtMost(duration)) } },
                )
            }
        }
        if (showResumeDialog) {
            ResumeDialog(
                resumePositionMs = movie.lastPlayedPosition,
                onResume = {
                    player?.seekTo(movie.lastPlayedPosition)
                    player?.prepare()
                    player?.play()
                    showResumeDialog = false
                },
                onStartOver = {
                    player?.seekTo(0)
                    player?.prepare()
                    player?.play()
                    showResumeDialog = false
                },
            )
        }
        if (showAudioDialog) {
            TrackSelectionDialog(
                title = t("Select Audio Track", "Pilih Jalur Audio"),
                tracks = audioTracks,
                selectedIndex = selectedAudioTrack,
                offTrackLabel = t("Auto (Default)", "Otomatis (Default)"),
                emptyMessage = t("No audio tracks found", "Track audio tidak ditemukan"),
                onTrackSelected = { index ->
                    selectedAudioTrack = index
                    player?.let { p ->
                        applyTrackSelection(p, audioTracks, index, C.TRACK_TYPE_AUDIO)
                    }
                    saveProgress()
                    showAudioDialog = false
                },
                onDismiss = { showAudioDialog = false },
            )
        }
        if (showSubtitleDialog) {
            SubtitleDialog(
                tracks = subtitleTracks,
                selectedIndex = selectedSubtitleTrack.takeIf { it in subtitleTracks.indices }
                    ?: subtitleTracks.indexOfFirst { it.isSelected },
                subtitleSize = subtitleSize,
                subtitleDelayMs = subtitleDelayMs,
                isExternalSubtitleSelected = isExternalSubtitleSelected,
                canAdjustSubtitleDelay = canAdjustSubtitleDelay,
                emptyMessage = t("No embedded subtitles found", "Subtitle bawaan tidak ditemukan"),
                onTrackSelected = { index ->
                    selectedSubtitleTrack = index
                    activeSubtitleUri = subtitleTracks.getOrNull(index)?.externalSubtitleUri
                    player?.let { p ->
                        applyTrackSelection(p, subtitleTracks, index, C.TRACK_TYPE_TEXT)
                    }
                    saveProgress()
                    showSubtitleDialog = false
                },
                onPickExternalSubtitle = {
                    viewModel.setIsPickingFile(true)
                    subtitlePickerLauncher.launch(arrayOf("*/*"))
                },
                onSubtitleSizeChange = { subtitleSize = it },
                onSubtitleDelayChange = { subtitleDelayMs = it.coerceIn(-10_000L, 10_000L) },
                onDismiss = { showSubtitleDialog = false },
            )
        }
    }
}
private fun buildExoPlayer(
    context: Context,
    movie: Movie,
    playbackSpeed: Float,
    smbBufferMs: Int,
    isSubtitleUriAccessible: (String) -> Boolean,
): ExoPlayer {
    val isSmb = com.mediadeck.app.util.media.MediaUtils.isSmbUri(movie.uri)
    val dataSourceFactory = DataSource.Factory {
        val uriStr = movie.uri
        if (com.mediadeck.app.util.media.MediaUtils.isSmbUri(uriStr)) {
            SmbDataSource(context)
        } else {
            DefaultDataSource.Factory(context).createDataSource()
        }
    }
    val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    val audioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
    val smbMaxBufferMs = smbBufferMs.coerceIn(2_000, 30_000)
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            if (isSmb) minOf(PlayerDefaults.SMB_MIN_BUFFER_MS, smbMaxBufferMs) else PlayerDefaults.LOCAL_MIN_BUFFER_MS,
            if (isSmb) smbMaxBufferMs else PlayerDefaults.LOCAL_MAX_BUFFER_MS,
            if (isSmb) 750 else PlayerDefaults.BUFFER_FOR_PLAYBACK_MS,
            if (isSmb) 2_000 else PlayerDefaults.BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setBackBuffer(if (isSmb) 30_000 else 15_000, true)
        .build()
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setAudioAttributes(audioAttributes, true)
        .setLoadControl(loadControl)
        .build()
        .apply {
            playWhenReady = true
            setPlaybackSpeed(playbackSpeed)
            val mediaItemBuilder = MediaItem.fromUri(movie.uri.toUri()).buildUpon()
            var hasExternalSubtitle = false
            movie.subtitleUri?.let { subtitleUriString ->
                if (isSubtitleUriAccessible(subtitleUriString)) {
                    val subtitleUri = Uri.parse(subtitleUriString)
                    val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(getMimeType(context, subtitleUri))
                        .setLabel(getDocumentDisplayName(context, subtitleUri).substringBeforeLast('.'))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
                    hasExternalSubtitle = true
                }
            }
            setMediaItem(mediaItemBuilder.build())
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(
                    C.TRACK_TYPE_TEXT,
                    !hasExternalSubtitle && movie.subtitleTrackIndex < 0,
                )
                .build()
            if (movie.lastPlayedPosition <= PlayerDefaults.RESUME_PROMPT_THRESHOLD_MS) {
                prepare()
            }
        }
}
private fun androidx.media3.common.Tracks.toTrackLists(
    externalSubtitleUri: String?,
    externalSubtitleLabel: String?,
): Pair<List<TrackInfo>, List<TrackInfo>> {
    val audios = mutableListOf<TrackInfo>()
    val subs = mutableListOf<TrackInfo>()
    groups.forEachIndexed { groupIndex, group ->
        when (group.type) {
            C.TRACK_TYPE_AUDIO -> for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val details = listOfNotNull(
                    format.channelCount.takeIf { it > 0 }?.let { if (it == 1) "Mono" else "${it}ch" },
                    format.sampleRate.takeIf { it > 0 }?.let { "${it / 1000} kHz" },
                ).joinToString(" · ").ifBlank { null }
                audios.add(TrackInfo(groupIndex, i, format.language ?: "Audio #${audios.size + 1}", format.label, group.isTrackSelected(i), details))
            }
            C.TRACK_TYPE_TEXT -> for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val details = format.sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT)
                val isExternal = externalSubtitleUri != null && format.label == externalSubtitleLabel
                subs.add(
                    TrackInfo(
                        groupIndex = groupIndex,
                        trackIndex = i,
                        language = format.language ?: "Subtitle #${subs.size + 1}",
                        label = format.label,
                        isSelected = group.isTrackSelected(i),
                        details = details,
                        externalSubtitleUri = externalSubtitleUri.takeIf { isExternal },
                    ),
                )
            }
        }
    }
    return audios to subs
}
private fun applyTrackOverride(
    player: ExoPlayer,
    tracks: androidx.media3.common.Tracks,
    candidates: List<TrackInfo>,
    selectedIndex: Int,
    trackType: Int,
) {
    if (selectedIndex !in candidates.indices) return
    val track = candidates[selectedIndex]
    val group = tracks.groups.getOrNull(track.groupIndex) ?: return
    if (track.trackIndex !in 0 until group.length) return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(trackType, false)
        .setOverrideForType(TrackSelectionOverride(tracks.groups[track.groupIndex].mediaTrackGroup, track.trackIndex))
        .build()
}
private fun applyTrackSelection(player: ExoPlayer, candidates: List<TrackInfo>, selectedIndex: Int, trackType: Int) {
    val builder = player.trackSelectionParameters.buildUpon()
    if (selectedIndex == -1) {
        builder.clearOverridesOfType(trackType)
            .setTrackTypeDisabled(trackType, trackType == C.TRACK_TYPE_TEXT)
    } else {
        val track = candidates.getOrNull(selectedIndex) ?: return
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        if (track.trackIndex !in 0 until group.length) return
        builder.setTrackTypeDisabled(trackType, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
    }
    player.trackSelectionParameters = builder.build()
}
private fun Modifier.playerTapGestures(
    isLocked: Boolean,
    skipIntervalSeconds: Int,
    onToggleController: () -> Unit,
    onDoubleTapSeek: (deltaSeconds: Int, isLeft: Boolean) -> Unit,
): Modifier = pointerInput(isLocked) {
    if (isLocked) {
        detectTapGestures { onToggleController() }
        return@pointerInput
    }
    detectTapGestures(
        onDoubleTap = { offset ->
            val isLeft = offset.x < size.width / 2
            onDoubleTapSeek(skipIntervalSeconds, isLeft)
        },
        onTap = { onToggleController() },
    )
}
private fun Modifier.playerDragGestures(
    isLocked: Boolean,
    activity: Activity?,
    audioManager: AudioManager,
    maxVolume: Int,
    currentPositionMs: () -> Long,
    durationMs: () -> Long,
    onGestureTypeChange: (GestureType) -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onSeekPreview: (seekTimeMs: Long, deltaSeconds: Int) -> Unit,
    onSeekCommit: () -> Unit,
    onSeekCancel: () -> Unit,
): Modifier = pointerInput(isLocked) {
    if (isLocked) return@pointerInput
    var activeGesture = GestureType.NONE
    var isLeftSide = false
    var startVolume = 0
    var startBrightness = 0.5f
    var startPositionMs = 0L
    var totalDragX = 0f
    var totalDragY = 0f
    detectDragGestures(
        onDragStart = { offset ->
            activeGesture = GestureType.NONE
            isLeftSide = offset.x < size.width / 2f
            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val brightness = activity?.window?.attributes?.screenBrightness ?: -1f
            startBrightness = if (brightness < 0f) 0.5f else brightness
            startPositionMs = currentPositionMs()
            totalDragX = 0f
            totalDragY = 0f
        },
        onDragEnd = {
            if (activeGesture == GestureType.SEEK) {
                onSeekCommit()
            }
            activeGesture = GestureType.NONE
            onGestureTypeChange(GestureType.NONE)
        },
        onDragCancel = {
            val wasSeeking = activeGesture == GestureType.SEEK
            activeGesture = GestureType.NONE
            onGestureTypeChange(GestureType.NONE)
            if (wasSeeking) onSeekCancel()
        },
        onDrag = { change, dragAmount ->
            change.consume()
            if (activeGesture == GestureType.NONE) {
                activeGesture = when {
                    abs(dragAmount.y) > abs(dragAmount.x) && isLeftSide -> GestureType.BRIGHTNESS
                    abs(dragAmount.y) > abs(dragAmount.x) -> GestureType.VOLUME
                    else -> GestureType.SEEK
                }
                onGestureTypeChange(activeGesture)
            }
            when (activeGesture) {
                GestureType.BRIGHTNESS -> {
                    totalDragY += dragAmount.y
                    val newBrightness = (startBrightness - totalDragY / size.height).coerceIn(0.01f, 1f)
                    val layoutParams = activity?.window?.attributes
                    layoutParams?.screenBrightness = newBrightness
                    if (layoutParams != null) activity?.window?.attributes = layoutParams
                    onBrightnessChange((newBrightness * 100).toInt())
                }
                GestureType.VOLUME -> {
                    totalDragY += dragAmount.y
                    val volumeDelta = (-totalDragY / size.height * maxVolume).roundToInt()
                    val newVolume = (startVolume + volumeDelta).coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    onVolumeChange(if (maxVolume > 0) ((newVolume.toFloat() / maxVolume) * 100).toInt() else 0)
                }
                GestureType.SEEK -> {
                    totalDragX += dragAmount.x
                    val deltaSeconds = (totalDragX / size.width * PlayerDefaults.DRAG_SEEK_RANGE_SECONDS).toInt()
                    val seekTime = (startPositionMs + deltaSeconds * 1000L).coerceIn(0L, durationMs())
                    onSeekPreview(seekTime, deltaSeconds)
                }
                else -> Unit
            }
        },
    )
}
@Composable
private fun PlayerSurface(player: ExoPlayer, zoomMode: Int, subtitleSize: Float, hideNativeSubtitles: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxSize().testTag("movie_player_view"),
        factory = { ctx ->
            val view = android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.exo_player_texture_view, null) as PlayerView
            view.apply {
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { view ->
            if (view.player != player) view.player = player
            view.resizeMode = when (zoomMode) {
                1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                4 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            view.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleSize)
            view.subtitleView?.visibility = if (hideNativeSubtitles) android.view.View.INVISIBLE else android.view.View.VISIBLE
        },
        onRelease = { view -> view.player = null },
    )
}
@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
            strokeWidth = 6.dp,
        )
    }
}
@Composable
private fun EndedOverlay(onReplay: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(t("Video Completed", "Video Selesai"), color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onReplay) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(t("Replay", "Putar Ulang"))
            }
        }
    }
}
@Composable
private fun GestureIndicatorOverlay(
    gestureType: GestureType,
    isVisible: Boolean,
    volumePercent: Int,
    brightnessPercent: Int,
    seekTimeMs: Long,
    seekDeltaSeconds: Int,
    doubleTapDeltaSeconds: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier,
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.sizeIn(minWidth = 100.dp, minHeight = 100.dp),
        ) {
            val controlColor = Color.White
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (gestureType) {
                    GestureType.VOLUME -> {
                        val icon = when {
                            volumePercent == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                            volumePercent < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                            else -> Icons.AutoMirrored.Filled.VolumeUp
                        }
                        GestureProgressIndicator(icon, volumePercent)
                    }
                    GestureType.BRIGHTNESS -> {
                        GestureProgressIndicator(Icons.Default.LightMode, brightnessPercent)
                    }
                    GestureType.SEEK -> {
                        val sign = if (seekDeltaSeconds >= 0) "+" else ""
                        Text(formatTime(seekTimeMs), color = controlColor, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "$sign$seekDeltaSeconds s",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    GestureType.DOUBLE_TAP_LEFT -> {
                        Icon(Icons.Default.FastRewind, contentDescription = null, tint = controlColor, modifier = Modifier.size(40.dp))
                        Text("${abs(doubleTapDeltaSeconds)}s", color = controlColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    GestureType.DOUBLE_TAP_RIGHT -> {
                        Icon(Icons.Default.FastForward, contentDescription = null, tint = controlColor, modifier = Modifier.size(40.dp))
                        Text("${doubleTapDeltaSeconds}s", color = controlColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    GestureType.NONE -> Unit
                }
            }
        }
    }
}
@Composable
private fun GestureProgressIndicator(icon: ImageVector, percent: Int) {
    val controlColor = Color.White
    Icon(icon, contentDescription = null, tint = controlColor, modifier = Modifier.size(36.dp))
    Spacer(modifier = Modifier.height(12.dp))
    LinearProgressIndicator(
        progress = { percent / 100f },
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.White.copy(alpha = 0.2f),
        modifier = Modifier.width(80.dp).height(4.dp).clip(CircleShape),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text("$percent%", color = controlColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}
@Composable
private fun UnlockButton(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onUnlock,
        modifier = modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Icon(Icons.Default.LockOpen, contentDescription = t("Unlock controls", "Buka kunci kontrol"), modifier = Modifier.size(22.dp))
    }
}
@Composable
private fun LockButton(onLock: () -> Unit, buttonSize: androidx.compose.ui.unit.Dp = 48.dp) {
    IconButton(
        onClick = onLock,
        modifier = Modifier.size(buttonSize).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Icon(Icons.Default.Lock, contentDescription = t("Lock controls", "Kunci kontrol"), modifier = Modifier.size(24.dp))
    }
}
@Composable
private fun topScrimBrush() = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.4f), Color.Transparent),
)
@Composable
private fun bottomScrimBrush() = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.9f)),
)
@Composable
private fun TopControlBar(
    title: String,
    onClose: () -> Unit,
    onLock: () -> Unit,
    onShowSubtitles: () -> Unit,
    onShowAudioTracks: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
) {
    val controlColor = Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(topScrimBrush())
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = t("Close player", "Tutup pemutar"), tint = controlColor)
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                color = controlColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LockButton(onLock = onLock, buttonSize = 40.dp)
        SettingsMenu(
            onShowSubtitles = onShowSubtitles,
            onShowAudioTracks = onShowAudioTracks,
            onMenuExpandedChange = onMenuExpandedChange,
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = onPlaybackSpeedChange,
            buttonSize = 40.dp,
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControlBar(
    currentPosition: Long,
    duration: Long,
    zoomMode: Int,
    onZoomModeChange: (Int) -> Unit,
    isDraggingSlider: Boolean,
    dragPosition: Long,
    onSliderDrag: (fraction: Float) -> Unit,
    onSliderDragFinished: () -> Unit,
    playbackMode: PlaybackMode,
    onPlaybackModeChange: (PlaybackMode) -> Unit,
    hasPlaylist: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
) {
    val controlColor = Color.White
    val inactiveColor = controlColor.copy(alpha = 0.4f)
    var showScaleMenu by remember { mutableStateOf(false) }
    BoxWithConstraints {
        val compact = maxHeight < 420.dp
        val narrow = maxWidth < 400.dp
        val buttonSize = if (narrow) 40.dp else 48.dp
        val playSize = when {
            compact && narrow -> 48.dp
            compact || narrow -> 56.dp
            else -> 64.dp
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(bottomScrimBrush())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    top = if (compact) 2.dp else 12.dp,
                    bottom = if (compact) 4.dp else 16.dp,
                    start = if (narrow) 4.dp else 12.dp,
                    end = if (narrow) 4.dp else 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 10.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatTime(currentPosition.coerceAtLeast(0L)), color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (duration > 0L) "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}" else "--:--", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Slider(
                    value = if (duration > 0) (if (isDraggingSlider) dragPosition else currentPosition).toFloat().div(duration.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = onSliderDrag,
                    onValueChangeFinished = onSliderDragFinished,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier.fillMaxWidth().height(if (compact) 28.dp else 36.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(
                    onClick = {
                        onPlaybackModeChange(
                            when (playbackMode) {
                                PlaybackMode.OFF -> PlaybackMode.ONE
                                PlaybackMode.ONE -> PlaybackMode.ALL
                                PlaybackMode.ALL -> PlaybackMode.OFF
                            },
                        )
                    },
                    modifier = Modifier.size(buttonSize),
                ) {
                    Icon(
                        if (playbackMode == PlaybackMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = when (playbackMode) {
                            PlaybackMode.OFF -> t("Repeat off", "Ulangi mati")
                            PlaybackMode.ONE -> t("Repeat one", "Ulangi satu")
                            PlaybackMode.ALL -> t("Repeat all", "Ulangi semua")
                        },
                        tint = if (playbackMode == PlaybackMode.OFF) inactiveColor else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (hasPlaylist) {
                    IconButton(onClick = onPrevious, modifier = Modifier.size(buttonSize)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = t("Previous video", "Video sebelumnya"), tint = controlColor, modifier = Modifier.size(28.dp))
                    }
                }
                IconButton(onClick = onSeekBack, modifier = Modifier.size(buttonSize)) {
                    Icon(Icons.Default.Replay10, contentDescription = t("Back 10 seconds", "Mundur 10 detik"), tint = controlColor, modifier = Modifier.size(26.dp))
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(playSize).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) t("Pause", "Jeda") else t("Play", "Putar"),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(if (compact) 30.dp else 36.dp),
                    )
                }
                IconButton(onClick = onSeekForward, modifier = Modifier.size(buttonSize)) {
                    Icon(Icons.Default.Forward10, contentDescription = t("Forward 10 seconds", "Maju 10 detik"), tint = controlColor, modifier = Modifier.size(26.dp))
                }
                if (hasPlaylist) {
                    IconButton(onClick = onNext, modifier = Modifier.size(buttonSize)) {
                        Icon(Icons.Default.SkipNext, contentDescription = t("Next video", "Video berikutnya"), tint = controlColor, modifier = Modifier.size(28.dp))
                    }
                }
                Box {
                    IconButton(onClick = { showScaleMenu = true }, modifier = Modifier.size(buttonSize)) {
                        Icon(Icons.Default.AspectRatio, contentDescription = t("Video scaling", "Skala video"), tint = controlColor, modifier = Modifier.size(22.dp))
                    }
                    DropdownMenu(expanded = showScaleMenu, onDismissRequest = { showScaleMenu = false }) {
                        listOf(
                            0 to t("Fit", "Pas"),
                            2 to t("Fill screen (crop)", "Penuhi layar (potong)"),
                            1 to t("Stretch", "Regangkan"),
                            3 to t("Fit width", "Pas lebar"),
                            4 to t("Fit height", "Pas tinggi"),
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (zoomMode == mode) Icon(Icons.Default.Check, contentDescription = null) },
                                onClick = {
                                    onZoomModeChange(mode)
                                    showScaleMenu = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
private val PLAYBACK_SPEEDS = listOf(0.25f, 0.35f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
@Composable
private fun SettingsMenu(
    onShowSubtitles: () -> Unit,
    onShowAudioTracks: () -> Unit,
    onMenuExpandedChange: (Boolean) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true; onMenuExpandedChange(true) }, modifier = Modifier.size(buttonSize)) {
            Icon(Icons.Default.MoreVert, contentDescription = t("Player settings", "Pengaturan pemutar"), tint = Color.White)
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false; onMenuExpandedChange(false) }) {
            DropdownMenuItem(
                text = { Text(t("Subtitles", "Subtitle")) },
                leadingIcon = { Icon(Icons.Default.Subtitles, null) },
                onClick = { showMenu = false; onMenuExpandedChange(false); onShowSubtitles() },
            )
            DropdownMenuItem(
                text = { Text(t("Audio Tracks", "Audio")) },
                leadingIcon = { Icon(Icons.Default.Audiotrack, null) },
                onClick = { showMenu = false; onMenuExpandedChange(false); onShowAudioTracks() },
            )
            DropdownMenuItem(
                text = { Text(t("Playback Speed", "Kecepatan")) },
                leadingIcon = { Icon(Icons.Default.Speed, null) },
                onClick = { showMenu = false; showSpeedDialog = true },
            )
        }
    }
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false; showMenu = false; onMenuExpandedChange(false) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(t("Playback speed", "Kecepatan pemutaran"))
                        Text("${playbackSpeed}x", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PLAYBACK_SPEEDS.chunked(3).forEach { rowSpeeds ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowSpeeds.forEach { speed ->
                                val selected = playbackSpeed == speed
                                Surface(
                                    modifier = Modifier.weight(1f).height(48.dp).clickable {
                                        onPlaybackSpeedChange(speed)
                                        showSpeedDialog = false
                                        showMenu = false
                                        onMenuExpandedChange(false)
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${speed}x", fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                    }
                                }
                            }
                            repeat(3 - rowSpeeds.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSpeedDialog = false; onMenuExpandedChange(false) }) { Text(t("Close", "Tutup")) } },
        )
    }
}
@Composable
private fun ResumeDialog(resumePositionMs: Long, onResume: () -> Unit, onStartOver: () -> Unit) {
    AlertDialog(
        onDismissRequest = onResume,
        title = { Text(t("Continue Watching?", "Lanjutkan Menonton?")) },
        text = {
            Text(
                t(
                    "Would you like to resume this video from your last saved position: ${formatTime(resumePositionMs)}?",
                    "Apakah Anda ingin melanjutkan tontonan video ini dari lokasi terakhir: ${formatTime(resumePositionMs)}?",
                ),
            )
        },
        confirmButton = { Button(onClick = onResume) { Text(t("Resume", "Lanjutkan")) } },
        dismissButton = { TextButton(onClick = onStartOver) { Text(t("Start Over", "Mulai Dari Awal")) } },
    )
}
@Composable
private fun TrackSelectionDialog(
    title: String,
    tracks: List<TrackInfo>,
    selectedIndex: Int,
    offTrackLabel: String?,
    emptyMessage: String,
    onTrackSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(title)
                    Text(
                        if (tracks.isEmpty()) t("No tracks", "Tidak ada track") else t("${tracks.size} tracks", "${tracks.size} track"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                if (offTrackLabel != null) {
                    item {
                        TrackRow(
                            label = offTrackLabel,
                            details = t("Let the player choose", "Pilih otomatis oleh pemutar"),
                            isSelected = selectedIndex == -1,
                        ) { onTrackSelected(-1) }
                    }
                }
                items(tracks.size) { index ->
                    val track = tracks[index]
                    TrackRow(label = track.displayName(), details = track.details, isSelected = selectedIndex == index) {
                        onTrackSelected(index)
                    }
                }
                if (tracks.isEmpty()) item { Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Tutup")) } },
    )
}
@Composable
private fun TrackRow(label: String, isSelected: Boolean, details: String? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = label,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!details.isNullOrBlank()) {
                    Text(details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
@Composable
private fun SubtitleDialog(
    tracks: List<TrackInfo>,
    selectedIndex: Int,
    subtitleSize: Float,
    subtitleDelayMs: Long,
    isExternalSubtitleSelected: Boolean,
    canAdjustSubtitleDelay: Boolean,
    emptyMessage: String,
    onTrackSelected: (Int) -> Unit,
    onPickExternalSubtitle: () -> Unit,
    onSubtitleSizeChange: (Float) -> Unit,
    onSubtitleDelayChange: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(t("Subtitles", "Subtitle"))
                    Text(
                        if (isExternalSubtitleSelected) t("External subtitle", "Subtitle eksternal") else t("Select a track", "Pilih track"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 460.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("Available tracks", "Track tersedia"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    OutlinedButton(
                        onClick = onPickExternalSubtitle,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Add file", "Tambah file"), fontSize = 12.sp)
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                    item {
                        TrackRow(t("Off (No Subtitles)", "Nonaktifkan Subtitle"), isSelected = selectedIndex == -1) {
                            onTrackSelected(-1)
                        }
                    }
                    items(tracks.size) { index ->
                        val track = tracks[index]
                        TrackRow(label = track.displayName(), details = track.details, isSelected = selectedIndex == index) {
                            onTrackSelected(index)
                        }
                    }
                    if (tracks.isEmpty()) item { Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider()
                Text(t("Text size", "Ukuran teks"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { onSubtitleSizeChange((subtitleSize - 2f).coerceAtLeast(10f)) }, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) { Text("−") }
                    Text("${subtitleSize.toInt()} sp", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { onSubtitleSizeChange((subtitleSize + 2f).coerceAtMost(32f)) }, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) { Text("+") }
                }
                HorizontalDivider()
                Text(t("External subtitle sync", "Sinkronisasi subtitle eksternal"), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        enabled = canAdjustSubtitleDelay && subtitleDelayMs > -10_000L,
                        onClick = { onSubtitleDelayChange((subtitleDelayMs - 250L).coerceAtLeast(-10_000L)) },
                    ) { Text("−250 ms") }
                    Text(
                        text = if (subtitleDelayMs == 0L) "0 ms" else "${if (subtitleDelayMs > 0) "+" else ""}${subtitleDelayMs} ms",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        enabled = canAdjustSubtitleDelay && subtitleDelayMs < 10_000L,
                        onClick = { onSubtitleDelayChange((subtitleDelayMs + 250L).coerceAtMost(10_000L)) },
                    ) { Text("+250 ms") }
                }
                Text(t("Positive delays; negative advances", "Positif menunda; negatif memajukan"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (!canAdjustSubtitleDelay) {
                    Text(
                        text = if (!isExternalSubtitleSelected) {
                            t("Delay is available for external subtitles", "Delay tersedia untuk subtitle eksternal")
                        } else {
                            t("This subtitle format cannot be synchronized here", "Format subtitle ini belum didukung untuk sinkronisasi")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Close", "Tutup")) } },
    )
}
private fun TrackInfo.displayName(): String {
    val trackLabel = label?.takeIf { it.isNotBlank() }
    return when {
        trackLabel == null -> language
        trackLabel.equals(language, ignoreCase = true) -> trackLabel
        else -> "$trackLabel · $language"
    }
}
private fun getMimeType(context: Context, uri: Uri): String {
    val fileName = getDocumentDisplayName(context, uri)
    return when {
        fileName.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
        fileName.endsWith(".ssa", true) || fileName.endsWith(".ass", true) -> MimeTypes.TEXT_SSA
        fileName.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
        else -> runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?.takeIf { it != "application/octet-stream" } ?: MimeTypes.APPLICATION_SUBRIP
    }
}
private fun getDocumentDisplayName(context: Context, uri: Uri): String {
    val displayName = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameColumn >= 0) cursor.getString(nameColumn) else null
        }
    }.getOrNull().orEmpty()
    return displayName.ifBlank { uri.lastPathSegment.orEmpty() }
}
private fun loadSubtitleCues(context: Context, uri: Uri): List<SubtitleCue> {
    val contents = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
            buildString {
                while (length < 16 * 1024 * 1024) {
                    val line = reader.readLine() ?: break
                    append(line).append('\n')
                }
            }
        }
    }.getOrNull().orEmpty()
    if (contents.isBlank()) return emptyList()
    val fileName = getDocumentDisplayName(context, uri).lowercase(Locale.ROOT)
    val cues = if (fileName.endsWith(".ass") || fileName.endsWith(".ssa")) {
        contents.lineSequence().mapNotNull { line ->
            if (!line.startsWith("Dialogue:", ignoreCase = true)) return@mapNotNull null
            val fields = line.substringAfter(':').trim().split(',', limit = 10)
            if (fields.size < 10) return@mapNotNull null
            val start = parseSubtitleTime(fields[1]) ?: return@mapNotNull null
            val end = parseSubtitleTime(fields[2]) ?: return@mapNotNull null
            val text = fields[9].replace("\\N", "\n", ignoreCase = true).replace(Regex("\\{[^}]*}"), "").trim()
            SubtitleCue(start, end, text).takeIf { end > start && text.isNotBlank() }
        }.toList()
    } else {
        contents.split(Regex("\\n\\s*\\n")).mapNotNull { block ->
            val lines = block.lines()
            val timingIndex = lines.indexOfFirst { "-->" in it }
            if (timingIndex < 0) return@mapNotNull null
            val timing = lines[timingIndex]
            val start = parseSubtitleTime(timing.substringBefore("-->")) ?: return@mapNotNull null
            val end = parseSubtitleTime(timing.substringAfter("-->").trim().substringBefore(' ')) ?: return@mapNotNull null
            val text = lines.drop(timingIndex + 1).joinToString("\n").replace(Regex("<[^>]*>"), "").trim()
            SubtitleCue(start, end, text).takeIf { end > start && text.isNotBlank() }
        }
    }
    return cues.sortedBy { it.startMs }
}
private fun findSubtitleCue(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
    val result = cues.binarySearchBy(positionMs) { it.startMs }
    val candidateIndex = if (result >= 0) result else -result - 2
    return cues.getOrNull(candidateIndex)?.takeIf { positionMs < it.endMs }
}
private fun parseSubtitleTime(value: String): Long? {
    val match = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""").find(value.trim()) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: 0L
    val minutes = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    val millis = match.groupValues[4].padEnd(3, '0').toLongOrNull() ?: return null
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
