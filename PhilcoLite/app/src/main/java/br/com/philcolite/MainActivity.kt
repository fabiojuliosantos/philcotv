package br.com.philcolite

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Philco Lite v0.1: HOME local, sem acesso a rede, anúncios, serviços ou WebView.
 * Views nativas para reduzir alocacoes e dependencias em TVs com ~1 GB de RAM.
 */
class MainActivity : Activity() {

    private data class TvApp(
        val packageName: String,
        val label: String,
        val component: ComponentName,
        val tvIntent: Boolean
    )

    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val background = Color.rgb(17, 24, 39)
    private val surface = Color.rgb(30, 41, 59)
    private val textPrimary = Color.rgb(248, 250, 252)
    private val textMuted = Color.rgb(148, 163, 184)
    private val accent = Color.rgb(167, 243, 208)

    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var status: TextView
    private lateinit var appGrid: LinearLayout

    private val prefs by lazy { getSharedPreferences("philco_lite", MODE_PRIVATE) }
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateClock()
    }
    private var clockReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.navigationBarColor = background
        buildUi()
        updateClock()
        showApps()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (!clockReceiverRegistered) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(clockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(clockReceiver, filter)
            }
            clockReceiverRegistered = true
        }
        updateClock()
    }

    override fun onStop() {
        if (clockReceiverRegistered) {
            unregisterReceiver(clockReceiver)
            clockReceiverRegistered = false
        }
        super.onStop()
    }

    private fun updateClock() {
        if (!::clock.isInitialized) return
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", ptBr).format(now)
        date.text = SimpleDateFormat("EEEE, d 'de' MMMM", ptBr)
            .format(now)
            .replaceFirstChar { it.titlecase(ptBr) }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
            setPadding(dp(46), dp(26), dp(46), dp(16))
            clipChildren = false
            clipToPadding = false
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        clock = TextView(this).apply {
            textSize = 35f
            setTextColor(textPrimary)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }
        date = TextView(this).apply {
            textSize = 13f
            setTextColor(textMuted)
        }
        titleBlock.addView(clock)
        titleBlock.addView(date)
        toolbar.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val projectivy = actionButton("Projectivy") { openProjectivy() }
        val settings = actionButton("Configurações") { openSettings() }
        val refresh = actionButton("Atualizar") { showApps() }
        toolbar.addView(projectivy)
        toolbar.addView(settings)
        toolbar.addView(refresh)
        root.addView(toolbar)

        val caption = TextView(this).apply {
            text = "SEUS APLICATIVOS"
            textSize = 13f
            setTextColor(accent)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.10f
        }
        root.addView(caption, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(29); bottomMargin = dp(14) })

        appGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(appGrid)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        status = TextView(this).apply {
            text = "OK: abrir  •  Segure OK: favoritar  •  Projectivy: voltar ao launcher reserva"
            textSize = 11f
            setTextColor(textMuted)
            setPadding(0, dp(10), 0, 0)
        }
        root.addView(status)
        setContentView(root)
    }

    private fun showApps() {
        val all = discoverApps()
        val favorites = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        val priority = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.spotify.tv.android",
            "com.disney.disneyplus",
            "com.globo.globotv",
            "com.crunchyroll.crunchyroid",
            "com.valvesoftware.steamlink",
            "org.videolan.vlc",
            "com.nathnetwork.xciptv"
        )
        val sorted = all.sortedWith(
            compareBy<TvApp> { if (it.packageName in favorites) 0 else 1 }
                .thenBy { priority.indexOf(it.packageName).let { index -> if (index < 0) 999 else index } }
                .thenBy { it.label.lowercase(ptBr) }
        )
        appGrid.removeAllViews()
        if (sorted.isEmpty()) {
            appGrid.addView(TextView(this).apply {
                text = "Nenhum aplicativo de TV encontrado. Use Configurações ou Projectivy para recuperar o acesso."
                textSize = 17f
                setTextColor(textPrimary)
            })
            return
        }

        // Cinco cards por linha: poucos elementos visuais e navegacao nativa por D-pad.
        var firstCard: View? = null
        sorted.chunked(5).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(dp(5), dp(7), dp(5), dp(10))
            }
            group.forEach { app ->
                val card = appCard(app, app.packageName in favorites)
                if (firstCard == null) firstCard = card
                row.addView(card, LinearLayout.LayoutParams(0, dp(118), 1f).apply {
                    leftMargin = dp(4)
                    rightMargin = dp(4)
                })
            }
            repeat(5 - group.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            appGrid.addView(row)
        }
        appGrid.post { firstCard?.requestFocus() }
    }

    private fun discoverApps(): List<TvApp> {
        val found = LinkedHashMap<String, TvApp>()
        val pm = packageManager
        listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { category ->
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            val matching: List<ResolveInfo> = pm.queryIntentActivities(query, 0)
            matching.forEach appLoop@{ info ->
                val activity = info.activityInfo ?: return@appLoop
                if (activity.packageName == packageName || !activity.enabled) return@appLoop
                if (found.containsKey(activity.packageName)) return@appLoop
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                found[activity.packageName] = TvApp(
                    packageName = activity.packageName,
                    label = label.ifBlank { activity.packageName.substringAfterLast('.') },
                    component = ComponentName(activity.packageName, activity.name),
                    tvIntent = category == Intent.CATEGORY_LEANBACK_LAUNCHER
                )
            }
        }
        return found.values.toList()
    }

    private fun appCard(app: TvApp, favorite: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            isLongClickable = true
            setPadding(dp(9), dp(9), dp(9), dp(7))
            background = rounded(surface, false)
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) Color.rgb(38, 57, 75) else surface, focused)
                view.elevation = if (focused) dp(8).toFloat() else 0f
                view.animate().scaleX(if (focused) 1.035f else 1f)
                    .scaleY(if (focused) 1.035f else 1f).setDuration(90).start()
            }
            setOnClickListener { launchApp(app) }
            setOnLongClickListener {
                toggleFavorite(app.packageName)
                true
            }
        }
        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                setImageDrawable(packageManager.getActivityIcon(app.component))
            } catch (_: Exception) {
                setImageResource(R.drawable.ic_philco_lite)
            }
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(53), dp(53)))
        val text = TextView(this).apply {
            text = if (favorite) "★ ${app.label}" else app.label
            setTextColor(textPrimary)
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        card.addView(text, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(36)
        ).apply { topMargin = dp(5) })
        return card
    }

    private fun toggleFavorite(pkg: String) {
        val items = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        val nowFavorite = if (pkg in items) { items.remove(pkg); false } else { items.add(pkg); true }
        prefs.edit().putStringSet("favorites", items).apply()
        Toast.makeText(this, if (nowFavorite) "Favorito adicionado" else "Favorito removido", Toast.LENGTH_SHORT).show()
        showApps()
    }

    private fun launchApp(app: TvApp) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(if (app.tvIntent) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
                component = app.component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir ${app.label}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openProjectivy() {
        val pkg = "com.spocky.projengmenu"
        try {
            val launch = packageManager.getLeanbackLaunchIntentForPackage(pkg)
                ?: packageManager.getLaunchIntentForPackage(pkg)
                ?: Intent(Intent.ACTION_MAIN).setComponent(
                    ComponentName(pkg, "com.spocky.projengmenu.ui.home.MainActivity")
                )
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        } catch (_: Exception) {
            Toast.makeText(this, "Projectivy indisponível. Use ADB para recuperação.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, "Configurações indisponíveis; acesse pelo Projectivy.", Toast.LENGTH_LONG).show()
        }
    }

    private fun actionButton(label: String, onClick: () -> Unit): View {
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(textPrimary)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isFocusable = true
            isClickable = true
            background = rounded(surface, false)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { view, focused ->
                view.background = rounded(if (focused) Color.rgb(38, 57, 75) else surface, focused)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(43)
            ).apply { leftMargin = dp(9) }
        }
    }

    private fun rounded(fill: Int, focused: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(13).toFloat()
        setStroke(dp(if (focused) 2 else 1), if (focused) accent else Color.rgb(45, 55, 72))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}
