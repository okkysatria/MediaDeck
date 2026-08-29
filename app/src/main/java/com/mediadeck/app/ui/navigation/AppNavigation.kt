package com.mediadeck.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import com.mediadeck.app.ui.screens.ComicReaderScreen
import com.mediadeck.app.ui.screens.ComicsScreen
import com.mediadeck.app.ui.screens.GalleryScreen
import com.mediadeck.app.ui.screens.GalleryViewerScreen
import com.mediadeck.app.ui.screens.MoviesScreen
import com.mediadeck.app.ui.screens.SettingsScreen
import com.mediadeck.app.ui.screens.VideoPlayerScreen
import com.mediadeck.app.util.i18n.t
import com.mediadeck.app.viewmodel.ComicViewModel
import com.mediadeck.app.viewmodel.GalleryViewModel
import com.mediadeck.app.viewmodel.MovieViewModel
import com.mediadeck.app.viewmodel.ScannerViewModel
import com.mediadeck.app.viewmodel.SettingsViewModel
import androidx.compose.ui.graphics.vector.ImageVector

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

    val comicsCount by comicViewModel.totalComicsCount.collectAsState()
    val galleryCount by galleryViewModel.totalGalleryCount.collectAsState()
    val moviesCount by movieViewModel.totalMoviesCount.collectAsState()
    val isSmbOnline by settingsViewModel.isSmbOnline.collectAsState()

    LaunchedEffect(isSmbOnline) {
        comicViewModel.setSmbOnline(isSmbOnline)
        galleryViewModel.setSmbOnline(isSmbOnline)
        movieViewModel.setSmbOnline(isSmbOnline)
    }

    LaunchedEffect(Unit) {
        scannerViewModel.navigationRequest.collect { targetTab ->
            navController.navigate(targetTab) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val exitMessage = t("Press back again to exit", "Tekan sekali lagi untuk keluar")
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = currentDestination?.route == Routes.COMICS) {
        val now = System.currentTimeMillis()
        if ((now - lastBackPressTime) < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressTime = now
            android.widget.Toast.makeText(context, exitMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val isFullScreenRoute = currentDestination?.route in FULL_SCREEN_ROUTES

    val navHost = remember {
        movableContentOf {
            NavHost(
                navController = navController,
                startDestination = Routes.COMICS,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.COMICS) {
                    ComicsScreen(
                        viewModel = comicViewModel,
                        scannerViewModel = scannerViewModel,
                        onOpenComic = { comic ->
                            comicViewModel.openComic(context, comic)
                            navController.navigate(Routes.COMIC_READER)
                        },
                        onNavigateToScan = {
                            settingsViewModel.setSelectedSettingsTabIndex(1)
                            navController.navigate(Routes.SETTINGS)
                        },
                    )
                }
                composable(Routes.GALLERY) {
                    GalleryScreen(
                        viewModel = galleryViewModel,
                        scannerViewModel = scannerViewModel,
                        onOpenItem = { id ->
                            navController.navigate(Routes.galleryViewer(id))
                        },
                        onNavigateToScan = {
                            settingsViewModel.setSelectedSettingsTabIndex(1)
                            navController.navigate(Routes.SETTINGS)
                        },
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
                        onNavigateToScan = {
                            settingsViewModel.setSelectedSettingsTabIndex(1)
                            navController.navigate(Routes.SETTINGS)
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        scannerViewModel = scannerViewModel,
                    )
                }
                composable(Routes.COMIC_READER) {
                    val activeComic by comicViewModel.activeComic.collectAsState()
                    if (activeComic != null) {
                        ComicReaderScreen(
                            viewModel = comicViewModel,
                            onClose = {
                                navController.popBackStack()
                            },
                        )
                        DisposableEffect(Unit) {
                            onDispose {
                                comicViewModel.closeComicReader()
                            }
                        }
                    } else {
                        LaunchedEffect(Unit) {
                            if (navController.currentDestination?.route == Routes.COMIC_READER) {
                                navController.popBackStack()
                            }
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                composable(Routes.VIDEO_PLAYER) {
                    val activeMovie by movieViewModel.activeMovie.collectAsState()
                    if (activeMovie != null) {
                        VideoPlayerScreen(
                            viewModel = movieViewModel,
                            movie = activeMovie!!,
                            onClose = {
                                navController.popBackStack()
                            },
                        )
                        DisposableEffect(Unit) {
                            onDispose {
                                movieViewModel.closeMoviePlayer()
                            }
                        }
                    } else {
                        LaunchedEffect(Unit) {
                            if (navController.currentDestination?.route == Routes.VIDEO_PLAYER) {
                                navController.popBackStack()
                            }
                        }
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            CircularProgressIndicator()
                        }
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
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                tabItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.COMICS } == true,
                    onClick = {
                        if (currentDestination?.route == Routes.COMICS) comicViewModel.requestScrollToTop()
                        else navigateToTab(navController, Routes.COMICS)
                    },
                    icon = if (currentDestination?.hierarchy?.any { it.route == Routes.COMICS } == true) Icons.Filled.Book else Icons.Outlined.Book,
                    labelEn = "COMICS",
                    labelId = "KOMIK",
                    badgeCount = comicsCount,
                )
                tabItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.GALLERY } == true,
                    onClick = {
                        if (currentDestination?.route == Routes.GALLERY) galleryViewModel.requestScrollToTop()
                        else navigateToTab(navController, Routes.GALLERY)
                    },
                    icon = if (currentDestination?.hierarchy?.any { it.route == Routes.GALLERY } == true) Icons.Filled.PhotoLibrary else Icons.Outlined.PhotoLibrary,
                    labelEn = "GALLERY",
                    labelId = "GALERI",
                    badgeCount = galleryCount,
                )
                tabItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.MOVIES } == true,
                    onClick = {
                        if (currentDestination?.route == Routes.MOVIES) movieViewModel.requestScrollToTop()
                        else navigateToTab(navController, Routes.MOVIES)
                    },
                    icon = if (currentDestination?.hierarchy?.any { it.route == Routes.MOVIES } == true) Icons.Filled.Movie else Icons.Outlined.Movie,
                    labelEn = "MOVIES",
                    labelId = "FILM",
                    badgeCount = moviesCount,
                )
                tabItem(
                    selected = currentDestination?.hierarchy?.any { it.route == Routes.SETTINGS } == true,
                    onClick = {
                        if (currentDestination?.route == Routes.SETTINGS) settingsViewModel.requestScrollToTop()
                        else navigateToTab(navController, Routes.SETTINGS)
                    },
                    icon = if (currentDestination?.hierarchy?.any { it.route == Routes.SETTINGS } == true) Icons.Filled.Settings else Icons.Outlined.Settings,
                    labelEn = "SETTINGS",
                    labelId = "PENGATURAN",
                )
            },
        ) {
            navHost()
        }
    }
}

private fun NavigationSuiteScope.tabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    labelEn: String,
    labelId: String,
    badgeCount: Int = 0,
) {
    item(
        selected = selected,
        onClick = onClick,
        icon = {
            BadgedBox(badge = { if (badgeCount > 0) Badge { Text(badgeCount.toString()) } }) {
                Icon(icon, contentDescription = null)
            }
        },
        label = { Text(t(labelEn, labelId), fontWeight = FontWeight.ExtraBold, fontSize = 10.sp) },
    )
}

private fun navigateToTab(navController: NavController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
