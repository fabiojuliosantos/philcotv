package br.com.philcolite

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Philco Lite v0.2: tema 04 / paisagem noturna.
 * Interface 100% nativa (Views); wallpaper JPG local em RGB_565;
 * sem servicos, rede, video, WebView ou bibliotecas extras.
 */
class MainActivity : Activity() {

    private enum class Section { HOME, APPS, FAVORITES }

    private data class TvApp(
        val packageName: String,
        val label: String,
        val component: ComponentName,
        val tvIntent: Boolean
    )

    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val dark = Color.rgb(7, 12, 28)
    private val textPrimary = Color.rgb(248, 249, 255)
    private val textMuted = Color.rgb(196, 199, 220)
    private val accent = Color.rgb(186, 185, 255)
    private val cardColor = Color.argb(210, 11, 19, 37)

    private val prefs by lazy { getSharedPreferences("philco_lite", MODE_PRIVATE) }
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var sectionHint: TextView
    private lateinit var appGrid: LinearLayout
    private lateinit var appScroll: ScrollView
    private lateinit var status: TextView
    private val navButtons = LinkedHashMap<Section, TextView>()
    private var currentSection = Section.HOME
    private var clockReceiverRegistered = false

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateClock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.navigationBarColor = dark
        buildUi()
        updateClock()
        showSection(Section.HOME, focusCard = true)
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showSection(currentSection, focusCard = true)
            Toast.makeText(this, "Lista de apps atualizada", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updateClock() {
        if (!::clock.isInitialized) return
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", ptBr).format(now)
        date.text = SimpleDateFormat("EEE, d 'de' MMM", ptBr)
            .format(now).replaceFirstChar { it.titlecase(ptBr) }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(dark) }

        // JPEG local de 1280x720, sem escala de densidade (drawable-nodpi).
        // RGB_565 reduz a RAM do bitmap para ~1,8 MiB.
        val backgroundOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = true
        }
        val wallpaperBitmap = BitmapFactory.decodeResource(
            resources, R.drawable.wallpaper_night, backgroundOptions
        )
        if (wallpaperBitmap != null) {
            val wallpaper = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(BitmapDrawable(resources, wallpaperBitmap))
            }
            root.addView(wallpaper, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        // Vinheta escura na lateral e na base: leitura confortavel sem blur/GPU extra.
        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                Color.argb(234, 3, 9, 23), Color.argb(112, 4, 10, 25), Color.argb(15, 4, 10, 25)
            ))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(
                Color.argb(244, 5, 10, 24), Color.argb(167, 6, 11, 26), Color.TRANSPARENT
            ))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(325), Gravity.BOTTOM))

        val columns = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        root.addView(columns, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(27), dp(34), dp(9), dp(22))
            clipChildren = false
            clipToPadding = false
        }
        columns.addView(sidebar, LinearLayout.LayoutParams(dp(222), ViewGroup.LayoutParams.MATCH_PARENT))

        val brand = TextView(this).apply {
            text = "Philco Lite"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textPrimary)
            includeFontPadding = false
        }
        sidebar.addView(brand)
        sidebar.addView(TextView(this).apply {
            text = "v0.2  •  Modo leve"
            textSize = 11f
            setTextColor(accent)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(34); topMargin = dp(5) })

        addNavigation(sidebar, Section.HOME, "⌂", "Início")
        addNavigation(sidebar, Section.APPS, "▦", "Aplicativos")
        addNavigation(sidebar, Section.FAVORITES, "★", "Favoritos")
        sidebar.addView(View(this), LinearLayout.LayoutParams(1, dp(12)))
        addAction(sidebar, "⚙", "Configurações") { openSettings() }
        addAction(sidebar, "↩", "Projectivy") { openProjectivy() }
        sidebar.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        sidebar.addView(TextView(this).apply {
            text = "Philco Lite • sem anúncios"
            textSize = 10f
            setTextColor(textMuted)
        })

        val main = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(43), dp(25))
            clipChildren = false
            clipToPadding = false
        }
        columns.addView(main, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "SEU ESPAÇO. SEU RITMO."
            textSize = 11f
            letterSpacing = .16f
            setTextColor(textMuted)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val clockBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clock = TextView(this).apply {
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textPrimary)
            includeFontPadding = false
        }
        date = TextView(this).apply {
            textSize = 12f
            setTextColor(textMuted)
        }
        clockBlock.addView(clock)
        clockBlock.addView(date)
        header.addView(clockBlock)
        main.addView(header)

        // Area livre para a paisagem; os apps ficam na faixa inferior.
        main.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))

        sectionTitle = TextView(this).apply {
            textSize = 22f
            setTextColor(textPrimary)
            setTypeface(null, Typeface.BOLD)
        }
        main.addView(sectionTitle)
        sectionHint = TextView(this).apply {
            textSize = 12f
            setTextColor(textMuted)
        }
        main.addView(sectionHint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(15) })

        appGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        appScroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(appGrid)
        }
        main.addView(appScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(137)
        ))

        status = TextView(this).apply {
            text = "OK: abrir   •   Segure OK: favoritar   •   MENU: atualizar apps"
            setTextColor(textMuted)
            textSize = 10f
            setPadding(0, dp(12), 0, 0)
        }
        main.addView(status)
        setContentView(root)
    }

    private fun addNavigation(parent: LinearLayout, section: Section, icon: String, label: String) {
        val item = sidebarButton(icon, label)
        navButtons[section] = item
        item.setOnClickListener { showSection(section, focusCard = true) }
        item.setOnFocusChangeListener { _, _ -> updateNavStyles() }
        parent.addView(item)
    }

    private fun addAction(parent: LinearLayout, icon: String, label: String, action: () -> Unit) {
        val item = sidebarButton(icon, label)
        item.setOnClickListener { action() }
        item.setOnFocusChangeListener { view, focused ->
            view.background = navBackground(selected = false, focused = focused)
        }
        parent.addView(item)
    }

    private fun sidebarButton(icon: String, label: String): TextView = TextView(this).apply {
        text = "$icon   $label"
        textSize = 15f
        setTextColor(textPrimary)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), 0, dp(8), 0)
        isFocusable = true
        isClickable = true
        background = navBackground(selected = false, focused = false)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(51)
        ).apply { bottomMargin = dp(9) }
    }

    private fun updateNavStyles() {
        navButtons.forEach { (section, button) ->
            button.background = navBackground(section == currentSection, button.isFocused)
        }
    }

    private fun showSection(section: Section, focusCard: Boolean, focusPackage: String? = null) {
        currentSection = section
        updateNavStyles()
        val favorites = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        val all = sortedApps(discoverApps(), favorites)
        val items = when (section) {
            Section.HOME -> {
                val favoriteApps = all.filter { it.packageName in favorites }
                if (favoriteApps.isEmpty()) all.take(5) else favoriteApps.take(5)
            }
            Section.APPS -> all
            Section.FAVORITES -> all.filter { it.packageName in favorites }
        }

        sectionTitle.text = when (section) {
            Section.HOME -> if (favorites.isEmpty()) "Acesso rápido" else "Seus favoritos"
            Section.APPS -> "Todos os aplicativos"
            Section.FAVORITES -> "Meus favoritos"
        }
        sectionHint.text = when (section) {
            Section.HOME -> "Uma TV mais simples, do seu jeito"
            Section.APPS -> "Escolha um app ou segure OK para favoritar"
            Section.FAVORITES -> "Segure OK para remover dos favoritos"
        }
        val params = appScroll.layoutParams as LinearLayout.LayoutParams
        params.height = dp(if (section == Section.HOME) 137 else 352)
        appScroll.layoutParams = params
        appScroll.scrollTo(0, 0)
        appGrid.removeAllViews()

        if (items.isEmpty()) {
            val emptyMessage = if (section == Section.FAVORITES)
                "Nenhum favorito ainda. Abra Aplicativos e segure OK em um app."
            else "Nenhum aplicativo encontrado. Use Projectivy ou Configurações."
            appGrid.addView(TextView(this).apply {
                text = emptyMessage
                textSize = 15f
                setTextColor(textPrimary)
                setPadding(dp(14), dp(20), dp(10), dp(20))
            })
            if (focusCard) navButtons[section]?.requestFocus()
            return
        }

        var firstCard: View? = null
        var requestedCard: View? = null
        items.chunked(5).forEach { group ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                clipChildren = false
                clipToPadding = false
                setPadding(dp(4), dp(6), dp(4), dp(8))
            }
            group.forEach { app ->
                val card = appCard(app, app.packageName in favorites)
                if (firstCard == null) firstCard = card
                if (app.packageName == focusPackage) requestedCard = card
                row.addView(card, LinearLayout.LayoutParams(0, dp(121), 1f).apply {
                    leftMargin = dp(5)
                    rightMargin = dp(5)
                })
            }
            repeat(5 - group.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            appGrid.addView(row)
        }
        val focusTarget = requestedCard ?: firstCard
        if (focusCard) appGrid.post { focusTarget?.requestFocus() }
        navButtons[section]?.nextFocusRightId = focusTarget?.id ?: View.NO_ID
    }

    private fun sortedApps(apps: List<TvApp>, favorites: Set<String>): List<TvApp> {
        val priority = listOf(
            "com.google.android.youtube.tv", "com.netflix.ninja",
            "com.spotify.tv.android", "com.disney.disneyplus",
            "com.globo.globotv", "com.crunchyroll.crunchyroid",
            "com.valvesoftware.steamlink", "org.videolan.vlc"
        )
        return apps.sortedWith(
            compareBy<TvApp> { if (it.packageName in favorites) 0 else 1 }
                .thenBy { priority.indexOf(it.packageName).let { index -> if (index < 0) 999 else index } }
                .thenBy { it.label.lowercase(ptBr) }
        )
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
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            isLongClickable = true
            setPadding(dp(8), dp(6), dp(8), dp(5))
            background = cardBackground(false)
            setOnFocusChangeListener { view, focused ->
                view.background = cardBackground(focused)
                view.elevation = if (focused) dp(8).toFloat() else 0f
                view.animate().cancel()
                view.animate().scaleX(if (focused) 1.045f else 1f)
                    .scaleY(if (focused) 1.045f else 1f).setDuration(100).start()
            }
            setOnClickListener { launchApp(app) }
            setOnLongClickListener {
                toggleFavorite(app.packageName)
                true
            }
        }
        val appImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            try {
                val pm = packageManager
                setImageDrawable(
                    pm.getActivityBanner(app.component)
                        ?: pm.getApplicationBanner(app.packageName)
                        ?: pm.getActivityIcon(app.component)
                )
            } catch (_: Exception) {
                try {
                    setImageDrawable(packageManager.getActivityIcon(app.component))
                } catch (_: Exception) {
                    setImageResource(R.drawable.ic_philco_lite)
                }
            }
        }
        card.addView(appImage, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(65)
        ))
        card.addView(TextView(this).apply {
            text = if (favorite) "★ ${app.label}" else app.label
            textSize = 12f
            setTextColor(textPrimary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))
        return card
    }

    private fun toggleFavorite(pkg: String) {
        val items = prefs.getStringSet("favorites", emptySet())?.toMutableSet() ?: mutableSetOf()
        val isFavorite = if (pkg in items) { items.remove(pkg); false } else { items.add(pkg); true }
        prefs.edit().putStringSet("favorites", items).apply()
        Toast.makeText(this, if (isFavorite) "Adicionado aos favoritos" else "Removido dos favoritos", Toast.LENGTH_SHORT).show()
        showSection(currentSection, focusCard = true, focusPackage = pkg)
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
            Toast.makeText(this, "Configurações indisponíveis; abra pelo Projectivy.", Toast.LENGTH_LONG).show()
        }
    }

    private fun cardBackground(focused: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(if (focused) Color.argb(246, 21, 33, 63) else cardColor)
        cornerRadius = dp(12).toFloat()
        setStroke(dp(if (focused) 3 else 1), if (focused) Color.WHITE else Color.argb(105, 115, 139, 197))
    }

    private fun navBackground(selected: Boolean, focused: Boolean): GradientDrawable = GradientDrawable().apply {
        setColor(when {
            focused -> Color.argb(235, 36, 64, 107)
            selected -> Color.argb(205, 32, 64, 111)
            else -> Color.TRANSPARENT
        })
        cornerRadius = dp(10).toFloat()
        setStroke(dp(if (focused) 2 else 0), if (focused) accent else Color.TRANSPARENT)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}