package al.speedline.iptv.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
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
import androidx.tv.material3.Text
import al.speedline.iptv.data.XtreamRepository

@Composable
fun AdminPinScreen(expectedPin: String, onSuccess: () -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }

    Box(
        Modifier.fillMaxSize().focusRequester(requester).focusable().onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
            val code = event.nativeKeyEvent.keyCode
            val digit = when (code) {
                KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> "0"
                KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> "1"
                KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> "2"
                KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> "3"
                KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> "4"
                KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> "5"
                KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> "6"
                KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> "7"
                KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> "8"
                KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> "9"
                else -> null
            }
            when {
                digit != null -> {
                    error = false
                    if (pin.length < expectedPin.length) {
                        val next = pin + digit
                        if (next.length == expectedPin.length) {
                            if (next == expectedPin) { pin = next; onSuccess() } else { pin = ""; error = true }
                        } else pin = next
                    }
                    true
                }
                code == KeyEvent.KEYCODE_DEL -> { pin = pin.dropLast(1); error = false; true }
                code == KeyEvent.KEYCODE_BACK -> { onBack(); true }
                else -> false
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(500.dp).background(Color(0xE6071C3A), RoundedCornerShape(22.dp)).padding(34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row {
                Text("SPEED", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("LINE", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF29B6F6))
                Text(" IPTV", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("ADMIN", fontSize = 18.sp, color = Color.White.copy(alpha = .75f))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                repeat(expectedPin.length) { i ->
                    Text(if (i < pin.length) "●" else "○", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        color = if (i < pin.length) Color(0xFF29B6F6) else Color.White.copy(alpha=.55f))
                }
            }
            Text(if (error) "PIN i pasaktë — provo përsëri" else "Shkruaj PIN-in me tastet numerike",
                color = if (error) Color(0xFFFF8A80) else Color.White.copy(alpha=.70f), fontSize = 15.sp)
        }
    }
}

@Composable
fun AdminSettingsScreen(repository: XtreamRepository, onSaved: () -> Unit, onBack: () -> Unit) {
    var mac by remember { mutableStateOf(repository.stalkerMac()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(700.dp).background(Color(0xF2071C3A), RoundedCornerShape(22.dp)).padding(34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row {
                Text("SPEED", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("LINE", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF29B6F6))
                Text(" IPTV", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            OutlinedTextField(
                value = mac,
                onValueChange = { mac = it.uppercase() },
                label = { M3Text("MAC Address", color = Color.White) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth()
            )
            M3Text("Formati: 00:1A:79:XX:XX:XX", color = Color.White.copy(alpha=.72f), fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onBack) { Text("ANULO") }
                Button(enabled = !busy && mac.isNotBlank(), onClick = {
                    busy = true
                    message = "Duke verifikuar…"
                    Thread {
                        val result = repository.updateMacBlocking(mac)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            busy = false
                            result.onSuccess { message = "MAC u ruajt me sukses"; onSaved() }
                                .onFailure { message = it.message ?: "MAC nuk u pranua" }
                        }
                    }.start()
                }) { Text(if (busy) "PRIT…" else "RUAJ") }
            }
            message?.let { M3Text(it, color = Color.White.copy(alpha=.88f)) }
        }
    }
}
