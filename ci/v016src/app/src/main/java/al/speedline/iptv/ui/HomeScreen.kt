package al.speedline.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import al.speedline.iptv.data.ContentType

@Composable
fun HomeScreen(
    syncLabel: String,
    onBrowse: (ContentType) -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 54.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedlineBrand(fontSize = 34.sp)
            Text(syncLabel, fontSize = 14.sp, color = Color.White.copy(alpha = 0.72f))
        }

        Text(
            "Zgjidh përmbajtjen",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.86f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            HomeButton("KANALET") { onBrowse(ContentType.LIVE) }
            HomeButton("FILMA") { onBrowse(ContentType.MOVIE) }
            HomeButton("SERIALE") { onBrowse(ContentType.SERIES) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            HomeButton("FAVORITET", onFavorites)
            HomeButton("CILËSIMET", onSettings)
        }
    }
}

@Composable
private fun HomeButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(190.dp).height(96.dp)
    ) {
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}
