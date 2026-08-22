package al.speedline.iptv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import al.speedline.iptv.data.*
import al.speedline.iptv.ui.*

private sealed interface Screen {
    data object Login : Screen
    data class Browse(val type: ContentType, val favorites: Boolean = false) : Screen
    data class Episodes(val title: String, val episodes: List<StreamItem>, val seriesId: Int) : Screen
    data class Player(val playlist: List<StreamItem>, val index: Int, val returnTo: Screen) : Screen
    data object AdminPin : Screen
    data object AdminSettings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val repo = XtreamRepository(this)

        setContent {
            SpeedlineTheme {
                var screen by remember {
                    mutableStateOf<Screen>(
                        if (repo.credentials() == null) Screen.Login else Screen.Browse(ContentType.LIVE)
                    )
                }
                var loginBusy by remember { mutableStateOf(false) }
                var loginError by remember { mutableStateOf<String?>(null) }
                var refreshing by remember { mutableStateOf(false) }
                var dataRevision by remember { mutableIntStateOf(0) }

                fun refreshNow() {
                    if (repo.credentials() == null || refreshing) return
                    refreshing = true
                    repo.syncAsync { result ->
                        refreshing = false
                        if (result.isSuccess) dataRevision++
                    }
                }

                LaunchedEffect(Unit) {
                    val syncIntervalMs = AppConfig.SYNC_INTERVAL_HOURS * 60L * 60L * 1000L
                    val lastSync = repo.lastSuccessfulSync()
                    val cacheIsStale = lastSync <= 0L || System.currentTimeMillis() - lastSync >= syncIntervalMs
                    if (repo.credentials() != null && cacheIsStale) refreshNow()
                }

                DisposableEffect(Unit) {
                    val handler = Handler(Looper.getMainLooper())
                    val task = object : Runnable {
                        override fun run() {
                            val age = System.currentTimeMillis() - repo.lastSuccessfulSync()
                            val syncIntervalMs = AppConfig.SYNC_INTERVAL_HOURS * 60L * 60L * 1000L
                            if (repo.credentials() != null && age >= syncIntervalMs) refreshNow()
                            handler.postDelayed(this, 60L * 60L * 1000L)
                        }
                    }
                    handler.postDelayed(task, 60L * 60L * 1000L)
                    onDispose { handler.removeCallbacks(task) }
                }

                val bg = Brush.linearGradient(
                    listOf(
                        Color(0xFF061A3A),
                        Color(0xFF0B4FA3),
                        Color(0xFF168BE0)
                    )
                )

                Box(Modifier.fillMaxSize().background(bg)) {
                    when (val s = screen) {
                        Screen.Login -> LoginScreen(loginBusy, loginError) { u, p ->
                            loginBusy = true
                            loginError = null
                            Thread {
                                val result = repo.loginBlocking(u, p)
                                runOnUiThread {
                                    loginBusy = false
                                    result.onSuccess {
                                        dataRevision++
                                        screen = Screen.Browse(ContentType.LIVE)
                                    }.onFailure {
                                        loginError = it.message ?: "Login failed"
                                    }
                                }
                            }.start()
                        }

                        is Screen.Browse -> BrowseScreen(
                            context = this@MainActivity,
                            repository = repo,
                            type = s.type,
                            favoritesOnly = s.favorites,
                            dataRevision = dataRevision,
                            onPlay = { list, idx -> screen = Screen.Player(list, idx, s) },
                            onSeries = { series ->
                                Thread {
                                    val result = repo.seriesEpisodesBlocking(series.id)
                                    runOnUiThread {
                                        result.onSuccess { eps -> screen = Screen.Episodes(series.name, eps, series.id) }
                                    }
                                }.start()
                            },
                            onMovies = { screen = Screen.Browse(ContentType.MOVIE) },
                            onAdminRequest = { screen = Screen.AdminPin },
                            onBack = {
                                when {
                                    s.favorites -> screen = Screen.Browse(ContentType.LIVE)
                                    s.type != ContentType.LIVE -> screen = Screen.Browse(ContentType.LIVE)
                                    else -> finish()
                                }
                            }
                        )

                        is Screen.Episodes -> EpisodesScreen(
                            title = s.title,
                            episodes = s.episodes,
                            onPlay = { list, idx -> screen = Screen.Player(list, idx, s) },
                            onBack = { screen = Screen.Browse(ContentType.SERIES) }
                        )

                        is Screen.Player -> PlayerScreen(
                            context = this@MainActivity,
                            repository = repo,
                            playlist = s.playlist,
                            startIndex = s.index,
                            onBack = { screen = s.returnTo }
                        )

                        Screen.AdminPin -> AdminPinScreen(
                            expectedPin = "1009",
                            onSuccess = { screen = Screen.AdminSettings },
                            onBack = { screen = Screen.Browse(ContentType.LIVE) }
                        )

                        Screen.AdminSettings -> AdminSettingsScreen(
                            repository = repo,
                            onSaved = {
                                dataRevision++
                                refreshNow()
                                screen = Screen.Browse(ContentType.LIVE)
                            },
                            onBack = { screen = Screen.Browse(ContentType.LIVE) }
                        )
                    }
                }
            }
        }
    }
}
