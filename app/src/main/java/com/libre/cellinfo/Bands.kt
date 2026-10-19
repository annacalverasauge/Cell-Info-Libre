package com.libre.cellinfo

import java.util.Locale

/** Conversión de canales (EARFCN, NR-ARFCN, UARFCN, ARFCN) a banda y frecuencia de bajada. */
object Bands {
    private class Lte(val n: Int, val fLow: Double, val nOff: Int, val nMax: Int)

    // 3GPP TS 36.101: F_DL = F_DL_low + 0.1 * (N_DL - N_Offs-DL)
    private val LTE = listOf(
        Lte(1, 2110.0, 0, 599), Lte(2, 1930.0, 600, 1199), Lte(3, 1805.0, 1200, 1949),
        Lte(4, 2110.0, 1950, 2399), Lte(5, 869.0, 2400, 2649), Lte(7, 2620.0, 2750, 3449),
        Lte(8, 925.0, 3450, 3799), Lte(12, 729.0, 5010, 5179), Lte(13, 746.0, 5180, 5279),
        Lte(14, 758.0, 5280, 5379), Lte(17, 734.0, 5730, 5849), Lte(18, 860.0, 5850, 5999),
        Lte(19, 875.0, 6000, 6149), Lte(20, 791.0, 6150, 6449), Lte(21, 1495.9, 6450, 6599),
        Lte(25, 1930.0, 8040, 8689), Lte(26, 859.0, 8690, 9039), Lte(28, 758.0, 9210, 9659),
        Lte(32, 1452.0, 9920, 10359), Lte(38, 2570.0, 37750, 38249), Lte(39, 1880.0, 38250, 38649),
        Lte(40, 2300.0, 38650, 39649), Lte(41, 2496.0, 39650, 41589), Lte(42, 3400.0, 41590, 43589),
        Lte(43, 3600.0, 43590, 45589), Lte(46, 5150.0, 46790, 54539), Lte(66, 2110.0, 66436, 67335),
        Lte(71, 617.0, 68586, 68935)
    )

    private fun lteBand(earfcn: Int) = LTE.firstOrNull { earfcn in it.nOff..it.nMax }

    fun lte(earfcn: Int): String {
        val b = lteBand(earfcn) ?: return ""
        return "B${b.n} · ${fmt(b.fLow + 0.1 * (earfcn - b.nOff))} MHz"
    }

    fun lteShort(earfcn: Int): String = lteBand(earfcn)?.let { "B${it.n}" } ?: ""

    // Rangos de bajada NR más comunes (se toma la primera coincidencia)
    private val NR = listOf(
        "n78" to 620000..653333, "n77" to 620000..680000, "n28" to 151600..160600,
        "n20" to 158200..164200, "n8" to 185000..192000, "n1" to 422000..434000,
        "n3" to 361000..376000, "n7" to 524000..538000, "n41" to 499200..537999,
        "n5" to 173800..178800, "n71" to 123400..130400, "n258" to 2016667..2070832,
        "n257" to 2054166..2104165
    )

    fun nrFreq(arfcn: Int): Double? = when (arfcn) {
        in 0..599999 -> 0.005 * arfcn
        in 600000..2016666 -> 3000.0 + 0.015 * (arfcn - 600000)
        in 2016667..3279165 -> 24250.08 + 0.06 * (arfcn - 2016667)
        else -> null
    }

    fun nr(arfcn: Int): String {
        val f = nrFreq(arfcn) ?: return ""
        val band = NR.firstOrNull { arfcn in it.second }?.first
        return (if (band != null) "$band · " else "") + "${fmt(f)} MHz"
    }

    fun nrShort(arfcn: Int): String = NR.firstOrNull { arfcn in it.second }?.first ?: ""

    // UMTS: F_DL = offset + UARFCN / 5
    private class Umts(val n: String, val off: Double, val range: IntRange)
    private val UMTS = listOf(
        Umts("B1", 0.0, 10562..10838), Umts("B2", 0.0, 9662..9938), Umts("B4", 1805.0, 1537..1738),
        Umts("B5", 0.0, 4357..4458), Umts("B8", 340.0, 2937..3088)
    )

    fun umts(uarfcn: Int): String {
        val b = UMTS.firstOrNull { uarfcn in it.range } ?: return ""
        return "${b.n} · ${fmt(b.off + uarfcn / 5.0)} MHz"
    }

    fun gsm(arfcn: Int): String = when (arfcn) {
        in 1..124 -> "900 · ${fmt(935.0 + 0.2 * arfcn)} MHz"
        in 975..1023 -> "E-900 · ${fmt(935.0 + 0.2 * (arfcn - 1024))} MHz"
        in 512..885 -> "1800 · ${fmt(1805.2 + 0.2 * (arfcn - 512))} MHz"
        in 128..251 -> "850 · ${fmt(869.2 + 0.2 * (arfcn - 128))} MHz"
        else -> ""
    }

    private fun fmt(d: Double) = String.format(Locale.US, "%.1f", d)
}
