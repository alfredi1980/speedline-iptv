package al.speedline.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

@Composable
fun SpeedlineBrand(fontSize: TextUnit = 34.sp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SPEEDLINE",
            color = Color(0xFF42B5FF),
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "IPTV",
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}
