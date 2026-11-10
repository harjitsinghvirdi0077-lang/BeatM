package com.example.beatm

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

data class GameApp(val name: String, val pkg: String, val icon: Drawable)

fun loadApps(context: Context): List<GameApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { GameApp(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.loadIcon(pm)) }
        .filter { it.pkg != context.packageName }
        .sortedBy { it.name.lowercase() }
}

@Composable
fun LauncherHome() {
    val context = LocalContext.current
    val apps = remember { loadApps(context) }
    var selected by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A1A3A), Color(0xFF020409))))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    listOf("Games", "Favorites", "Recent").forEachIndexed { i, t ->
                        Text(
                            t,
                            color = if (i == 0) Color.White else Color.White.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                            fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            apps.getOrNull(selected)?.let { app ->
                Text(app.name, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable { launch(context, app.pkg) }
                        .padding(horizontal = 36.dp, vertical = 10.dp)
                ) { Text("Play", color = Color.Black, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(24.dp))

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            ) {
                itemsIndexed(apps) { index, app ->
                    val focused = index == selected
                    val scale by animateFloatAsState(
                        if (focused) 1.2f else 0.9f,
                        spring(Spring.DampingRatioMediumBouncy)
                    )
                    Image(
                        bitmap = app.icon.toBitmap(256, 256).asImageBitmap(),
                        contentDescription = app.name,
                        modifier = Modifier
                            .size(110.dp)
                            .scale(scale)
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(20.dp))
                                else Modifier
                            )
                            .clickable {
                                if (focused) launch(context, app.pkg) else selected = index
                            }
                    )
                }
            }
        }
    }
}

fun launch(context: Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
}
