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
import androidx.compose.ui.focus.onFocusChanged
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
    val allCategoryId = "__speedline_all__"
    val favorites = remember { FavoritesStore(context) }
    var favoriteRevision by remember { mutableIntStateOf(0) }

    val categories = remember(type, dataRevision) {
        val sourceCategories = repository.categories(type).filterNot { category ->
            val label = category.name.trim().uppercase()
            label == "ALL" ||
                label == "TE GJITHA" ||
                label == "TE GJITHË" ||
                label == "TË GJITHA" ||
                label == "TË GJITHË"
        }
        val allLabel = when (type) {
            ContentType.LIVE -> "TË GJITHA"
            ContentType.MOVIE -> "TË GJITHË"
            ContentType.SERIES -> "TË GJITHA"
        }
        listOf(Category(allCategoryId, allLabel)) + sourceCategories
    }

    var selectedCategory by remember(type, dataRevision) {
        mutableStateOf(categories.firstOrNull()?.id)
    }

    var streams by remember(type, selectedCategory, favoritesOnly, favoriteRevision, dataRevision) {
        mutableStateOf(
            repository.streams(
                type,
                if (favoritesOnly || selectedCategory == allCategoryId) null else selectedCategory
            ).let {
                if (favoritesOnly) favorites.filter(it) else it
            }
        )
    }

    var zeroTapCount by remember { mutableIntStateOf(0) }
    var lastZeroTap by remember { mutableLongStateOf(0L) }
    var focusStreamsAfterCategory by remember { mutableStateOf(false) }
    var initialFocusDone by remember(type, dataRevision) { mutableStateOf(false) }
    val firstStreamRequester = remember { FocusRequester() }

    fun selectCategory(id: String) {
        selectedCategory = id
        streams = repository.streams(type, if (id == allCategoryId) null else id)
        focusStreamsAfterCategory = true
    }

    LaunchedEffect(streams, focusStreamsAfterCategory, initialFocusDone) {
        if (streams.isNotEmpty() && (focusStreamsAfterCategory || !initialFocusDone)) {
            delay(if (focusStreamsAfterCategory) 70 else 140)
            runCatching { firstStreamRequester.requestFocus() }
            focusStreamsAfterCategory = false
            initialFocusDone = true
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

    val cyan = Color(0xFF35DDF4)
    val sidePanel = Color(0xD0121922)
    val listPanel = Color(0xDF111820)
    val categoryCard = Color(0xA926313F)
    val categorySelected = Color(0xD51A6FB2)

    Row(
        Modifier
            .fillMaxSize()
            .background(Color(0xB2070E16))
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
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            Modifier
                .width(235.dp)
                .fillMaxHeight()
                .background(sidePanel, RoundedCornerShape(14.dp))
                .border(1.dp, Color(0x5535DDF4), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SPEED", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("LINE", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = cyan)
            }
            Text("I P T V", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
            Spacer(Modifier.height(18.dp))
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(Modifier.height(10.dp))

            if (!favoritesOnly) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(categories, key = { _, cat -> cat.id }) { _, cat ->
                        val selected = cat.id == selectedCategory
                        Button(
                            onClick = { selectCategory(cat.id) },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.colors(
                                containerColor = if (selected) categorySelected else categoryCard,
                                focusedContainerColor = Color(0xFF207FC3)
                            )
                        ) {
                            Text(cat.name, maxLines = 1, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }

                if (type == ContentType.LIVE) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onMovies,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = categoryCard,
                            focusedContainerColor = Color(0xFF207FC3)
                        )
                    ) {
                        Text("FILMA", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                colors = ButtonDefaults.colors(
                    containerColor = Color(0x80303B49),
                    focusedContainerColor = Color(0xFF207FC3)
                )
            ) {
                Text("← BACK", fontSize = 13.sp)
            }
        }

        Column(
            Modifier
                .weight(1f)
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
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("${streams.size} $itemLabel", fontSize = 14.sp, color = cyan)
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(streams, key = { _, item -> "${item.type}-${item.id}" }) { index, item ->
                    val fav = favorites.isFavorite(item)
                    var rowFocused by remember(item.id, item.type) { mutableStateOf(false) }
                    val rowShape = RoundedCornerShape(9.dp)
                    val rowHeight = if (type == ContentType.LIVE) 48.dp else 46.dp

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(rowHeight)
                            .background(
                                if (rowFocused) Color(0xD52A3440) else Color.Transparent,
                                rowShape
                            )
                            .border(
                                width = if (rowFocused) 2.dp else 1.dp,
                                color = if (rowFocused) cyan else Color(0x2235DDF4),
                                shape = rowShape
                            )
                    ) {
                        val buttonModifier = Modifier
                            .fillMaxSize()
                            .then(if (index == 0) Modifier.focusRequester(firstStreamRequester) else Modifier)
                            .onFocusChanged { state ->
                                rowFocused = state.isFocused
                            }

                        Button(
                            onClick = {
                                if (item.type == ContentType.SERIES) onSeries(item) else onPlay(streams, index)
                            },
                            onLongClick = {
                                favorites.toggle(item)
                                favoriteRevision++
                            },
                            modifier = buttonModifier,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent
                            )
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.type == ContentType.LIVE) {
                                    Text(
                                        text = "${item.channelNumber ?: (index + 1)}",
                                        modifier = Modifier.width(50.dp),
                                        fontSize = 16.sp,
                                        color = cyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "${if (fav) "★  " else ""}${item.name}",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    fontSize = if (type == ContentType.LIVE) 17.sp else 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )

                                Text(
                                    text = "›",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (rowFocused) cyan else Color.White.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
