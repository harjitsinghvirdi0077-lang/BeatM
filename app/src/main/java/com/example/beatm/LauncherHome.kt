package com.example.beatm

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val NeonDefault = Color(0xFF00E5FF)
private val Night = Color(0xFF05060F)

data class GameApp(
    val name: String,
    val pkg: String,
    val art: ImageBitmap,
    val color: Color
)

// ---------- Sounds (generated in code, no files needed) ----------
object Sfx {
    private const val SR = 22050

    private class Seg(
        val f0: Double, val f1: Double,
        val startMs: Int, val durMs: Int, val vol: Double
    )

    private fun build(segs: List<Seg>): ShortArray {
        val total = segs.maxOf { it.startMs + it.durMs }
        val n = SR * total / 1000
        val out = DoubleArray(n)
        for (s in segs) {
            val start = SR * s.startMs / 1000
            val len = SR * s.durMs / 1000
            var phase = 0.0
            for (i in 0 until len) {
                val p = i.toDouble() / len
                val f = s.f0 + (s.f1 - s.f0) * p
                phase += 2.0 * Math.PI * f / SR
                val env = min(1.0, i / (SR * 0.004)) * (1.0 - p).pow(2.0)
                val idx = start + i
                if (idx < n) out[idx] += sin(phase) * env * s.vol
            }
        }
        return ShortArray(n) { (out[it].coerceIn(-1.0, 1.0) * 32767 * 0.8).toInt().toShort() }
    }

    private val tapData by lazy {
        build(listOf(Seg(1500.0, 900.0, 0, 80, 0.6), Seg(3000.0, 2000.0, 0, 40, 0.15)))
    }
    private val startData by lazy {
        build(
            listOf(
                Seg(523.0, 523.0, 0, 500, 0.25),
                Seg(659.0, 659.0, 110, 500, 0.25),
                Seg(784.0, 784.0, 220, 500, 0.25),
                Seg(1047.0, 1047.0, 330, 700, 0.25),
                Seg(200.0, 1200.0, 0, 450, 0.2)
            )
        )
    }
    private val launchData by lazy {
        build(listOf(Seg(250.0, 1800.0, 0, 450, 0.5), Seg(125.0, 900.0, 0, 450, 0.3)))
    }

    private fun play(data: ShortArray) {
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SR)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            t.write(data, 0, data.size)
            t.play()
            Handler(Looper.getMainLooper()).postDelayed({ t.release() }, data.size * 1000L / SR + 200)
        } catch (_: Exception) {
        }
    }

    fun tap() = play(tapData)
    fun startup() = play(startData)
    fun launch() = play(launchData)
}

// ---------- Games list ----------
private fun dominantColor(bmp: Bitmap): Color {
    val s = Bitmap.createScaledBitmap(bmp, 24, 24, true)
    val hsv = FloatArray(3)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    var ws = 0.0
    for (x in 0 until 24) for (y in 0 until 24) {
        val c = s.getPixel(x, y)
        if (android.graphics.Color.alpha(c) < 200) continue
        android.graphics.Color.colorToHSV(c, hsv)
        val w = (hsv[1] * hsv[1] * hsv[2]).toDouble() + 0.001
        r += android.graphics.Color.red(c) * w
        g += android.graphics.Color.green(c) * w
        b += android.graphics.Color.blue(c) * w
        ws += w
    }
    if (ws <= 0.0) return NeonDefault
    val avg = android.graphics.Color.rgb((r / ws).toInt(), (g / ws).toInt(), (b / ws).toInt())
    android.graphics.Color.colorToHSV(avg, hsv)
    if (hsv[1] < 0.2f) return NeonDefault
    hsv[1] = max(hsv[1], 0.7f)
    hsv[2] = max(hsv[2], 0.9f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Suppress("DEPRECATION")
fun loadGames(context: Context): List<GameApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter {
            val ai = it.activityInfo.applicationInfo
            ai.packageName != context.packageName &&
                (ai.category == ApplicationInfo.CATEGORY_GAME ||
                    (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0)
        }
        .distinctBy { it.activityInfo.packageName }
        .map {
            val bmp = it.loadIcon(pm).toBitmap(192, 192)
            GameApp(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                bmp.asImageBitmap(),
                dominantColor(bmp)
            )
        }
        .sortedBy { it.name.lowercase() }
}

fun openGame(context: Context, pkg: String): Boolean {
    val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
    context.startActivity(i)
    return true
}

// ---------- UI ----------
@Composable
fun LauncherHome(resumeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember(resumeTick) { loadGames(context) }
    var selected by remember { mutableIntStateOf(0) }
    val idx = selected.coerceIn(0, max(apps.lastIndex, 0))
    val current = apps.getOrNull(idx)
    val neon by animateColorAsState(current?.color ?: NeonDefault, tween(600), label = "neon")
    val listState = rememberLazyListState()
    val intro = remember { Animatable(0f) }
    val launchAnim = remember { Animatable(0f) }
    var launching by remember { mutableStateOf(false) }
    var firstRun by remember { mutableStateOf(true) }

    LaunchedEffect(resumeTick) {
        launching = false
        launchAnim.snapTo(0f)
        intro.snapTo(0f)
        if (firstRun) {
            firstRun = false
            Sfx.startup()
        }
        intro.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(idx) { listState.animateScrollToItem(max(idx - 1, 0)) }

    fun startGame(app: GameApp) {
        if (launching) return
        launching = true
        Sfx.launch()
        scope.launch {
            launchAnim.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            if (!openGame(context, app.pkg)) {
                launching = false
                launchAnim.snapTo(0f)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val l = launchAnim.value
                    val sc = (0.94f + 0.06f * intro.value) * (1f + 0.25f * l)
                    scaleX = sc
                    scaleY = sc
                    alpha = intro.value * (1f - 0.9f * l)
                }
        ) {
            // Big background themed to the selected game
            Crossfade(targetState = current, animationSpec = tween(500), label = "bg") { app ->
                if (app != null) {
                    Box(Modifier.fillMaxSize()) {
                        Image(
                            bitmap = app.art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(60.dp)
                                .graphicsLayer { alpha = 0.55f }
                        )
                        Image(
                            bitmap = app.art,
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = 50.dp)
                                .size(330.dp)
                                .graphicsLayer { alpha = 0.4f }
                                .clip(RoundedCornerShape(64.dp))
                        )
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(neon.copy(alpha = 0.30f), Night.copy(alpha = 0.75f), Night)
                    )
                )
            )

            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "BEATM",
                        style = TextStyle(
                            color = neon,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            shadow = Shadow(color = neon, blurRadius = 24f)
                        )
                    )
                    Text(
                        "${apps.size} GAMES",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                if (current != null) {
                    Text(
                        current.name,
                        maxLines = 1,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            shadow = Shadow(color = neon, blurRadius = 30f)
                        )
                    )
                    Spacer(Modifier.height(14.dp))
                    val btnShape = RoundedCornerShape(50)
                    Box(
                        Modifier
                            .shadow(22.dp, btnShape, ambientColor = neon, spotColor = neon)
                            .clip(btnShape)
                            .background(Brush.horizontalGradient(listOf(neon, Color.White)))
                            .clickable { startGame(current) }
                            .padding(horizontal = 44.dp, vertical = 11.dp)
                    ) {
                        Text(
                            "PLAY",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp
                        )
                    }
                } else {
                    Text(
                        "Koi game nahi mila",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 20.sp
                    )
                }

                Spacer(Modifier.height(18.dp))

                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                ) {
                    itemsIndexed(apps) { index, app ->
                        val focused = index == idx
                        val s by animateFloatAsState(
                            if (focused) 1.25f else 0.88f,
                            spring(Spring.DampingRatioMediumBouncy),
                            label = "s"
                        )
                        val shape = RoundedCornerShape(22.dp)
                        Box(
                            Modifier
                                .padding(horizontal = 8.dp)
                                .graphicsLayer {
                                    val p = (intro.value * 1.8f - index * 0.1f).coerceIn(0f, 1f)
                                    translationY = (1f - p) * 90f
                                    alpha = p * (if (focused) 1f else 0.7f)
                                    scaleX = s
                                    scaleY = s
                                }
                        ) {
                            Image(
                                bitmap = app.art,
                                contentDescription = app.name,
                                modifier = Modifier
                                    .size(104.dp)
                                    .then(
                                        if (focused) Modifier.shadow(
                                            24.dp, shape,
                                            ambientColor = neon, spotColor = neon
                                        ) else Modifier
                                    )
                                    .clip(shape)
                                    .then(
                                        if (focused) Modifier.border(2.5.dp, neon, shape)
                                        else Modifier
                                    )
                                    .clickable {
                                        if (focused) {
                                            startGame(app)
                                        } else {
                                            selected = index
                                            Sfx.tap()
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }

        // Neon flash when a game opens
        Box(
            Modifier.fillMaxSize().drawBehind {
                val a = launchAnim.value
                if (a > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = a * 0.85f),
                                neon.copy(alpha = a * 0.6f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.5f, size.height * 0.8f),
                            radius = size.maxDimension * a * 1.2f + 1f
                        )
                    )
                }
            }
        )
    }
}
