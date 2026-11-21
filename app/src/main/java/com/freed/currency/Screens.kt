package com.freed.currency

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val DAY = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val SHORT_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)
private fun date(day: Long) = LocalDate.ofEpochDay(day)

/** The little "line going up and down" picture on the graph buttons. */
@Composable
fun ChartGlyph(color: Color, description: String) {
    Canvas(Modifier.size(20.dp).semantics { contentDescription = description }) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.05f, h * 0.80f); lineTo(w * 0.35f, h * 0.45f)
            lineTo(w * 0.58f, h * 0.65f); lineTo(w * 0.95f, h * 0.20f)
        }
        drawPath(p, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------- Prices tab

@Composable
fun PricesTab(model: AppModel, modifier: Modifier) {
    val store = model.store
    val scheme = MaterialTheme.colorScheme
    var showIn by rememberSaveable { mutableStateOf(HOME) }
    if (showIn != HOME && showIn !in store.codes) showIn = HOME

    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = !model.pricesBusy) { model.refreshPrices(force = true) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh prices", Modifier.size(20.dp), tint = scheme.primary)
                Spacer(Modifier.width(8.dp))
                val missing = model.pricesMissing
                Text(
                    when {
                        model.pricesBusy -> "Updating prices…"
                        missing.isNotEmpty() -> "Couldn't update ${missing.joinToString(", ")} · showing saved prices"
                        else -> "Prices in US dollars · tap to refresh"
                    },
                    fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2,
                    color = if (missing.isNotEmpty() && !model.pricesBusy) scheme.error else scheme.onSurfaceVariant,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show in", fontSize = 12.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, end = 8.dp))
            (listOf(HOME) + store.codes).forEach { code ->
                Chip(code, selected = code == showIn, tag = "showin-$code") { showIn = code }
                Spacer(Modifier.width(6.dp))
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            item { SectionTitle("Metals and energy") }
            items(Catalog.markets, key = { it.id }) { PriceRow(model, it, showIn) }
            item { SectionTitle("Groceries · US average store price") }
            items(Catalog.groceries, key = { it.id }) { PriceRow(model, it, showIn) }
            item {
                Text(
                    "Gold and silver: Frankfurter. Oil: US Energy Information Administration daily spot price. " +
                        "Groceries and gasoline: US Bureau of Labor Statistics monthly average prices " +
                        "(BLS.gov cannot vouch for the data or analyses derived from these data after retrieval). " +
                        "Other currencies use today's exchange rate.",
                    fontSize = 11.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) scheme.primary else scheme.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp).testTag(tag),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun PriceRow(model: AppModel, item: Item, showIn: String) {
    val scheme = MaterialTheme.colorScheme
    val rates = model.store.rates
    val last = model.latest(item)
    // Monthly figures are dated by month; daily ones by day.
    val asOf = last?.let { if (item.feed == Feed.BLS) date(it.day).format(MONTH) else date(it.day).format(SHORT_DAY) }
    val converted = last?.let { Converter.convert(it.v, HOME, showIn, rates, 0.0) }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerLow).clickable { model.openChart("item:${item.id}") }
            .padding(start = 12.dp, top = 9.dp, bottom = 9.dp).testTag("price-${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.emoji, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.unit, fontSize = 12.sp, lineHeight = 14.sp, color = scheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (converted != null) Fmt.money(converted, showIn, rates) else "—",
                fontSize = 18.sp, fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Text(
                when {
                    last == null -> "No price yet"
                    showIn != HOME -> "${Fmt.usd(last.v)} · $asOf"
                    else -> asOf ?: ""
                },
                fontSize = 11.sp, lineHeight = 13.sp, color = scheme.onSurfaceVariant,
            )
        }
        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            ChartGlyph(scheme.onSurfaceVariant, "Show ${item.name} history")
        }
    }
}

// ---------------------------------------------------------------- Graph screen

@Composable
fun ChartScreen(model: AppModel, chart: ChartUi) {
    val scheme = MaterialTheme.colorScheme
    val id = chart.key.substringAfter(':')
    val isFx = chart.key.startsWith("fx:")
    val item = if (isFx) null else Catalog.byId(id)
    val dayFmt = if (item?.feed == Feed.BLS) MONTH else DAY // grocery figures are monthly
    var flipped by rememberSaveable(chart.key) { mutableStateOf(false) }
    var picked by remember(chart.key, chart.span, flipped) { mutableStateOf<Int?>(null) }

    val points = remember(chart.points, flipped) {
        if (isFx && flipped) chart.points.map { Point(it.day, 1 / it.v) } else chart.points
    }
    val show: (Double) -> String = { v ->
        when {
            !isFx -> Fmt.usd(v)
            flipped -> "${Fmt.sig(v)} USD"
            else -> "${Fmt.sig(v)} $id"
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { model.closeChart() }, modifier = Modifier.testTag("chart-close")) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (isFx) "${CurrencyInfo.flag(id)}  ${CurrencyInfo.name(id)}" else "${item?.emoji}  ${item?.name}",
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !isFx -> "US dollars ${item?.unit}"
                        flipped -> "What 1 $id is worth in US dollars"
                        else -> "How many $id one US dollar buys"
                    },
                    fontSize = 12.sp, color = scheme.onSurfaceVariant,
                )
            }
            if (isFx) TextButton(onClick = { flipped = !flipped }, modifier = Modifier.testTag("chart-flip")) { Text("Flip") }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            val shown = picked?.let { points.getOrNull(it) } ?: points.lastOrNull()
            Text(shown?.let { show(it.v) } ?: " ", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.testTag("chart-value"))
            val first = points.firstOrNull()
            val sub = when {
                shown == null -> " "
                picked != null -> date(shown.day).format(dayFmt)
                first != null && first.v > 0 -> {
                    val pct = (shown.v - first.v) / first.v * 100
                    val arrow = if (pct > 0.005) "▲" else if (pct < -0.005) "▼" else "•"
                    "$arrow ${Fmt.pct(abs(pct))}% over ${chart.span.label} · ${date(shown.day).format(dayFmt)}"
                }
                else -> date(shown.day).format(dayFmt)
            }
            Text(sub, fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.testTag("chart-sub"))
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Span.entries.forEach { s -> Chip(s.label, selected = s == chart.span, tag = "span-${s.label}") { model.setSpan(s) } }
        }

        Box(Modifier.fillMaxWidth().weight(1f).padding(start = 16.dp, end = 8.dp), contentAlignment = Alignment.Center) {
            when {
                chart.loading -> CircularProgressIndicator()
                chart.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(chart.error, color = scheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                    TextButton(onClick = { model.setSpan(chart.span) }) { Text("Try again") }
                }
                else -> LineChart(points, chart.span, picked, { picked = it }, show)
            }
        }

        if (!chart.loading && chart.error == null && points.isNotEmpty()) {
            val lo = points.minBy { it.v }
            val hi = points.maxBy { it.v }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Stat("Low", show(lo.v), date(lo.day).format(dayFmt), Modifier.weight(1f))
                Stat("High", show(hi.v), date(hi.day).format(dayFmt), Modifier.weight(1f))
            }
            Text(
                if (points.size > 1) "Touch and slide along the graph to read any date." else "",
                fontSize = 12.sp, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, whenText: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = scheme.onSurfaceVariant)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
        Text(whenText, fontSize = 11.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun LineChart(
    points: List<Point>,
    span: Span,
    picked: Int?,
    onPick: (Int?) -> Unit,
    show: (Double) -> String,
) {
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant, fontFeatureSettings = "tnum")
    val lo = points.minOf { it.v }
    val hi = points.maxOf { it.v }
    val pad = if (hi > lo) (hi - lo) * 0.08 else (hi * 0.02).coerceAtLeast(1e-9)
    val bottom = lo - pad
    val top = hi + pad
    val firstDay = points.first().day
    val lastDay = points.last().day.coerceAtLeast(firstDay + 1)
    val ticks = listOf(hi, (hi + lo) / 2, lo).distinct()
    val tickText = ticks.map { measurer.measure(show(it).substringBefore(' '), label) }
    val gutter = (tickText.maxOf { it.size.width } + 20).toFloat()

    Canvas(
        Modifier.fillMaxSize().testTag("chart").pointerInput(points) {
            val plot = size.width - gutter
            fun nearest(x: Float): Int {
                val day = firstDay + (x / plot).coerceIn(0f, 1f) * (lastDay - firstDay)
                return points.indices.minBy { abs(points[it].day - day) }
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                onPick(nearest(down.position.x))
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { onPick(nearest(it.position.x)); it.consume() }
                } while (event.changes.any { it.pressed })
                onPick(null)
            }
        },
    ) {
        val axis = 22.dp.toPx()
        val plotW = size.width - gutter
        val plotH = size.height - axis
        fun x(day: Long) = (day - firstDay).toFloat() / (lastDay - firstDay) * plotW
        fun y(v: Double) = ((top - v) / (top - bottom)).toFloat() * plotH

        // Quiet guide lines with their values in the right-hand gutter.
        ticks.forEachIndexed { i, t ->
            drawLine(scheme.outlineVariant, Offset(0f, y(t)), Offset(plotW, y(t)), 1.dp.toPx())
            drawText(tickText[i], topLeft = Offset(plotW + 8.dp.toPx(), y(t) - tickText[i].size.height / 2f))
        }

        val line = Path()
        points.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.day), y(p.v)) else line.lineTo(x(p.day), y(p.v)) }
        val fill = Path().apply {
            addPath(line); lineTo(x(points.last().day), plotH); lineTo(x(firstDay), plotH); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.16f), Color.Transparent), 0f, plotH))
        drawPath(line, scheme.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Dates along the bottom: start, middle, end.
        val fmt = if (span.months <= 6) SHORT_DAY else MONTH
        listOf(0f, 0.5f, 1f).forEach { f ->
            val day = firstDay + ((lastDay - firstDay) * f).toLong()
            val t = measurer.measure(date(day).format(fmt), label)
            drawText(t, topLeft = Offset((plotW * f - t.size.width * f).coerceAtLeast(0f), plotH + 6.dp.toPx()))
        }

        val mark = picked?.let { points.getOrNull(it) } ?: points.last()
        if (picked != null) drawLine(scheme.outline, Offset(x(mark.day), 0f), Offset(x(mark.day), plotH), 1.dp.toPx())
        drawCircle(scheme.surface, 6.dp.toPx(), Offset(x(mark.day), y(mark.v)))
        drawCircle(scheme.primary, 4.dp.toPx(), Offset(x(mark.day), y(mark.v)))
    }
}
