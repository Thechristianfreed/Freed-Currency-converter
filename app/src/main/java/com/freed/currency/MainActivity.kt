package com.freed.currency

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                LifecycleResumeEffect(Unit) {
                    vm.refresh(force = false)
                    vm.refreshPrices(force = false)
                    onPauseOrDispose { vm.store.save() }
                }
                ConverterApp(vm)
            }
        }
    }
}

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val colors = when {
        // On Android 12+ pick up the phone's own wallpaper-based colours.
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun ConverterApp(model: AppModel) {
    val store = model.store
    var pickerFor by rememberSaveable { mutableStateOf<String?>(null) }
    var showFee by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        val slot = pickerFor
        val chart = model.chart
        if (chart != null) {
            BackHandler { model.closeChart() }
            ChartScreen(model, chart)
        } else if (slot != null) {
            BackHandler { pickerFor = null }
            CurrencyPicker(
                store = store,
                slotCode = slot,
                onPick = { store.replace(slot, it); pickerFor = null },
                onClose = { pickerFor = null },
            )
        } else {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 10.dp)) {
                Tabs(tab) { tab = it }
                if (tab == 0) {
                    TopBar(store, model.refreshing, model.failed, { model.refresh(force = true) }) { showFee = true }
                    CurrencyRow(store, HOME, Modifier.height(68.dp), onSwap = null, onChart = null)
                    Spacer(Modifier.height(4.dp))
                    SlotList(store, Modifier.weight(1f), onChart = { model.openChart("fx:$it") }) { pickerFor = it }
                    Keypad(store::press)
                } else {
                    PricesTab(model, Modifier.weight(1f))
                }
            }
        }
    }
    if (showFee) FeeDialog(store) { showFee = false }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).height(38.dp).clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHigh).padding(3.dp),
    ) {
        listOf("Converter", "Prices").forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50))
                    .background(if (on) scheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }.testTag("tab-$label"),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) scheme.onSurface else scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TopBar(
    store: ConverterStore,
    refreshing: Boolean,
    failed: Boolean,
    onRefresh: () -> Unit,
    onFee: () -> Unit,
) {
    val rates = store.rates
    val date = remember(rates.asOf) { SimpleDateFormat("MMM d", Locale.US).format(Date(rates.asOf * 1000)) }
    val status = when {
        refreshing -> "Updating…"
        failed -> "Couldn't update · using saved rates"
        rates.fetchedAt == 0L -> "Built-in rates · tap to update"
        else -> "Checked ${ago(rates.fetchedAt)} · tap to refresh"
    }
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !refreshing, onClick = onRefresh).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh rates", Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Rates from $date", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(status, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1,
                    color = if (failed && !refreshing) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val on = store.feeOn && store.feePct > 0
        Box(
            Modifier.clip(RoundedCornerShape(50))
                .background(if (on) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent)
                .border(1.dp, if (on) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                .clickable(onClick = onFee).padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("fee-chip"),
        ) {
            Text(
                if (on) "Fee ${Fmt.pct(store.feePct)}%" else "No fee",
                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                color = if (on) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun ago(unixSeconds: Long): String {
    val mins = (System.currentTimeMillis() / 1000 - unixSeconds) / 60
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 48 * 60 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}

/** The eight swappable rows. Long-press a row and drag it up or down to reorder. */
@Composable
private fun SlotList(store: ConverterStore, modifier: Modifier, onChart: (String) -> Unit, onSwap: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    var dragCode by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }

    Column(modifier) {
        store.codes.forEach { code ->
            key(code) {
                val dragging = dragCode == code
                CurrencyRow(
                    store, code,
                    Modifier
                        .weight(1f)
                        .onSizeChanged { rowHeight = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            val s = if (dragging) 1.03f else 1f
                            scaleX = s; scaleY = s
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragCode = code
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val h = rowHeight.toFloat()
                                    if (h > 0) {
                                        // Each time the row is dragged past half a neighbour, they trade places.
                                        var i = store.codes.indexOf(code)
                                        while (dragOffset > h / 2 && i < store.codes.lastIndex) {
                                            store.move(i, i + 1); i++; dragOffset -= h
                                        }
                                        while (dragOffset < -h / 2 && i > 0) {
                                            store.move(i, i - 1); i--; dragOffset += h
                                        }
                                    }
                                },
                                onDragEnd = { dragCode = null; dragOffset = 0f; store.save() },
                                onDragCancel = { dragCode = null; dragOffset = 0f; store.save() },
                            )
                        },
                    onSwap = { onSwap(code) },
                    onChart = { onChart(code) },
                    lifted = dragging,
                )
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    store: ConverterStore,
    code: String,
    modifier: Modifier,
    onSwap: (() -> Unit)?,
    onChart: (() -> Unit)?,
    lifted: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val active = store.active == code
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        active -> scheme.primaryContainer
        lifted -> scheme.surfaceContainerHighest
        else -> scheme.surfaceContainerLow
    }
    val fg = if (active) scheme.onPrimaryContainer else scheme.onSurface
    val muted = if (active) scheme.onPrimaryContainer.copy(alpha = 0.75f) else scheme.onSurfaceVariant

    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(bg)
            .then(if (active) Modifier.border(1.5.dp, scheme.primary, shape) else Modifier)
            .clickable { store.select(code) }
            .testTag("row-$code"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .width(158.dp).fillMaxHeight()
                .then(if (onSwap != null) Modifier.clickable(onClick = onSwap).testTag("swap-$code") else Modifier)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(CurrencyInfo.flag(code), fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(code, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg)
                    if (onSwap != null) {
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change currency",
                            Modifier.size(20.dp), tint = muted)
                    }
                }
                Text(CurrencyInfo.name(code), fontSize = 11.sp, lineHeight = 13.sp, color = muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f).padding(end = if (onChart != null) 2.dp else 14.dp), horizontalAlignment = Alignment.End) {
            BasicText(
                text = store.amountText(code),
                modifier = Modifier.fillMaxWidth().testTag("amount-$code"),
                style = TextStyle(
                    color = fg, textAlign = TextAlign.End,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 25.sp, stepSize = 1.sp),
            )
            if (code != HOME) {
                Text(store.rateText(code), fontSize = 11.sp, lineHeight = 13.sp, color = muted, maxLines = 1)
            } else if (store.appliedFee > 0) {
                Text("Amounts include a ${Fmt.pct(store.appliedFee)}% exchange fee",
                    fontSize = 11.sp, lineHeight = 13.sp, color = muted, maxLines = 1)
            }
        }
        if (onChart != null) {
            Box(
                Modifier.width(40.dp).fillMaxHeight().clickable(onClick = onChart).testTag("chart-$code"),
                contentAlignment = Alignment.Center,
            ) { ChartGlyph(muted, "Show $code history") }
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val press = { k: String ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onKey(k)
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp).height(212.dp)) {
        Column(Modifier.weight(3f)) {
            listOf(listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf("00", "0", "."))
                .forEach { keys ->
                    Row(Modifier.weight(1f)) {
                        keys.forEach { k -> Key(k, Modifier.weight(1f), accent = false) { press(k) } }
                    }
                }
        }
        Column(Modifier.weight(1f)) {
            Key("⌫", Modifier.weight(1f), accent = true) { press("⌫") }
            Key("C", Modifier.weight(1f), accent = true) { press("C") }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, accent: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(16.dp))
            .background(if (accent) scheme.secondaryContainer else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .testTag("key-$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium,
            color = if (accent) scheme.onSecondaryContainer else scheme.onSurface)
    }
}

@Composable
private fun CurrencyPicker(store: ConverterStore, slotCode: String, onPick: (String) -> Unit, onClose: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(store.rates) {
        store.rates.perUsd.keys.filter { it != HOME }.map { it to CurrencyInfo.name(it) }.sortedBy { it.second }
    }
    val q = query.trim()
    val shown = if (q.isEmpty()) all else all.filter { (c, n) -> c.contains(q, true) || n.contains(q, true) }
    val scheme = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            Text("Replace $slotCode with…", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("search"),
            placeholder = { Text("Search by name or code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.first }) { (code, name) ->
                val listed = code in store.codes
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = code != slotCode) { onPick(code) }
                        .padding(horizontal = 16.dp, vertical = 11.dp).testTag("pick-$code"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(CurrencyInfo.flag(code), fontSize = 24.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(code, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    if (listed) {
                        Text(if (code == slotCode) "Current" else "On screen · trade places",
                            fontSize = 12.sp, color = scheme.primary)
                    }
                }
            }
            if (shown.isEmpty()) {
                item { Text("No currency matches “$q”.", Modifier.padding(16.dp), color = scheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun FeeDialog(store: ConverterStore, onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        title = { Text("Exchange fee") },
        text = {
            Column {
                Text(
                    "Rates shown are the mid-market rate. ATMs, cards and exchange booths give you a bit " +
                        "less. Turn this on to see what you'll really get after their cut.",
                    fontSize = 14.sp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Apply a ${Fmt.pct(store.feePct)}% fee", Modifier.weight(1f),
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = store.feeOn, onCheckedChange = { store.setFee(it, store.feePct) },
                        modifier = Modifier.testTag("fee-switch"))
                }
                Slider(
                    value = store.feePct.toFloat(),
                    onValueChange = { store.setFee(true, Math.round(it * 2) / 2.0) },
                    valueRange = 0f..10f,
                    steps = 19,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(1.0 to "1%", 3.0 to "3%", 5.0 to "5%", 8.0 to "8%").forEach { (v, label) ->
                        OutlinedButton(
                            onClick = { store.setFee(true, v) },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) { Text(label) }
                    }
                }
                Text("Typical: card 0–3%, ATM 3–5%, airport booth 8%+.", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Rates By Exchange Rate API",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(top = 14.dp)
                        .clickable { runCatching { uri.openUri("https://www.exchangerate-api.com") } },
                )
            }
        },
    )
}
