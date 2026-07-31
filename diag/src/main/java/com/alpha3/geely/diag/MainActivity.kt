package com.alpha3.geely.diag

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App descartavel. Unico objetivo: descobrir em que ambiente a multimidia roda,
 * porque nao ha documentacao publica confiavel do Flyme Auto E1.8.0.
 *
 * Cada sonda e isolada em runCatching: um probe que exploda nao pode derrubar o
 * relatorio inteiro, senao perdemos a viagem ate o carro.
 */
class MainActivity : Activity() {

    private lateinit var output: TextView
    private var report: String = ""

    // Hosts que a loja vai realmente precisar alcancar. Vale testar os quatro
    // separadamente: firmware de mercado chines as vezes resolve github.com mas
    // nao o CDN que serve os assets de release.
    private val hosts = listOf(
        "https://raw.githubusercontent.com/" to "catalogo (index.json)",
        "https://api.github.com/" to "API do GitHub",
        "https://github.com/" to "entrada do download",
        "https://objects.githubusercontent.com/" to "CDN dos assets de release"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val title = TextView(this).apply {
            text = "Diagnostico da multimidia"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 16)
        }
        root.addView(title)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        buttons.addView(bigButton("Testar rede") { runNetworkTests() })
        buttons.addView(bigButton("Salvar TXT") { saveReport() })
        buttons.addView(bigButton("Compartilhar") { shareReport() })
        root.addView(buttons)

        output = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(0, 16, 0, 0)
        }
        val scroll = ScrollView(this).apply {
            addView(output)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(scroll)

        setContentView(root)

        report = buildReport()
        output.text = report
        runNetworkTests()
    }

    private fun bigButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            minHeight = 120 // alvo de toque generoso: isso vai ser usado dirigindo parado, na tela do carro
            setPadding(24, 16, 24, 16)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }

    private fun buildReport(): String = buildString {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("=== DIAGNOSTICO GEELY / FLYME AUTO ===")
        appendLine("gerado em: $stamp")
        appendLine()

        section("ANDROID")
        probe("SDK_INT (API level)") { Build.VERSION.SDK_INT.toString() }
        probe("Versao Android") { Build.VERSION.RELEASE }
        probe("Security patch") {
            if (Build.VERSION.SDK_INT >= 23) Build.VERSION.SECURITY_PATCH else "n/d"
        }
        probe("Codename") { Build.VERSION.CODENAME }
        appendLine()

        section("HARDWARE / FIRMWARE")
        probe("Fabricante") { Build.MANUFACTURER }
        probe("Marca") { Build.BRAND }
        probe("Modelo") { Build.MODEL }
        probe("Device") { Build.DEVICE }
        probe("Product") { Build.PRODUCT }
        probe("Board") { Build.BOARD }
        probe("Hardware") { Build.HARDWARE }
        probe("Display ID") { Build.DISPLAY }
        probe("Fingerprint") { Build.FINGERPRINT }
        probe("ABIs suportadas") { Build.SUPPORTED_ABIS.joinToString(", ") }
        appendLine()

        section("TELA  <-- define todo o layout da loja")
        probe("Metricas") {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(m)
            "${m.widthPixels} x ${m.heightPixels} px | densityDpi=${m.densityDpi} " +
                "| density=${m.density} | scaledDensity=${m.scaledDensity}"
        }
        probe("Tamanho em dp") {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(m)
            "${(m.widthPixels / m.density).toInt()} x ${(m.heightPixels / m.density).toInt()} dp"
        }
        probe("Orientacao atual") {
            when (resources.configuration.orientation) {
                android.content.res.Configuration.ORIENTATION_LANDSCAPE -> "landscape"
                android.content.res.Configuration.ORIENTATION_PORTRAIT -> "portrait"
                else -> "indefinida"
            }
        }
        probe("Screen layout size") {
            val size = resources.configuration.screenLayout and
                android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
            when (size) {
                android.content.res.Configuration.SCREENLAYOUT_SIZE_SMALL -> "small"
                android.content.res.Configuration.SCREENLAYOUT_SIZE_NORMAL -> "normal"
                android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE -> "large"
                android.content.res.Configuration.SCREENLAYOUT_SIZE_XLARGE -> "xlarge"
                else -> "desconhecido"
            }
        }
        appendLine()

        section("INSTALACAO DE APPS  <-- define se a loja e viavel")
        probe("Pode solicitar instalacao") {
            if (Build.VERSION.SDK_INT >= 26) {
                val can = packageManager.canRequestPackageInstalls()
                if (can) "SIM (permissao ja concedida)"
                else "NAO ainda - precisa habilitar 'fontes desconhecidas' para este app"
            } else "API < 26: controlado pela config global de fontes desconhecidas"
        }
        probe("Fontes desconhecidas (global)") {
            @Suppress("DEPRECATION")
            val v = android.provider.Settings.Secure.getInt(
                contentResolver,
                android.provider.Settings.Secure.INSTALL_NON_MARKET_APPS,
                -1
            )
            when (v) {
                1 -> "habilitado"
                0 -> "desabilitado"
                else -> "n/d nesta versao"
            }
        }
        probe("Tela de instalacao existe") {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(
                android.net.Uri.parse("file:///dummy.apk"),
                "application/vnd.android.package-archive"
            )
            val n = packageManager.queryIntentActivities(i, 0).size
            if (n > 0) "SIM ($n handler(s) para APK)" else "NENHUM handler encontrado"
        }
        probe("Apps visiveis para nos") {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(i, 0)
            "${apps.size} apps com icone no launcher"
        }
        probe("Amostra de apps instalados") {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(i, 0)
                .take(15)
                .joinToString("\n                     ") { it.activityInfo.packageName }
        }
        appendLine()

        section("PLATAFORMA")
        probe("E Android Automotive (AAOS)") {
            val auto = packageManager.hasSystemFeature("android.hardware.type.automotive")
            if (auto) "SIM - regras de distracao se aplicam" else "NAO - Android comum customizado"
        }
        probe("Tem touchscreen") {
            packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN).toString()
        }
        probe("Tem Wi-Fi") {
            packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI).toString()
        }
        probe("Google Play Services") {
            runCatching { packageManager.getPackageInfo("com.google.android.gms", 0) }
                .fold({ "presente (${it.versionName})" }, { "ausente" })
        }
        probe("Play Store") {
            runCatching { packageManager.getPackageInfo("com.android.vending", 0) }
                .fold({ "presente" }, { "ausente" })
        }
        appendLine()

        section("SISTEMA")
        probe("Locale") { Locale.getDefault().toString() }
        probe("Timezone") { java.util.TimeZone.getDefault().id }
        probe("Espaco livre (dados)") {
            val s = StatFs(Environment.getDataDirectory().path)
            val free = s.availableBlocksLong * s.blockSizeLong
            "%.1f GB".format(free / 1024.0 / 1024.0 / 1024.0)
        }
        probe("Pasta de arquivos do app") { getExternalFilesDir(null)?.absolutePath ?: "n/d" }
        appendLine()

        section("REDE")
        appendLine("  (aguardando teste...)")
    }

    /** Executa a sonda; qualquer excecao vira uma linha de erro em vez de um crash. */
    private fun StringBuilder.probe(label: String, block: () -> String) {
        val value = runCatching(block).getOrElse { "ERRO: ${it.javaClass.simpleName}: ${it.message}" }
        appendLine("  ${label.padEnd(28)} : $value")
    }

    private fun StringBuilder.section(name: String) {
        appendLine("--- $name ---")
    }

    private fun runNetworkTests() {
        output.text = report + "\n  testando conectividade...\n"
        Thread {
            val sb = StringBuilder()
            for ((url, description) in hosts) {
                val started = System.currentTimeMillis()
                val result = runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.requestMethod = "GET"
                    conn.instanceFollowRedirects = false
                    val code = conn.responseCode
                    val protocol = runCatching { conn.cipherSuiteOrNull() }.getOrNull()
                    conn.disconnect()
                    val ms = System.currentTimeMillis() - started
                    "HTTP $code em ${ms}ms" + (protocol?.let { " | $it" } ?: "")
                }.getOrElse { "FALHOU: ${it.javaClass.simpleName}: ${it.message}" }
                sb.appendLine("  ${description.padEnd(28)} : $result")
            }
            val networkSection = sb.toString()
            runOnUiThread {
                report = report.replace("  (aguardando teste...)", networkSection.trimEnd())
                output.text = report
            }
        }.start()
    }

    /** Extrai o cipher suite quando a conexao for HTTPS, para detectar problemas de TLS antigo. */
    private fun HttpURLConnection.cipherSuiteOrNull(): String? =
        (this as? javax.net.ssl.HttpsURLConnection)?.cipherSuite

    private fun reportFile(): File = File(getExternalFilesDir(null), "diagnostico-geely.txt")

    private fun saveReport() {
        val targets = mutableListOf<String>()
        runCatching {
            reportFile().writeText(report)
            targets += reportFile().absolutePath
        }.onFailure { targets += "falhou em getExternalFilesDir: ${it.message}" }

        // Tentativa adicional: Downloads publico, mais facil de achar no gerenciador
        // de arquivos da multimidia. Falha silenciosamente em Android 10+ sem permissao.
        runCatching {
            @Suppress("DEPRECATION")
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val f = File(downloads, "diagnostico-geely.txt")
            f.writeText(report)
            targets += f.absolutePath
        }

        Toast.makeText(this, "Salvo em:\n" + targets.joinToString("\n"), Toast.LENGTH_LONG).show()
        output.text = report + "\n\n--- SALVO EM ---\n" + targets.joinToString("\n")
    }

    private fun shareReport() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Diagnostico Geely / Flyme Auto")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Enviar relatorio")) }
            .onFailure { Toast.makeText(this, "Nenhum app de compartilhamento", Toast.LENGTH_LONG).show() }
    }
}
