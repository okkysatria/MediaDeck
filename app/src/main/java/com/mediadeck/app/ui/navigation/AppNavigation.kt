package com.mediadeck.app.ui.navigation

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mediadeck.app.ui.screens.ComicReaderScreen
import com.mediadeck.app.ui.screens.ComicsScreen
import com.mediadeck.app.ui.screens.GalleryScreen
import com.mediadeck.app.ui.screens.GalleryViewerScreen
import com.mediadeck.app.ui.screens.MoviesScreen
import com.mediadeck.app.ui.screens.SettingsScreen
import com.mediadeck.app.ui.screens.VideoPlayerScreen
import com.mediadeck.app.ui.theme.DeckAccentBlue
import com.mediadeck.app.ui.theme.DeckAccentPillBg
import com.mediadeck.app.ui.theme.DeckDivider
import com.mediadeck.app.ui.theme.DeckSurface
import com.mediadeck.app.ui.theme.DeckTextSecondary
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.ComicViewModel
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.MovieViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import com.mediadeck.app.viewmodel.SettingsViewModel

object Routes {
    const val COMICS = "comics"
    const val GALLERY = "gallery"
    const val MOVIES = "movies"
    const val SETTINGS = "settings"
    const val COMIC_READER = "comic_reader"
    const val VIDEO_PLAYER = "video_player"
    const val GALLERY_VIEWER = "gallery_viewer/{itemId}"
    fun galleryViewer(id: Long) = "gallery_viewer/$id"
}

private val FULL_SCREEN_ROUTES = setOf(Routes.COMIC_READER, Routes.VIDEO_PLAYER, Routes.GALLERY_VIEWER)
private val ROOT_TABS = listOf(Routes.COMICS, Routes.GALLERY, Routes.MOVIES, Routes.SETTINGS)

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun AppNavigationLayout(
    comicViewModel: ComicViewModel,
    galleryViewModel: GalleryViewModel,
    movieViewModel: MovieViewModel,
    settingsViewModel: SettingsViewModel,
    scannerViewModel: ScannerViewModel,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    var topLevelHistory by rememberSaveable { mutableStateOf(listOf(Routes.COMICS)) }

    fun navigateToRoot(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateToRootTab(route: String) {
        if (topLevelHistory.lastOrNull() != route) {
            topLevelHistory = topLevelHistory.filterNot { it == route } + route
        }
        navigateToRoot(route)
    }

    fun navigateToPreviousRootTab() {
        if (topLevelHistory.size <= 1) return
        topLevelHistory = topLevelHistory.dropLast(1)
        navigateToRoot(topLevelHistory.last())
    }


    fun openScanSettings() {
        settingsViewModel.setSelectedSettingsTabIndex(1)
        navigateToRootTab(Routes.SETTINGS)
    }

    val isSmbOnline by settingsViewModel.isSmbOnline.collectAsState()
    val currentBrowserPath by settingsViewModel.currentBrowserPath.collectAsState()

    LaunchedEffect(isSmbOnline) {
        comicViewModel.setSmbOnline(isSmbOnline)
        galleryViewModel.setSmbOnline(isSmbOnline)
        movieViewModel.setSmbOnline(isSmbOnline)
    }

    LaunchedEffect(Unit) {
        scannerViewModel.navigationRequest.collect { targetTab ->
            when (targetTab) {
                Routes.GALLERY -> galleryViewModel.resetToExploreRoot()
                Routes.MOVIES -> movieViewModel.resetToExploreRoot()
            }
            navigateToRootTab(targetTab)
        }
    }

    val exitMessage = t("Press back again to exit", "Tekan sekali lagi untuk keluar")
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val isCurrentRootTab = currentRoute != null && currentRoute == topLevelHistory.lastOrNull()
    BackHandler(enabled = isCurrentRootTab) {
        if (topLevelHistory.size > 1) {
            navigateToPreviousRootTab()
        } else {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBackPressTime < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressTime = now
                Toast.makeText(context, exitMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val isFullScreenRoute = currentRoute in FULL_SCREEN_ROUTES

    val navHost = remember {
        movableContentOf {
            NavHost(
                navController = navController,
                startDestination = Routes.COMICS,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(150)) },
                exitTransition = { fadeOut(tween(100)) },
                popEnterTransition = { fadeIn(tween(150)) },
                popExitTransition = { fadeOut(tween(100)) },
            ) {
                composable(Routes.COMICS) {
                    ComicsScreen(
                        viewModel = comicViewModel,
                        scannerViewModel = scannerViewModel,
                        onOpenComic = { comic ->
                            comicViewModel.openComic(context, comic)
                            navController.navigate(Routes.COMIC_READER)
                        },
                        onNavigateToScan = { openScanSettings() },
                    )
                }
                composable(Routes.GALLERY) {
                    GalleryScreen(
                        viewModel = galleryViewModel,
                        scannerViewModel = scannerViewModel,
                        onOpenItem = { id -> navController.navigate(Routes.galleryViewer(id)) },
                        onNavigateToScan = { openScanSettings() },
                    )
                }
                composable(Routes.MOVIES) {
                    MoviesScreen(
                        viewModel = movieViewModel,
                        scannerViewModel = scannerViewModel,
                        onOpenMovie = { movie ->
                            movieViewModel.openMovie(movie)
                            navController.navigate(Routes.VIDEO_PLAYER)
                        },
                        onNavigateToScan = { openScanSettings() },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        scannerViewModel = scannerViewModel,
                        onNavigateBack = {
                            if (topLevelHistory.size > 1) navigateToPreviousRootTab()
                            else navController.popBackStack()
                        },
                        showBackButton = topLevelHistory.size > 1,
                    )
                }
                composable(Routes.COMIC_READER) {
                    val activeComic by comicViewModel.activeComic.collectAsState()
                    if (activeComic != null) {
                        ComicReaderScreen(
                            viewModel = comicViewModel,
                            onClose = { navController.popBackStack() },
                        )
                        DisposableEffect(Unit) {
                            onDispose { comicViewModel.closeComicReader() }
                        }
                    } else {
                        LoadingThenPop(navController, Routes.COMIC_READER)
                    }
                }
                composable(Routes.VIDEO_PLAYER) {
                    val activeMovie by movieViewModel.activeMovie.collectAsState()
                    val movie = activeMovie
                    if (movie != null) {
                        VideoPlayerScreen(
                            viewModel = movieViewModel,
                            movie = movie,
                            onClose = { navController.popBackStack() },
                        )
                        DisposableEffect(Unit) {
                            onDispose { movieViewModel.closeMoviePlayer() }
                        }
                    } else {
                        LoadingThenPop(navController, Routes.VIDEO_PLAYER)
                    }
                }
                composable(Routes.GALLERY_VIEWER) { backStackEntry ->
                    val itemId = backStackEntry.arguments?.getString("itemId")?.toLongOrNull()
                    if (itemId != null) {
                        GalleryViewerScreen(
                            itemId = itemId,
                            viewModel = galleryViewModel,
                            movieViewModel = movieViewModel,
                            onBack = { navController.popBackStack() },
                            onFilterTag = { tag ->
                                galleryViewModel.selectSingleTagFilter(tag)
                                navController.popBackStack(Routes.GALLERY, inclusive = false)
                            },
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }
            }
        }
    }

    if (isFullScreenRoute) {
        navHost()
    } else {
        val selectedRoute = ROOT_TABS.firstOrNull { route ->
            currentDestination?.hierarchy?.any { it.route == route } == true
        }
        val tabs = listOf(
            NavTab(Routes.COMICS, Icons.Filled.Book, Icons.Outlined.Book, t("Comics", "Komik")),
            NavTab(Routes.GALLERY, Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary, t("Gallery", "Galeri")),
            NavTab(Routes.MOVIES, Icons.Filled.Movie, Icons.Outlined.Movie, t("Movies", "Film")),
            NavTab(Routes.SETTINGS, Icons.Filled.Settings, Icons.Outlined.Settings, t("Settings", "Pengaturan")),
        )

        val isSmbBrowsing = currentRoute == Routes.SETTINGS && currentBrowserPath.isNotEmpty()

        Box(modifier = Modifier.fillMaxSize()) {
            navHost()
            if (!isSmbBrowsing) {
                CustomBottomNavBar(
                    tabs = tabs,
                    selectedRoute = selectedRoute,
                    onTabClick = { route ->
                        if (currentRoute == route) {
                            when (route) {
                                Routes.COMICS -> comicViewModel.requestScrollToTop()
                                Routes.GALLERY -> galleryViewModel.requestScrollToTop()
                                Routes.MOVIES -> movieViewModel.requestScrollToTop()
                                Routes.SETTINGS -> settingsViewModel.requestScrollToTop()
                            }
                        } else {
                            navigateToRootTab(route)
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun LoadingThenPop(navController: NavHostController, route: String) {
    LaunchedEffect(Unit) {
        if (navController.currentDestination?.route == route) {
            navController.popBackStack()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private class NavTab(
    val route: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val label: String,
)

@Composable
private fun CustomBottomNavBar(
    tabs: List<NavTab>,
    selectedRoute: String?,
    onTabClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = DeckSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            HorizontalDivider(thickness = 0.5.dp, color = DeckDivider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()

                    .navigationBarsPadding()
                    .height(76.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = tab.route == selectedRoute
                    CustomNavItem(
                        selected = selected,
                        onClick = { onTabClick(tab.route) },
                        icon = if (selected) tab.filledIcon else tab.outlinedIcon,
                        label = tab.label,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) DeckAccentBlue else DeckTextSecondary,
        label = "navContentColor"
    )
    val pillColor by animateColorAsState(

        targetValue = if (selected) DeckAccentPillBg else DeckAccentPillBg.copy(alpha = 0f),
        label = "navPillColor"
    )

    Column(
        modifier = Modifier
            .width(80.dp)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(32.dp)
                .background(pillColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
    }
}
