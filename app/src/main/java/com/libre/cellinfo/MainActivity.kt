package com.libre.cellinfo

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var tm: TelephonyManager
    private lateinit var header: TextView
    private lateinit var graph: SignalGraphView
    private lateinit var serving: TextView
    private lateinit var neighbors: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var asked = false

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            if (running) handler.postDelayed(this, INTERVAL_MS)
        }
    }

    // ---------------------------------------------------------------- ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tm = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (hasPermissions()) start()
        else if (!asked) { asked = true; askPermissions() }
        else showPermissionHelp()
    }

    override fun onPause() {
        super.onPause()
        running = false
        handler.removeCallbacks(tick)
    }

    private fun start() {
        if (running) return
        running = true
        handler.post(tick)
    }

    // ---------------------------------------------------------------- permisos

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return PERMS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(PERMS, REQ_PERMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) start() else showPermissionHelp()
    }

    private fun showPermissionHelp() {
        header.text = "Faltan permisos (toca aquí para concederlos).\n\n" +
            "Android exige el permiso de Ubicación para leer los datos de las antenas, " +
            "y el de Teléfono para saber el tipo de red. Esta app no tiene permiso de " +
            "Internet: ningún dato sale del móvil."
    }

    // ---------------------------------------------------------------- interfaz

    private fun buildUi() {
        val bg = Color.parseColor("#101418")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(32))
            setBackgroundColor(bg)
        }
        header = text(16f, Color.WHITE, bold = true)
        graph = SignalGraphView(this)
        serving = text(14f, Color.parseColor("#E0E0E0"), mono = true)
        neighbors = text(12.5f, Color.parseColor("#B0BEC5"), mono = true)

        root.addView(header, lp())
        root.addView(graph, LinearLayout.LayoutParams(MATCH, dp(180)).apply {
            topMargin = dp(10); bottomMargin = dp(6)
        })
        root.addView(section("CELDA PRINCIPAL"), lp())
        root.addView(serving, lp())
        root.addView(section("CELDAS VECINAS"), lp())
        root.addView(neighbors, lp())

        header.setOnClickListener { if (!hasPermissions()) askPermissions() }
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root) })
    }

    private fun text(size: Float, color: Int, bold: Boolean = false, mono: Boolean = false) =
        TextView(this).apply {
            textSize = size
            setTextColor(color)
            typeface = when {
                mono -> Typeface.MONOSPACE
                bold -> Typeface.DEFAULT_BOLD
                else -> Typeface.DEFAULT
            }
            if (mono) setTextIsSelectable(true)
        }

    private fun section(title: String) = text(13f, Color.parseColor("#4FC3F7"), bold = true).apply {
        text = title
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun lp() = LinearLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- lectura de datos

    @SuppressLint("MissingPermission")
    private fun refresh() {
        if (!hasPermissions()) return
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                tm.requestCellInfoUpdate(mainExecutor, object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) = render(cellInfo)
                    override fun onError(errorCode: Int, detail: Throwable?) {
                        try { render(tm.allCellInfo ?: emptyList()) } catch (_: SecurityException) {}
                    }
                })
            } else {
                render(tm.allCellInfo ?: emptyList())
            }
        } catch (e: SecurityException) {
            showPermissionHelp()
        }
    }

    private fun render(cells: List<CellInfo>) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        header.text = buildString {
            append(tm.networkOperatorName.orEmpty().ifBlank { "Sin operador" })
            append("  ·  ").append(networkTypeName())
            append("\nSIM: ").append(tm.simOperatorName.orEmpty().ifBlank { "—" })
            append("   Celdas: ").append(cells.size)
            append("   ").append(time)
        }

        val reg = cells.filter { it.isRegistered }
        val other = cells.filterNot { it.isRegistered }

        serving.text = if (reg.isEmpty())
            "No hay celda registrada.\nComprueba que la Ubicación del sistema esté " +
                "activada y que haya una SIM con cobertura."
        else reg.joinToString("\n") { describe(it) }

        graph.add(reg.firstOrNull()?.let { mainSignal(it) })

        val nsa = Build.VERSION.SDK_INT >= 29 &&
            reg.any { it is CellInfoLte } && other.any { isNr(it) }
        neighbors.text = buildString {
            if (nsa) append("ℹ Se ven celdas 5G NR junto al LTE registrado (posible 5G NSA).\n\n")
            if (other.isEmpty()) append("El módem no reporta celdas vecinas.")
            else other.forEach { append(shortLine(it)).append('\n') }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun networkTypeName(): String = try {
        val t = if (Build.VERSION.SDK_INT >= 24) tm.dataNetworkType else tm.networkType
        when (t) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
            TelephonyManager.NETWORK_TYPE_GSM -> "GSM (2G)"
            TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT -> "CDMA (2G)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPA+ (3G)"
            TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD -> "EV-DO (3G)"
            TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD-SCDMA (3G)"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
            TelephonyManager.NETWORK_TYPE_NR -> "NR (5G SA)"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN (Wi-Fi)"
            else -> "Red desconocida"
        }
    } catch (e: SecurityException) { "—" }

    // ---------------------------------------------------------------- formato

    private fun valid(x: Int) = x != Int.MAX_VALUE && x != Int.MIN_VALUE
    private fun na(x: Int, suffix: String = "") = if (valid(x)) "$x$suffix" else "—"

    private fun quality(rsrp: Int): String = if (!valid(rsrp)) "" else when {
        rsrp >= -80 -> " (excelente)"
        rsrp >= -90 -> " (buena)"
        rsrp >= -100 -> " (regular)"
        rsrp >= -110 -> " (débil)"
        else -> " (muy débil)"
    }

    private fun StringBuilder.kv(k: String, v: String) {
        append(k.padEnd(9)).append(v).append('\n')
    }

    private fun describe(c: CellInfo): String = when (c) {
        is CellInfoLte -> lte(c)
        is CellInfoWcdma -> wcdma(c)
        is CellInfoGsm -> gsm(c)
        is CellInfoCdma -> cdma(c)
        else -> if (Build.VERSION.SDK_INT >= 29) describeQ(c) else "Tecnología no soportada\n"
    }

    @Suppress("DEPRECATION")
    private fun lte(c: CellInfoLte): String {
        val id = c.cellIdentity
        val s = c.cellSignalStrength
        val sdk = Build.VERSION.SDK_INT
        return buildString {
            append("▶ LTE (4G)\n")
            kv("MCC-MNC", if (sdk >= 28) "${id.mccString ?: "—"}-${id.mncString ?: "—"}"
                          else "${na(id.mcc)}-${na(id.mnc)}")
            kv("TAC", na(id.tac))
            val ci = id.ci
            if (valid(ci)) {
                kv("CI", "$ci")
                kv("eNB", "${ci shr 8}  (sector ${ci and 0xFF})")
            }
            kv("PCI", na(id.pci))
            if (sdk >= 24 && valid(id.earfcn)) kv("EARFCN", "${id.earfcn}  ${Bands.lte(id.earfcn)}")
            if (sdk >= 30 && id.bands.isNotEmpty()) kv("Bandas", id.bands.joinToString { "B$it" })
            if (sdk >= 28 && valid(id.bandwidth)) kv("Ancho", "${id.bandwidth / 1000} MHz")
            append("— señal —\n")
            if (sdk >= 26) {
                kv("RSRP", na(s.rsrp, " dBm") + quality(s.rsrp))
                kv("RSRQ", na(s.rsrq, " dB"))
                kv("SINR", na(s.rssnr, " dB"))
                kv("CQI", na(s.cqi))
            } else {
                kv("Señal", na(s.dbm, " dBm"))
            }
            if (sdk >= 29) kv("RSSI", na(s.rssi, " dBm"))
            val ta = s.timingAdvance
            if (valid(ta)) kv("TA", "$ta  (~${ta * 78} m)")
            kv("Nivel", "${s.level}/4  (ASU ${s.asuLevel})")
        }
    }

    @Suppress("DEPRECATION")
    private fun wcdma(c: CellInfoWcdma): String {
        val id = c.cellIdentity
        val s = c.cellSignalStrength
        val sdk = Build.VERSION.SDK_INT
        return buildString {
            append("▶ WCDMA / UMTS (3G)\n")
            kv("MCC-MNC", if (sdk >= 28) "${id.mccString ?: "—"}-${id.mncString ?: "—"}"
                          else "${na(id.mcc)}-${na(id.mnc)}")
            kv("LAC", na(id.lac))
            val cid = id.cid
            if (valid(cid)) {
                kv("CID", "$cid")
                kv("RNC", "${cid shr 16}  (celda ${cid and 0xFFFF})")
            }
            kv("PSC", na(id.psc))
            if (sdk >= 24 && valid(id.uarfcn)) kv("UARFCN", "${id.uarfcn}  ${Bands.umts(id.uarfcn)}")
            append("— señal —\n")
            kv("RSCP", na(s.dbm, " dBm"))
            if (sdk >= 30) kv("Ec/No", na(s.ecNo, " dB"))
            kv("Nivel", "${s.level}/4  (ASU ${s.asuLevel})")
        }
    }

    @Suppress("DEPRECATION")
    private fun gsm(c: CellInfoGsm): String {
        val id = c.cellIdentity
        val s = c.cellSignalStrength
        val sdk = Build.VERSION.SDK_INT
        return buildString {
            append("▶ GSM (2G)\n")
            kv("MCC-MNC", if (sdk >= 28) "${id.mccString ?: "—"}-${id.mncString ?: "—"}"
                          else "${na(id.mcc)}-${na(id.mnc)}")
            kv("LAC", na(id.lac))
            kv("CID", na(id.cid))
            if (sdk >= 24) {
                if (valid(id.arfcn)) kv("ARFCN", "${id.arfcn}  ${Bands.gsm(id.arfcn)}")
                kv("BSIC", na(id.bsic))
            }
            append("— señal —\n")
            kv("RSSI", na(s.dbm, " dBm"))
            if (sdk >= 26) kv("TA", na(s.timingAdvance))
            kv("Nivel", "${s.level}/4  (ASU ${s.asuLevel})")
        }
    }

    private fun cdma(c: CellInfoCdma): String {
        val id = c.cellIdentity
        val s = c.cellSignalStrength
        return buildString {
            append("▶ CDMA\n")
            kv("SID", na(id.systemId))
            kv("NID", na(id.networkId))
            kv("BID", na(id.basestationId))
            append("— señal —\n")
            kv("CDMA", na(s.cdmaDbm, " dBm"))
            kv("Ec/Io", na(s.cdmaEcio))
            kv("EVDO", na(s.evdoDbm, " dBm"))
            kv("Nivel", "${s.level}/4")
        }
    }

    @TargetApi(29)
    private fun describeQ(c: CellInfo): String = when (c) {
        is CellInfoNr -> nr(c)
        is CellInfoTdscdma -> buildString {
            val id = c.cellIdentity
            val s = c.cellSignalStrength
            append("▶ TD-SCDMA (3G)\n")
            kv("LAC", na(id.lac))
            kv("CID", na(id.cid))
            kv("UARFCN", na(id.uarfcn))
            append("— señal —\n")
            kv("RSCP", na(s.rscp, " dBm"))
        }
        else -> "Tecnología desconocida\n"
    }

    @TargetApi(29)
    private fun nr(c: CellInfoNr): String {
        val id = c.cellIdentity as CellIdentityNr
        val s = c.cellSignalStrength as CellSignalStrengthNr
        return buildString {
            append("▶ NR (5G)\n")
            kv("MCC-MNC", "${id.mccString ?: "—"}-${id.mncString ?: "—"}")
            kv("TAC", na(id.tac))
            val nci = id.nci
            if (nci in 0 until Long.MAX_VALUE) kv("NCI", "$nci")
            kv("PCI", na(id.pci))
            if (valid(id.nrarfcn)) kv("ARFCN", "${id.nrarfcn}  ${Bands.nr(id.nrarfcn)}")
            if (Build.VERSION.SDK_INT >= 30 && id.bands.isNotEmpty())
                kv("Bandas", id.bands.joinToString { "n$it" })
            append("— señal —\n")
            kv("SS-RSRP", na(s.ssRsrp, " dBm") + quality(s.ssRsrp))
            kv("SS-RSRQ", na(s.ssRsrq, " dB"))
            kv("SS-SINR", na(s.ssSinr, " dB"))
            kv("CSI-RSRP", na(s.csiRsrp, " dBm"))
            kv("CSI-SINR", na(s.csiSinr, " dB"))
            kv("Nivel", "${s.level}/4")
        }
    }

    @Suppress("DEPRECATION")
    private fun shortLine(c: CellInfo): String {
        val sdk = Build.VERSION.SDK_INT
        return when (c) {
            is CellInfoLte -> {
                val id = c.cellIdentity
                val s = c.cellSignalStrength
                val ch = if (sdk >= 24 && valid(id.earfcn))
                    " ch ${id.earfcn} ${Bands.lteShort(id.earfcn)}" else ""
                val sig = if (sdk >= 26) "RSRP ${na(s.rsrp)} RSRQ ${na(s.rsrq)}" else "${na(s.dbm)} dBm"
                "4G PCI ${na(id.pci)}$ch  $sig"
            }
            is CellInfoWcdma -> {
                val id = c.cellIdentity
                val ch = if (sdk >= 24 && valid(id.uarfcn)) " ch ${id.uarfcn}" else ""
                "3G PSC ${na(id.psc)}$ch  RSCP ${na(c.cellSignalStrength.dbm)}"
            }
            is CellInfoGsm -> {
                val id = c.cellIdentity
                val ch = if (sdk >= 24 && valid(id.arfcn)) " ARFCN ${id.arfcn} BSIC ${na(id.bsic)}" else ""
                "2G$ch  ${na(c.cellSignalStrength.dbm)} dBm"
            }
            is CellInfoCdma -> "CDMA BID ${na(c.cellIdentity.basestationId)}  ${na(c.cellSignalStrength.cdmaDbm)} dBm"
            else -> if (sdk >= 29) shortQ(c) else "Otra tecnología"
        }
    }

    @TargetApi(29)
    private fun shortQ(c: CellInfo): String = when (c) {
        is CellInfoNr -> {
            val id = c.cellIdentity as CellIdentityNr
            val s = c.cellSignalStrength as CellSignalStrengthNr
            val ch = if (valid(id.nrarfcn)) " ch ${id.nrarfcn} ${Bands.nrShort(id.nrarfcn)}" else ""
            "5G PCI ${na(id.pci)}$ch  RSRP ${na(s.ssRsrp)} SINR ${na(s.ssSinr)}"
        }
        is CellInfoTdscdma -> "TD-SCDMA CID ${na(c.cellIdentity.cid)}  ${na(c.cellSignalStrength.rscp)} dBm"
        else -> "Otra tecnología"
    }

    @TargetApi(29)
    private fun isNr(c: CellInfo) = c is CellInfoNr

    /** Valor que se dibuja en la gráfica (RSRP en 4G/5G, dBm en el resto). */
    @Suppress("DEPRECATION")
    private fun mainSignal(c: CellInfo): Int? {
        val v = when (c) {
            is CellInfoLte -> if (Build.VERSION.SDK_INT >= 26) c.cellSignalStrength.rsrp
                              else c.cellSignalStrength.dbm
            is CellInfoWcdma -> c.cellSignalStrength.dbm
            is CellInfoGsm -> c.cellSignalStrength.dbm
            is CellInfoCdma -> c.cellSignalStrength.cdmaDbm
            else -> if (Build.VERSION.SDK_INT >= 29) signalQ(c) else Int.MAX_VALUE
        }
        return if (valid(v)) v else null
    }

    @TargetApi(29)
    private fun signalQ(c: CellInfo): Int = when (c) {
        is CellInfoNr -> (c.cellSignalStrength as CellSignalStrengthNr).ssRsrp
        is CellInfoTdscdma -> c.cellSignalStrength.rscp
        else -> Int.MAX_VALUE
    }

    companion object {
        private const val INTERVAL_MS = 2000L
        private const val REQ_PERMS = 1
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private val PERMS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
    }
}
