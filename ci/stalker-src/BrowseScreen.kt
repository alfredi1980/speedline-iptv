package al.speedline.iptv.ui

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import al.speedline.iptv.data.*
import kotlinx.coroutines.delay

@Composable
fun BrowseScreen(
    context: Context,
    repository: XtreamRepository,
    type: ContentType,
    favoritesOnly: Boolean = false,
    dataRevision: Int = 0,
    onPlay: (List<StreamItem>, Int) -> Unit,
    onSeries: (StreamItem) -> Unit,
    onMovies: () -> Unit,
    onAdminRequest: () -> Unit,
    onBack: () -> Unit
) {
    val favorites = remember { FavoritesStore(context) }
    var favoriteRevision by remember { mutableIntStateOf(0) }
    val categories = remember(type, dataRevision) { repository.categories(type) }
    val allId = "__ALL__"
    val allLabel = when (type) {
        ContentType.LIVE -> "TE GJITHA"
        ContentType.MOVIE -> "TE GJITHË"
        ContentType.SERIES -> "TË GJITHA"
    }

    var selectedCategory by remember(type, dataRevision) { mutableStateOf(allId) }
    var streams by remember(type, selectedCategory, favoritesOnly, favoriteRevision, dataRevision) {
        mutableStateOf(
            repository.streams(
                type,
                if (favoritesOnly || selectedCategory == allId) null else selectedCategory
            ).let {
                if (favoritesOnly) favorites.filter(it) else it
            }
        )
    }

    var zeroTapCount by remember { mutableIntStateOf(0) }
    var lastZeroTap by remember { mutableLongStateOf(0L) }
    var focusStreamsAfterCategory by remember { mutableStateOf(false) }
    val firstStreamRequester = remember { FocusRequester() }

    fun selectCategory(id: String) {
        selectedCategory = id
        streams = repository.streams(type, if (id == allId) null else id)
        focusStreamsAfterCategory = true
    }

    LaunchedEffect(streams, focusStreamsAfterCategory) {
        if (focusStreamsAfterCategory && streams.isNotEmpty()) {
            delay(80)
            runCatching { firstStreamRequester.requestFocus() }
            focusStreamsAfterCategory = false
        }
    }

    val title = when {
        favoritesOnly -> "FAVORITET"
        type == ContentType.LIVE -> "KANALET"
        type == ContentType.MOVIE -> "FILMA"
        type == ContentType.SERIES -> "SERIALE"
        else -> type.name
    }
    val itemLabel = when (type) {
        ContentType.LIVE -> "kanale"
        ContentType.MOVIE -> "filma"
        ContentType.SERIES -> "seriale"
    }

    val panel = Color(0xAA071C3A)
    val card = Color(0xB8254772)
    val cyan = Color(0xFF29B6F6)
    val liveCard = Color(0xFF23C552)
    val liveFocusedCard = Color(0xFF39E86B)
    val listBackground = if (type == ContentType.LIVE) Color(0xCC0A2644) else Color(0x99051632)

    Row(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_0 || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_0)
                ) {
                    val now = SystemClock.elapsedRealtime()
                    zeroTapCount = if (now - lastZeroTap <= 900L) zeroTapCount + 1 else 1
                    lastZeroTap = now
                    if (zeroTapCount >= 5) {
                        zeroTapCount = 0
                        onAdminRequest()
                        true
                    } else false
                } else false
            }
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Column(
            Modifier
                .width(270.dp)
                .fillMaxHeight()
                .background(panel, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x5534A9FF), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SPEED", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("LINE", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = cyan)
            }
            Text("I P T V", fontSize = 14.sp, color = Color.White.copy(alpha = 0.86f))
            Spacer(Modifier.height(22.dp))
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(14.dp))

            if (!favoritesOnly) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        val selected = selectedCategory == allId
                        Button(
                            onClick = { selectCategory(allId) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = if (selected) Color(0xCC147CE5) else card,
                                focusedContainerColor = Color(0xFF168BE0)
                            )
                        ) { Text(allLabel, maxLines = 1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    }

                    itemsIndexed(categories) { _, cat ->
                        val selected = cat.id == selectedCategory
                        Button(
                            onClick = { selectCategory(cat.id) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = if (selected) Color(0xCC147CE5) else card,
                                focusedContainerColor = Color(0xFF168BE0)
                            )
                        ) { Text(cat.name, maxLines = 1, fontSize = 15.sp) }
                    }
                }
                if (type == ContentType.LIVE) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onMovies,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.colors(containerColor = card, focusedContainerColor = Color(0xFF168BE0))
                    ) { Text("FILMA", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.colors(containerColor = Color(0x80445A78), focusedContainerColor = Color(0xFF168BE0))
            ) { Text("← BACK", fontSize = 14.sp) }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(listBackground, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0x6634A9FF), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("${streams.size} $itemLabel", fontSize = 17.sp, color = cyan)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(streams, key = { _, item -> "${item.type}-${item.id}" }) { index, item ->
                    val fav = favorites.isFavorite(item)
                    val numberPrefix = if (item.type == ContentType.LIVE) {
                        "${item.channelNumber ?: (index + 1)}   "
                    } else ""
                    val itemModifier = if (index == 0) {
                        Modifier.fillMaxWidth().height(if (type == ContentType.LIVE) 50.dp else 46.dp).focusRequester(firstStreamRequester)
                    } else {
                        Modifier.fillMaxWidth().height(if (type == ContentType.LIVE) 50.dp else 46.dp)
                    }
                    Button(
                        onClick = {
                            if (item.type == ContentType.SERIES) onSeries(item) else onPlay(streams, index)
                        },
                        onLongClick = {
                            favorites.toggle(item)
                            favoriteRevision++
                        },
                        modifier = itemModifier,
                        colors = ButtonDefaults.colors(
                            containerColor = if (type == ContentType.LIVE) liveCard else card,
                            focusedContainerColor = if (type == ContentType.LIVE) liveFocusedCard else Color(0xFF147CE5)
                        )
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                numberPrefix,
                                fontSize = if (type == ContentType.LIVE) 17.sp else 14.sp,
                                color = if (type == ContentType.LIVE) Color(0xFF062A14) else cyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${if (fav) "★ " else ""}${item.name}",
                                maxLines = 1,
                                fontSize = if (type == ContentType.LIVE) 18.sp else 15.sp,
                                fontWeight = if (type == ContentType.LIVE) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (type == ContentType.LIVE) Color(0xFF03150A) else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
