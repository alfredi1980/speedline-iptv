package al.speedline.iptv.data

import android.content.Context
import android.provider.Settings
import java.net.NetworkInterface

class CredentialsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("speedline_stalker_auth", Context.MODE_PRIVATE)

    fun get(): Credentials? {
        if (!prefs.getBoolean("activated", false)) return null
        return Credentials(currentMac(), "stalker")
    }

    fun currentMac(): String {
        val saved = prefs.getString("mac", null)?.uppercase()?.takeIf { isValidMac(it) }
        if (saved != null) return saved
        val generated = generateDeviceMac()
        prefs.edit().putString("mac", generated).apply()
        return generated
    }

    fun save(credentials: Credentials) = saveMac(credentials.username, true)

    fun saveMac(mac: String, activate: Boolean = true) {
        val normalized = normalizeMac(mac)
        require(isValidMac(normalized)) { "MAC i pavlefshëm. Formati: 00:1A:79:XX:XX:XX" }
        prefs.edit().putString("mac", normalized).putBoolean("activated", activate).apply()
    }

    fun markActivated() = prefs.edit().putBoolean("activated", true).apply()

    fun clear() = prefs.edit().putBoolean("activated", false).apply()

    private fun generateDeviceMac(): String {
        val suffix = hardwareSuffix() ?: androidIdSuffix()
        return "00:1A:79:${suffix.substring(0,2)}:${suffix.substring(2,4)}:${suffix.substring(4,6)}"
    }

    private fun hardwareSuffix(): String? = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        val preferred = interfaces.sortedBy { iface ->
            when {
                iface.name.startsWith("eth", true) -> 0
                iface.name.startsWith("wlan", true) -> 1
                else -> 2
            }
        }
        preferred.firstNotNullOfOrNull { iface ->
            val hw = runCatching { iface.hardwareAddress }.getOrNull() ?: return@firstNotNullOfOrNull null
            if (hw.size < 3) return@firstNotNullOfOrNull null
            hw.takeLast(3).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        }
    }.getOrNull()

    private fun androidIdSuffix(): String {
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val hex = raw.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.uppercase()
        return hex.takeLast(6).padStart(6, '0')
    }

    private fun normalizeMac(value: String): String {
        val clean = value.uppercase().replace('-', ':').trim()
        if (isValidMac(clean)) return clean
        val hex = clean.filter { it.isDigit() || it in 'A'..'F' }
        return if (hex.length == 12) hex.chunked(2).joinToString(":") else clean
    }

    private fun isValidMac(value: String): Boolean = Regex("^00:1A:79:[0-9A-F]{2}:[0-9A-F]{2}:[0-9A-F]{2}$").matches(value)
}
