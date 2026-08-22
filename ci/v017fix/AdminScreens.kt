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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import al.speedline.iptv.data.XtreamRepository

// Compatibility guard for CI patch: import androidx.compose.ui.text.LocalTextStyle

@Composable
fun AdminPinScreen(
    expectedPin: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }

    LaunchedEffect(Unit) { requester.requestFocus() }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(requester)
            .focusable()
            .onPreviewKeyEvent { event ->
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
                            val newPin = pin + digit
                            if (newPin.length == expectedPin.length) {
                                if (newPin == expectedPin) {
                                    pin = newPin
                                    onSuccess()
                                } else {
                                    pin = ""
                                    error = true
                                }
                            } else {
                                pin = newPin
                            }
                        }
                        true
                    }

                    code == KeyEvent.KEYCODE_DEL -> {
                        pin = pin.dropLast(1)
                        error = false
                        true
                    }

                    code == KeyEvent.KEYCODE_BACK -> {
                        onBack()
                        true
                    }

                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(500.dp)
                .background(Color(0xE6071C3A), RoundedCornerShape(22.dp))
                .padding(34.dp),
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
                repeat(expectedPin.length) { index ->
                    Text(
                        text = if (index < pin.length) "●" else "○",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (index < pin.length) Color(0xFF29B6F6) else Color.White.copy(alpha = .55f)
                    )
                }
            }

            if (error) {
                Text("PIN i pasaktë — provo përsëri", color = Color(0xFFFF8A80), fontSize = 15.sp)
            } else {
                Text("Shkruaj PIN-in me tastet numerike", fontSize = 15.sp, color = Color.White.copy(alpha = .70f))
            }
        }
    }
}

@Composable
fun AdminSettingsScreen(
    repository: XtreamRepository,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val current = remember { repository.credentials() }
    var username by remember { mutableStateOf(current?.username.orEmpty()) }
    var password by remember { mutableStateOf(current?.password.orEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(650.dp)
                .background(Color(0xF2071C3A), RoundedCornerShape(22.dp))
                .padding(34.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row {
                Text("SPEED", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("LINE", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF29B6F6))
                Text(" IPTV — ADMIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            M3Text("Kredencialet e klientit", color = Color.White, fontSize = 18.sp)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { M3Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { M3Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "FSHIH PASSWORD" else "SHFAQ PASSWORD")
                }
                Button(onClick = onBack) { Text("ANULO") }
                Button(
                    enabled = !busy && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        busy = true
                        message = "Duke verifikuar…"
                        Thread {
                            val result = repository.loginBlocking(username, password)
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                busy = false
                                result.onSuccess {
                                    message = "U ruajt me sukses"
                                    onSaved()
                                }.onFailure {
                                    message = it.message ?: "Kredencialet nuk u pranuan"
                                }
                            }
                        }.start()
                    }
                ) { Text(if (busy) "PRIT…" else "RUAJ") }
            }
            message?.let { M3Text(it, color = Color.White.copy(alpha = .85f)) }
        }
    }
}
