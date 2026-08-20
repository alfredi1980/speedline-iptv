from pathlib import Path
import re

player = Path('build-src/SpeedlineIPTV/app/src/main/java/al/speedline/iptv/ui/PlayerScreen.kt')
s = player.read_text()
old = "\n".join([
    "        val url = remember(current.id, current.type) { repository.playbackUrl(current) }",
    "        val vodUrls = remember(current.id, current.type, current.containerExtension, current.directSource) { repository.playbackUrls(current) }",
    "        if (current.type == ContentType.MOVIE || current.type == ContentType.SERIES) {",
])
new = "\n".join([
    "        var resolvedUrls by remember(current.id, current.type, current.directSource) { mutableStateOf<List<String>?>(null) }",
    "        var resolveError by remember(current.id, current.type, current.directSource) { mutableStateOf<String?>(null) }",
    "        val rootView = androidx.compose.ui.platform.LocalView.current",
    "",
    "        LaunchedEffect(current.id, current.type, current.directSource) {",
    "            resolvedUrls = null",
    "            resolveError = null",
    "            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {",
    "                runCatching { repository.playbackUrls(current) }",
    "            }",
    "            result.onSuccess { urls ->",
    "                resolvedUrls = urls.filter { it.isNotBlank() }.distinct()",
    "                if (resolvedUrls.isNullOrEmpty()) resolveError = \"Stream URL mungon\"",
    "            }.onFailure { e ->",
    "                resolveError = e.message ?: \"Kanali nuk mund të hapet\"",
    "            }",
    "        }",
    "",
    "        val vodUrls = resolvedUrls.orEmpty()",
    "        val url = resolvedUrls?.firstOrNull()",
    "        LaunchedEffect(current.id, current.type, url) {",
    "            if (current.type == ContentType.LIVE && !url.isNullOrBlank()) {",
    "                rootView.keepScreenOn = true",
    "                repeat(12) {",
    "                    rootView.postInvalidateOnAnimation()",
    "                    kotlinx.coroutines.delay(100)",
    "                }",
    "            }",
    "        }",
    "        if (url == null) {",
    "            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {",
    "                Text(resolveError ?: \"Duke hapur kanalin…\", color = Color.White, fontSize = 20.sp)",
    "            }",
    "        } else if (current.type == ContentType.MOVIE || current.type == ContentType.SERIES) {",
])
if old not in s:
    raise RuntimeError('Expected PlayerScreen playback block not found')
s = s.replace(old, new, 1)

# Live TV: keep the display/surface awake while audio/video is playing.
if 'keepScreenOn = true' not in s:
    s = s.replace(
        'useController = false',
        'useController = false\n                keepScreenOn = true',
        1
    )

# ARM32 Android TV boxes are often more stable/faster for MPEG-TS with IJK.
# Movies remain on VLC. Replace only the simple live Exo call when present.
live_replacements = [
    ('ExoSurface(url = url, modifier = Modifier.fillMaxSize())', 'IjkSurface(url = url, modifier = Modifier.fillMaxSize())'),
    ('ExoPlayerSurface(url = url, modifier = Modifier.fillMaxSize())', 'IjkSurface(url = url, modifier = Modifier.fillMaxSize())'),
]
for before, after in live_replacements:
    if before in s:
        s = s.replace(before, after, 1)
        break

player.write_text(s)

# Portal browse labels for the synthetic ALL category.
browse = Path('build-src/SpeedlineIPTV/app/src/main/java/al/speedline/iptv/ui/BrowseScreen.kt')
b = browse.read_text()
b = b.replace('ContentType.LIVE -> "TE GJITHA"', 'ContentType.LIVE -> "Te gjitha"')
b = b.replace('ContentType.MOVIE -> "TE GJITHË"', 'ContentType.MOVIE -> "Te gjithe"')
browse.write_text(b)

gradle = Path('build-src/SpeedlineIPTV/app/build.gradle.kts')
g = gradle.read_text()
g = g.replace('versionCode = 11', 'versionCode = 15')
g = g.replace('versionCode = 14', 'versionCode = 15')
g = g.replace('versionName = "0.2.1"', 'versionName = "0.3.3"')
g = g.replace('versionName = "0.3.2"', 'versionName = "0.3.3"')
g = re.sub(r'\n\s*splits\s*\{.*?\n\s*buildFeatures\s*\{', '\n\n    buildFeatures {', g, flags=re.S)
if 'splits {' not in g:
    split_block = '\n'.join([
        '',
        '    splits {',
        '        abi {',
        '            isEnable = true',
        '            reset()',
        '            include("arm64-v8a", "armeabi-v7a")',
        '            isUniversalApk = false',
        '        }',
        '    }',
        '',
        '    buildFeatures {'
    ])
    g = g.replace('\n    buildFeatures {', split_block, 1)
gradle.write_text(g)
