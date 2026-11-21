package com.freed.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CoreTest {
    private val rates = Rates(mapOf("USD" to 1.0, "THB" to 34.0, "EUR" to 0.9, "LAK" to 22000.0, "JPY" to 160.0) +
        (1..20).associate { "Z%02d".format(it) to 1.0 }, 1000, 2000)

    @Test fun convertsThroughUsd() {
        assertEquals(3400.0, Converter.convert(100.0, "USD", "THB", rates, 0.0)!!, 1e-9)
        assertEquals(100.0, Converter.convert(3400.0, "THB", "USD", rates, 0.0)!!, 1e-9)
        assertEquals(90.0, Converter.convert(3400.0, "THB", "EUR", rates, 0.0)!!, 1e-9)
    }

    @Test fun feeGivesLessForeignAndCostsMoreUsd() {
        assertEquals(3298.0, Converter.convert(100.0, "USD", "THB", rates, 3.0)!!, 1e-9)
        assertEquals(100.0 / 0.97, Converter.convert(3400.0, "THB", "USD", rates, 3.0)!!, 1e-9)
    }

    @Test fun typing() {
        var s = ""
        for (k in listOf("1", "2", "3", "4", ".", "5", "0", "9")) s = Entry.press(s, k, false)
        assertEquals("1234.50", s)
        assertEquals("1,234.50", Fmt.input(s))
        assertEquals("1234.5", Entry.press(s, "⌫", false))
        assertEquals("", Entry.press(s, "C", false))
        assertEquals("0.", Entry.press("", ".", false))
        assertEquals("7", Entry.press("55.25", "7", true))
        assertEquals("5", Entry.press("0", "5", false))
        assertEquals("500", Entry.press("5", "00", false))
        assertEquals("0", Fmt.input(""))
        assertEquals("12.", Fmt.input("12."))
    }

    @Test fun formatting() {
        assertEquals("2,200,000", Fmt.amount(2_200_000.4, "LAK", rates))
        assertEquals("16,000", Fmt.amount(16000.0, "JPY", rates))
        assertEquals("3,400.00", Fmt.amount(3400.0, "THB", rates))
        assertEquals("22,000", Fmt.rate(22000.0))
        assertEquals("34.00", Fmt.rate(34.0))
        assertEquals("0.9000", Fmt.rate(0.9))
    }

    @Test fun flagsAndNames() {
        assertEquals("🇹🇭", CurrencyInfo.flag("THB"))
        assertEquals("🇪🇺", CurrencyInfo.flag("EUR"))
        assertEquals("Thai Baht", CurrencyInfo.name("THB"))
        assertEquals("Guatemalan Quetzal", CurrencyInfo.name("GTQ"))
    }

    @Test fun parsesBothSourcesAndRoundTrips() {
        val seed = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .assets.open("seed_rates.json").bufferedReader().readText()
        val a = Rates.fromErApi(seed, 5)
        assertTrue(a.perUsd.size > 150)
        for (c in DEFAULT_CODES) assertTrue(c, a.perUsd.containsKey(c))
        assertEquals(a, Rates.fromJson(a.toJson()))

        val body = (listOf("usd" to 1, "thb" to 33.9, "btc" to 0.00001, "xau" to 0.0003) +
            (1..25).map { "eur" to 0.91 } + listOf("gbp", "jpy", "cny", "lak", "mxn", "gtq", "cad", "aud", "chf",
            "nzd", "sek", "nok", "dkk", "pln", "czk", "huf", "inr", "krw", "sgd", "hkd", "vnd").map { it to 2 })
            .joinToString(",") { "\"${it.first}\":${it.second}" }
        val b = Rates.fromCurrencyApi("{\"date\":\"2026-11-21\",\"usd\":{$body}}", 9)
        assertEquals(33.9, b.perUsd["THB"]!!, 0.0)
        assertFalse(b.perUsd.containsKey("BTC"))
        assertFalse(b.perUsd.containsKey("XAU"))
        assertEquals(1795219200L, b.asOf)
    }
}
