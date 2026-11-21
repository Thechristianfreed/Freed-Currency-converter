package com.freed.currency

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PIXEL6 = "w411dp-h914dp-420dpi"

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PIXEL6)
class ScreenTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var store: ConverterStore

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val seed = Rates.fromErApi(ctx.assets.open("seed_rates.json").bufferedReader().readText(), 0)
        store = ConverterStore(ctx.getSharedPreferences("t", 0), seed)
    }

    private lateinit var model: FakeModel

    private fun show(dark: Boolean = false) {
        model = FakeModel(store)
        rule.setContent { AppTheme(dark) { ConverterApp(model) } }
    }

    /** Stands in for the real app model: fixed prices and a saved year of THB history, no network. */
    class FakeModel(override val store: ConverterStore) : AppModel {
        private val thb = Parse.frankfurter(javaClass.classLoader!!.getResource("thb_1y.json")!!.readText(), "THB")
        private val seed = Parse.fromJson(
            ApplicationProvider.getApplicationContext<Context>().assets.open("seed_prices.json").bufferedReader().readText())
        override val refreshing = false
        override val failed = false
        override fun refresh(force: Boolean) {}
        override val pricesBusy = false
        override val pricesMissing = emptyList<String>()
        override fun latest(item: Item): Point? = when (item.feed) {
            Feed.METAL -> Point(20778, if (item.ref == "XAU") 3653.45 else 44.76)
            Feed.OIL -> Point(20774, 89.53)
            Feed.BLS -> seed[item.ref]?.lastOrNull()
        }
        override fun refreshPrices(force: Boolean) {}
        override var chart by mutableStateOf<ChartUi?>(null)
        override fun openChart(key: String) { chart = series(key, Span.Y1) }
        override fun setSpan(span: Span) { chart = series(chart!!.key, span) }
        override fun closeChart() { chart = null }
        private fun series(key: String, span: Span): ChartUi {
            val item = Catalog.byId(key.substringAfter(':'))
            val pts = if (item == null) thb else seed[item.ref].orEmpty().takeLast(span.months.toInt() + 1)
            return ChartUi(key, span, loading = false, points = pts)
        }
    }

    @Test fun graphOpensFromRowButtonAndScrubs() {
        show()
        rule.onNodeWithTag("chart-THB").performClick()
        rule.onNodeWithTag("chart-value").assertTextEquals("33.90 THB")
        rule.onRoot().captureRoboImage("build/shots/chart-fx.png")
        rule.onNodeWithTag("chart").performTouchInput { down(Offset(1f, height / 2f)) }
        rule.onNodeWithTag("chart-value").assertTextEquals("32.44 THB")
        rule.onNodeWithTag("chart-sub").assertTextEquals("Nov 19, 2025")
        rule.onNodeWithTag("chart").performTouchInput { up() }
        rule.onNodeWithTag("chart-flip").performClick()
        rule.onNodeWithTag("chart-value").assertTextEquals("0.0295 USD")
        rule.onNodeWithTag("span-5Y").performClick()
        assertEquals(Span.Y5, model.chart!!.span)
        rule.onNodeWithTag("chart-close").performClick()
        rule.onNodeWithTag("row-THB").assertExists()
    }

    @Test fun pricesTabConvertsAndOpensGraph() {
        show(dark = true)
        rule.onNodeWithTag("tab-Prices").performClick()
        rule.onNodeWithTag("price-gold").assertExists()
        rule.onRoot().captureRoboImage("build/shots/prices-usd.png")
        rule.onNodeWithTag("showin-THB").performClick()
        rule.onRoot().captureRoboImage("build/shots/prices-thb.png")
        rule.onNodeWithTag("price-eggs").performClick()
        assertEquals("item:eggs", model.chart!!.key)
        rule.onNodeWithTag("span-10Y").performClick()
        rule.onRoot().captureRoboImage("build/shots/chart-eggs.png")
    }

    private fun type(keys: String) = keys.forEach { rule.onNodeWithTag("key-$it").performClick() }

    @Test fun typeInUsdThenInAnotherRow() {
        show()
        type("250")
        rule.onNodeWithTag("amount-USD", useUnmergedTree = true).assertTextEquals("250")
        val thb = store.rates.perUsd["THB"]!!
        rule.onNodeWithTag("amount-THB", useUnmergedTree = true).assertTextEquals(Fmt.amount(250 * thb, "THB", store.rates))
        rule.onRoot().captureRoboImage("build/shots/main-light.png")

        rule.onNodeWithTag("row-THB").performTouchInput { click(Offset(width * 0.8f, height / 2f)) }
        assertEquals("THB", store.active)
        type("1000")
        rule.onNodeWithTag("amount-THB", useUnmergedTree = true).assertTextEquals("1,000")
        rule.onNodeWithTag("amount-USD", useUnmergedTree = true).assertTextEquals(Fmt.amount(1000 / thb, "USD", store.rates))
    }

    @Test fun longPressDragReorders() {
        show()
        assertEquals("THB", store.codes[0])
        rule.onNodeWithTag("row-THB").performTouchInput {
            down(Offset(width * 0.8f, height / 2f))
            advanceEventTime(800)
            repeat(10) { moveBy(Offset(0f, height * 0.25f)); advanceEventTime(16) }
            up()
        }
        rule.waitForIdle()
        assertEquals(listOf("JPY", "CNY", "THB"), store.codes.take(3))
        assertEquals("USD", store.active) // a drag must not also count as a tap
    }

    @Test fun swapCurrencyFromPicker() {
        show()
        rule.onNodeWithTag("swap-GTQ").performClick()
        rule.onNodeWithTag("search").performTextInput("viet")
        rule.onRoot().captureRoboImage("build/shots/picker.png")
        rule.onNodeWithTag("pick-VND").performClick()
        assertEquals("VND", store.codes.last())
        rule.onNodeWithTag("row-VND").assertExists()
    }

    @Test fun feeDialogAndDarkTheme() {
        store.select("LAK"); "5000000".forEach { store.press(it.toString()) }
        store.setFee(true, 3.0)
        show(dark = true)
        rule.onRoot().captureRoboImage("build/shots/main-dark-fee.png")
        rule.onNodeWithTag("fee-chip").performClick()
        rule.onNodeWithTag("fee-switch").performClick()
        assertEquals(false, store.feeOn)
    }
}
