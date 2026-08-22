package al.speedline.iptv.ui

import android.content.Context
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.WindowManager
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.tv.material3.Text
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import al.speedline.iptv.AppConfig
import al.speedline.iptv.data.ContentType
import al.speedline.iptv.data.PlayerMode
import al.speedline.iptv.data.PlayerSettingsStore
import al.speedline.iptv.data.StreamItem
import al.speedline.iptv.data.XtreamRepository
import al.speedline.iptv.player.IjkReflectionPlayer
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    context: Context,
    repository: XtreamRepository,
    playlist: List<StreamItem>,
    startIndex: Int,
    onBack: () -> Unit
) {
    var index by remember {
        mutableIntStateOf(startIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0)))
    }
    var showChannelList by remember { mutableStateOf(false) }
    var listIndex by remember { mutableIntStateOf(index) }
    var numericEntry by remember { mutableStateOf("") }
    var numericRevision by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val settings = remember { PlayerSettingsStore(context) }
    val requestedMode = remember { settings.getMode() }
    var useIjk by remember {
        mutableStateOf(requestedMode == PlayerMode.IJK && IjkReflectionPlayer.isAvailable())
    }
    var useVlcFallback by remember { mutableStateOf(false) }
    val current = playlist.getOrNull(index) ?: return
    var playbackRevision by remember(current.id, current.type) { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val rootView = LocalView.current

    DisposableEffect(rootView, context) {
        // Prevent Android TV/Box ambient mode or screen saver from detaching/blanking
        // the video surface while playback continues with audio.
        rootView.keepScreenOn = true
        val activity = context as? android.app.Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            rootView.keepScreenOn = false
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(current.id, current.type) {
        val activeItem = current
        onDispose {
            // A Stalker create_link can stop being reusable as soon as its player
            // session is closed. Never reopen a recently visited channel with the
            // cached link; resolve a fresh link when the user returns to it.
            repository.invalidatePlayback(activeItem)
        }
    }

    fun resetAutoPlayer() {
        if (requestedMode == PlayerMode.AUTO) {
            useIjk = false
            useVlcFallback = false
        }
    }

    fun recoverLivePlayback() {
        if (current.type != ContentType.LIVE) return
        repository.invalidatePlayback(current)
        playbackRevision++
        resetAutoPlayer()
    }

    fun channelNumberAt(itemIndex: Int): Int =
        playlist[itemIndex].channelNumber ?: (itemIndex + 1)

    fun tuneChannelNumber(number: Int) {
        val target = playlist.indices.firstOrNull { channelNumberAt(it) == number } ?: return
        index = target
        listIndex = target
        showChannelList = false
        resetAutoPlayer()
    }

    fun commitNumericEntry() {
        val number = numericEntry.toIntOrNull()
        numericEntry = ""
        if (number != null) tuneChannelNumber(number)
    }

    fun appendDigit(digit: Int) {
        if (current.type != ContentType.LIVE) return
        numericEntry = if (numericEntry.length >= 4) digit.toString() else numericEntry + digit.toString()
        numericRevision++
    }

    BackHandler {
        when {
            numericEntry.isNotBlank() -> numericEntry = ""
            showChannelList -> showChannelList = false
            else -> onBack()
        }
    }

    fun zap(delta: Int) {
        if (playlist.isEmpty()) return
        numericEntry = ""
        index = (index + delta + playlist.size) % playlist.size
        listIndex = index
        resetAutoPlayer()
    }

    fun moveList(delta: Int) {
        if (playlist.isEmpty()) return
        listIndex = (listIndex + delta + playlist.size) % playlist.size
    }

    fun tuneSelectedChannel() {
        if (playlist.isEmpty()) return
        index = listIndex.coerceIn(0, playlist.lastIndex)
        showChannelList = false
        resetAutoPlayer()
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(showChannelList, listIndex) {
        if (showChannelList && playlist.isNotEmpty()) {
            runCatching { listState.scrollToItem(listIndex.coerceIn(0, playlist.lastIndex)) }
        }
    }

    LaunchedEffect(numericRevision) {
        if (numericEntry.isNotBlank()) {
            val snapshot = numericEntry
            delay(1_200)
            if (numericEntry == snapshot) commitNumericEntry()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }

                val keyCode = event.nativeKeyEvent.keyCode
                remoteDigit(keyCode)?.let { digit ->
                    if (current.type == ContentType.LIVE) {
                        if (event.nativeKeyEvent.repeatCount == 0) appendDigit(digit)
                        return@onPreviewKeyEvent true
                    }
                }

                when (keyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (showChannelList) moveList(-1) else zap(+1)
                        true
                    }

                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (showChannelList) moveList(+1) else zap(-1)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (showChannelList) moveList(-1) else zap(+1)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (showChannelList) moveList(+1) else zap(-1)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (current.type == ContentType.LIVE) {
                            if (numericEntry.isNotBlank()) {
                                commitNumericEntry()
                            } else if (showChannelList) {
                                tuneSelectedChannel()
                            } else {
                                listIndex = index
                                showChannelList = true
                            }
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_BACK -> {
                        when {
                            numericEntry.isNotBlank() -> numericEntry = ""
                            showChannelList -> showChannelList = false
                            else -> onBack()
                        }
                        true
                    }

                    else -> false
                }
            }
    ) {
        var resolvedUrls by remember(current.id, current.type, current.directSource, playbackRevision) {
            mutableStateOf<List<String>?>(null)
        }
        var resolveError by remember(current.id, current.type, current.directSource, playbackRevision) {
            mutableStateOf<String?>(null)
        }

        LaunchedEffect(current.id, current.type, current.directSource, playbackRevision) {
            resolvedUrls = null
            resolveError = null
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { repository.playbackUrls(current) }
            }
            result.onSuccess { urls ->
                resolvedUrls = urls.filter { it.isNotBlank() }.distinct()
                if (resolvedUrls.isNullOrEmpty()) resolveError = "Stream URL mungon"
            }.onFailure { error ->
                resolveError = error.message ?: "Kanali nuk mund të hapet"
            }
        }

        val url = resolvedUrls?.firstOrNull()
        if (url == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(resolveError ?: "Duke hapur kanalin…", color = Color.White, fontSize = 20.sp)
            }
        } else if (current.type == ContentType.MOVIE || current.type == ContentType.SERIES) {
            // VOD commonly contains codecs that are not exposed by MediaCodec on
            // low-cost Android TV boxes. LibVLC bundles a much wider decoder set.
            VlcSurface(
                url = url,
                modifier = Modifier.fillMaxSize(),
                enableVodControls = true
            )
        } else if (useVlcFallback) {
            VlcSurface(url = url, modifier = Modifier.fillMaxSize())
        } else if (useIjk && IjkReflectionPlayer.isAvailable()) {
            IjkSurface(url = url, modifier = Modifier.fillMaxSize())
        } else {
            Media3Surface(
                url = url,
                playbackRevision = playbackRevision,
                modifier = Modifier.fillMaxSize(),
                onFatalError = {
                    if (requestedMode == PlayerMode.AUTO) {
                        if (IjkReflectionPlayer.isAvailable()) {
                            useIjk = true
                        } else {
                            useVlcFallback = true
                        }
                    }
                },
                onPlaybackStalled = { recoverLivePlayback() }
            )
        }

        if (showChannelList && current.type == ContentType.LIVE) {
            ChannelListOverlay(
                playlist = playlist,
                selectedIndex = listIndex,
                listState = listState
            )
        }

        if (numericEntry.isNotBlank() && current.type == ContentType.LIVE) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(28.dp)
                    .background(Color(0xFF5AA9E6).copy(alpha = 0.82f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(numericEntry, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun remoteDigit(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
    else -> null
}

@Composable
private fun ChannelListOverlay(
    playlist: List<StreamItem>,
    selectedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState
) {
    val cyan = Color(0xFF35DDF4)
    val listPanel = Color(0xF5111820)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x76070E16))
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Column(
            Modifier
                .width(560.dp)
                .fillMaxHeight()
                .background(listPanel, RoundedCornerShape(14.dp))
                .border(1.dp, Color(0x6635DDF4), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("KANALET", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("${playlist.size} kanale", fontSize = 14.sp, color = cyan)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(playlist, key = { _, item -> "player-${item.id}" }) { itemIndex, item ->
                    val selected = itemIndex == selectedIndex
                    val number = item.channelNumber ?: (itemIndex + 1)
                    val rowShape = RoundedCornerShape(9.dp)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(if (selected) Color(0xD52A3440) else Color.Transparent, rowShape)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) cyan else Color(0x2235DDF4),
                                shape = rowShape
                            )
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$number",
                            modifier = Modifier.width(50.dp),
                            fontSize = 16.sp,
                            color = cyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.name,
                            modifier = Modifier.weight(1f),
                            fontSize = 17.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text("›", fontSize = 28.sp, color = cyan, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Media3Surface(
    url: String,
    playbackRevision: Int,
    modifier: Modifier,
    onFatalError: () -> Unit,
    onPlaybackStalled: () -> Unit
) {
    val context = LocalContext.current
    val latestOnFatalError by rememberUpdatedState(onFatalError)
    val latestOnPlaybackStalled by rememberUpdatedState(onPlaybackStalled)
    var firstVideoFrameRendered by remember { mutableStateOf(false) }
    var attachedTextureView by remember { mutableStateOf<TextureView?>(null) }
    val player = remember {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(AppConfig.APP_USER_AGENT)
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(10_000)
            .setAllowCrossProtocolRedirects(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMsForStreaming(
                2_000,
                10_000,
                350,
                750
            )
            .setPrioritizeTimeOverSizeThresholdsForStreaming(true)
            .build()
        val renderersFactory = DefaultRenderersFactory(context)
            .setAllowedVideoJoiningTimeMs(500)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                latestOnFatalError()
            }

            override fun onRenderedFirstFrame() {
                firstVideoFrameRendered = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            attachedTextureView?.let { view ->
                runCatching { player.clearVideoTextureView(view) }
            }
            player.release()
        }
    }

    LaunchedEffect(url, playbackRevision) {
        firstVideoFrameRendered = false
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        delay(3_000)
        if (!firstVideoFrameRendered && player.playbackState != Player.STATE_ENDED) {
            latestOnFatalError()
        }

        while (
            !firstVideoFrameRendered &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
        ) {
            delay(250)
        }
        if (!firstVideoFrameRendered) return@LaunchedEffect

        var lastPosition = player.currentPosition
        var stalledForMs = 0L
        while (true) {
            delay(1_000)
            val currentPosition = player.currentPosition
            val playbackExpected = player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED
            val progressed = currentPosition > lastPosition + 100L
            stalledForMs = if (playbackExpected && !progressed) stalledForMs + 1_000L else 0L
            if (stalledForMs >= 5_000L) {
                latestOnPlaybackStalled()
                return@LaunchedEffect
            }
            lastPosition = currentPosition
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isFocusable = false
                keepScreenOn = true
                isOpaque = true
                attachedTextureView = this
                player.setVideoTextureView(this)
                post { invalidate() }
            }
        },
        update = { view ->
            view.keepScreenOn = true
            if (attachedTextureView !== view) {
                attachedTextureView?.let { oldView ->
                    runCatching { player.clearVideoTextureView(oldView) }
                }
                attachedTextureView = view
                player.setVideoTextureView(view)
            }
            view.invalidate()
        }
    )
}

@Composable
private fun VlcSurface(
    url: String,
    modifier: Modifier,
    enableVodControls: Boolean = false
) {
    val context = LocalContext.current
    val libVlc = remember {
        LibVLC(
            context,
            arrayListOf(
                "--network-caching=1500",
                "--clock-jitter=0",
                "--clock-synchro=0"
            )
        )
    }
    val player = remember { VlcMediaPlayer(libVlc) }
    var videoLayout by remember { mutableStateOf<VLCVideoLayout?>(null) }
    var showVodControls by remember(url) { mutableStateOf(enableVodControls) }
    var controlsRevision by remember(url) { mutableIntStateOf(0) }

    fun revealVodControls() {
        if (!enableVodControls) return
        showVodControls = true
        controlsRevision++
    }

    fun handleVodKey(event: KeyEvent): Boolean {
        if (!enableVodControls || event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                revealVodControls()
                val currentTime = player.time.coerceAtLeast(0L)
                val length = player.length
                val target = if (length > 0L) {
                    kotlin.math.min(currentTime + 10_000L, length)
                } else {
                    currentTime + 10_000L
                }
                runCatching { player.time = target }
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                revealVodControls()
                runCatching { player.time = kotlin.math.max(0L, player.time - 10_000L) }
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                revealVodControls()
                if (event.repeatCount == 0) {
                    runCatching { if (player.isPlaying) player.pause() else player.play() }
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                revealVodControls()
                runCatching { player.play() }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                revealVodControls()
                runCatching { player.pause() }
                true
            }

            else -> false
        }
    }

    DisposableEffect(player, libVlc) {
        onDispose {
            runCatching { player.stop() }
            runCatching { player.detachViews() }
            runCatching { player.release() }
            runCatching { libVlc.release() }
        }
    }

    LaunchedEffect(url, enableVodControls, controlsRevision) {
        if (enableVodControls) {
            delay(4_000)
            showVodControls = false
        }
    }

    LaunchedEffect(url, videoLayout) {
        val layout = videoLayout ?: return@LaunchedEffect
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        player.attachViews(layout, null, false, false)
        val media = Media(libVlc, android.net.Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=1500")
            addOption(":http-reconnect=true")
        }
        player.media = media
        media.release()
        player.play()
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                VLCVideoLayout(ctx).also { view ->
                    view.keepScreenOn = true
                    view.isFocusable = enableVodControls
                    view.isFocusableInTouchMode = enableVodControls
                    view.setOnKeyListener { _, _, event -> handleVodKey(event) }
                    videoLayout = view
                    if (enableVodControls) view.post { view.requestFocus() }
                }
            },
            update = { view ->
                view.keepScreenOn = true
                view.isFocusable = enableVodControls
                view.isFocusableInTouchMode = enableVodControls
                view.setOnKeyListener { _, _, event -> handleVodKey(event) }
                if (videoLayout !== view) videoLayout = view
                if (enableVodControls && !view.hasFocus()) view.post { view.requestFocus() }
            }
        )

        if (showVodControls) {
            Text(
                text = "←  -10s      OK  PAUSE / PLAY      +10s  →",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp)
                    .background(Color(0x99000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun IjkSurface(url: String, modifier: Modifier) {
    val ijk = remember { IjkReflectionPlayer() }

    DisposableEffect(ijk) {
        onDispose { ijk.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceView(ctx).apply {
                isFocusable = false
                keepScreenOn = true
                tag = ""
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        tag = url
                        ijk.play(url, holder.surface) { }
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) = Unit

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        ijk.release()
                    }
                })
            }
        },
        update = { view ->
            if (view.holder.surface.isValid && view.tag != url) {
                view.tag = url
                ijk.play(url, view.holder.surface) { }
            }
        }
    )
}
