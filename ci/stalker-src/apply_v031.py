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
    "        if (url == null) {",
    "            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {",
    "                Text(resolveError ?: \"Duke hapur kanalin…\", color = Color.White, fontSize = 20.sp)",
    "            }",
    "        } else if (current.type == ContentType.MOVIE || current.type == ContentType.SERIES) {",
])
if old not in s:
    raise RuntimeError('Expected PlayerScreen playback block not found')
player.write_text(s.replace(old, new, 1))

gradle = Path('build-src/SpeedlineIPTV/app/build.gradle.kts')
g = gradle.read_text()
g = g.replace('versionCode = 11', 'versionCode = 13')
g = g.replace('versionName = "0.2.1"', 'versionName = "0.3.1"')
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
