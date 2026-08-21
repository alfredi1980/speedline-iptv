package al.speedline.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import al.speedline.iptv.data.CredentialsStore

@Composable
fun LoginScreen(busy: Boolean, error: String?, onLogin: (String, String) -> Unit) {
    val context = LocalContext.current
    val mac = remember { CredentialsStore(context.applicationContext).get().username }

    Box(Modifier.fillMaxSize().background(Color(0xFF071C3A)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(Color(0xE60A2B57), RoundedCornerShape(24.dp))
                .padding(horizontal = 42.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row {
                Text("SPEED", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("LINE", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color(0xFF29B6F6))
                Text(" IPTV", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("STALKER PORTAL", fontSize = 17.sp, color = Color.White.copy(alpha = .72f))
            Spacer(Modifier.height(4.dp))
            Text("MAC ADDRESS", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .78f))
            Text(mac, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Button(
                onClick = { if (!busy) onLogin(mac, "stalker") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "DUKE U LIDHUR…" else "HYR", fontSize = 20.sp)
            }
            if (!error.isNullOrBlank()) {
                Text(error, color = Color(0xFFFF8A80), fontSize = 15.sp)
            }
        }
    }
}
