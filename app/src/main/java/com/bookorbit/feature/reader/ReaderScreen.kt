package com.bookorbit.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    vm: ReaderViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val controller = remember { ReaderController() }
    var ready by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var tocVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Keep the screen on while reading.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        controller.listener = { event ->
            when (event) {
                is ReaderEvent.Ready -> ready = true
                is ReaderEvent.Loaded -> vm.onLoaded(event.toc, event.title)
                is ReaderEvent.Relocate -> vm.onRelocate(event.cfi, event.fraction, event.chapterTitle)
                is ReaderEvent.Error -> vm.onError(event.message)
            }
        }
    }

    val resolved = ui.resolved
    LaunchedEffect(ready, resolved) {
        if (ready && resolved != null && !opened) {
            opened = true
            withContext(Dispatchers.IO) {
                controller.open(openParamsFor(resolved.ref, resolved.format, resolved.initial, ui.settings), context)
            }
        }
    }

    val surface = themeBackgroundColor(ui.settings.themeName, ui.settings.isDark)
    val paginated = ui.settings.flow == "paginated"
    val showChrome = chromeVisible || !paginated
    val onSurface = if (ui.settings.isDark) Color.White else Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface),
    ) {
        ReaderWebView(controller = controller, modifier = Modifier.fillMaxSize())

        // Paging taps + chrome toggle (paginated mode), plus pinch-zoom for comics. Both gestures
        // live on ONE node so the tap overlay no longer swallows the pinch: tap position drives
        // prev / chrome / next exactly as the old three TapZones did, while a two-finger pinch drives
        // foliate's own zoom. For reflowable books the zoom command is a no-op (guarded in bridge.js),
        // so leaving the pinch handler on for every paginated book is harmless.
        if (paginated && !tocVisible && !settingsVisible) {
            val comicZoom = remember { mutableStateOf(1f) }
            val density = LocalDensity.current.density
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            when (offset.x / size.width.toFloat()) {
                                in 0f..0.3f -> controller.prev()
                                in 0.7f..1f -> controller.next()
                                else -> chromeVisible = !chromeVisible
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // A two-finger pinch (zoom != 1) drives foliate's zoom; a one-finger drag
                        // (zoom == 1) pans, but only once zoomed in, so paging taps stay untouched.
                        detectTransformGestures { _, pan, zoom, _ ->
                            if (kotlin.math.abs(zoom - 1f) > 0.001f) {
                                val z = (comicZoom.value * zoom).coerceIn(1f, 4f)
                                comicZoom.value = z
                                controller.zoom(String.format(Locale.US, "%.3f", z))
                            } else if (comicZoom.value > 1f) {
                                controller.panBy(pan.x / density, pan.y / density)
                            }
                        }
                    },
            )
        }

        if (showChrome) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC0A0A0A))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    ui.chapterTitle ?: ui.title ?: "",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { tocVisible = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Contents", tint = Color.White)
                }
                IconButton(onClick = { settingsVisible = true }) {
                    Icon(Icons.Filled.TextFields, contentDescription = "Settings", tint = Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC0A0A0A))
                    .navigationBarsPadding()
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${ui.percentage}%", color = Color.White.copy(alpha = 0.8f))
            }
        }

        if (!ui.loaded && ui.error == null) {
            Box(modifier = Modifier.fillMaxSize().background(surface), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        ui.error?.let { message ->
            Box(modifier = Modifier.fillMaxSize().background(surface), contentAlignment = Alignment.Center) {
                Column2(message = message, onBack = onBack, tint = onSurface)
            }
        }

        // One-time coach overlay teaching the (invisible) paginated tap zones.
        if (paginated && ui.loaded && ui.showPagingHint) {
            PagingHintOverlay(onDismiss = { vm.dismissPagingHint() })
        }
    }

    if (tocVisible) {
        ReaderTocSheet(
            toc = ui.toc,
            onSelect = { href ->
                tocVisible = false
                controller.goTo(href)
            },
            onDismiss = { tocVisible = false },
        )
    }
    if (settingsVisible) {
        ReaderSettingsSheet(
            settings = ui.settings,
            onChange = { updated ->
                vm.updateSettings(updated)
                controller.applyStyles(updated)
            },
            onDismiss = { settingsVisible = false },
        )
    }
}

@Composable
private fun PagingHintOverlay(onDismiss: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60A0A0A))
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
    ) {
        HintZone(weight = 0.3f, icon = Icons.Filled.ChevronLeft, label = "Previous page")
        HintZone(weight = 0.4f, icon = Icons.Filled.TouchApp, label = "Tap for menu")
        HintZone(weight = 0.3f, icon = Icons.Filled.ChevronRight, label = "Next page")
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HintZone(
    weight: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TapZone(weight: Float, onTap: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clickable(interactionSource = interaction, indication = null, onClick = onTap),
    )
}

@Composable
private fun Column2(message: String, onBack: () -> Unit, tint: Color) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        Text(message, color = tint)
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tint)
        }
    }
}
