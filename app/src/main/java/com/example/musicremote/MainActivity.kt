package com.example.musicremote

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.provider.Settings
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var status: TextView
    private lateinit var art: ImageView
    private lateinit var playButton: Button
    private lateinit var permissionButton: Button
    private lateinit var progress: SeekBar
    private lateinit var elapsed: TextView
    private lateinit var durationText: TextView
    private lateinit var clockText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherIcon: TextView
    private lateinit var temperatureText: TextView
    private lateinit var weatherDescription: TextView
    private lateinit var settingsButton: Button

    private var controller: MediaController? = null
    private var locationManager: LocationManager? = null
    private var lastWeatherUpdate = 0L
    private var locationListener: LocationListener? = null
    private var lastKey = ""
    private var pendingCoverKey = ""
    private var coverRetryCount = 0

    private val prefs by lazy {
        getSharedPreferences("music_remote_saved", MODE_PRIVATE)
    }

    private val uiPrefs by lazy {
        getSharedPreferences("music_remote_ui", MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val selectedTheme: String get() = uiPrefs.getString("theme", "midnight") ?: "midnight"
    private val bg: Int get() = when (selectedTheme) {
        "ocean" -> Color.rgb(4, 24, 43)
        "emerald" -> Color.rgb(7, 31, 27)
        "purple" -> Color.rgb(27, 17, 43)
        "black" -> Color.rgb(0, 0, 0)
        else -> Color.rgb(9, 17, 29)
    }
    private val panel: Int get() = when (selectedTheme) {
        "ocean" -> Color.rgb(17, 54, 78)
        "emerald" -> Color.rgb(20, 59, 48)
        "purple" -> Color.rgb(52, 35, 73)
        "black" -> Color.rgb(24, 24, 24)
        else -> Color.rgb(25, 39, 57)
    }
    private val green: Int get() = when (selectedTheme) {
        "ocean" -> Color.rgb(65, 205, 255)
        "emerald" -> Color.rgb(71, 245, 165)
        "purple" -> Color.rgb(199, 139, 255)
        "black" -> Color.rgb(255, 190, 70)
        else -> Color.rgb(22, 245, 132)
    }
    private val muted: Int get() = when (selectedTheme) {
        "ocean" -> Color.rgb(164, 204, 224)
        "emerald" -> Color.rgb(165, 211, 190)
        "purple" -> Color.rgb(205, 188, 224)
        "black" -> Color.rgb(190, 190, 190)
        else -> Color.rgb(167, 185, 208)
    }

    private val clockTask = object : Runnable {
        override fun run() {
            if (::clockText.isInitialized) {
                clockText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                dateText.text = SimpleDateFormat("EEEE, d MMMM", Locale("tr", "TR")).format(Date())
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshMedia()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        restoreLastTrack()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshTask)
        handler.removeCallbacks(clockTask)
        handler.post(refreshTask)
        handler.post(clockTask)
        updateClockAndWeather()
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        handler.removeCallbacks(clockTask)
        stopLocationUpdates()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST &&
            grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            findCurrentLocation()
        } else if (requestCode == LOCATION_PERMISSION_REQUEST) {
            weatherDescription.text = "Konum izni gerekli"
        }
    }

    private fun buildUi() {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(18, 32, 51), bg, Color.rgb(5, 12, 21))
            )
        }

        setContentView(
            screen,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Üst bölüm: gerçek saat/tarih ve otomatik konuma göre hava durumu.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(8))
        }

        val clockColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        clockText = TextView(this).apply {
            text = "--:--"
            textSize = clockSizeSp()
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            includeFontPadding = false
        }
        dateText = TextView(this).apply {
            text = "Konum bekleniyor"
            textSize = dateSizeSp()
            setTextColor(muted)
            maxLines = 1
        }
        clockColumn.addView(clockText, matchWrap())
        clockColumn.addView(dateText, matchWrap())
        header.addView(clockColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val weatherColumn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        weatherIcon = TextView(this).apply {
            text = "☁"
            textSize = weatherIconSizeSp()
            setTextColor(green)
            setPadding(0, 0, dp(7), 0)
        }
        val weatherTextColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        temperatureText = TextView(this).apply {
            text = "--°"
            textSize = temperatureSizeSp()
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.END
            includeFontPadding = false
        }
        weatherDescription = TextView(this).apply {
            text = "Hava durumu"
            textSize = weatherDescriptionSizeSp()
            setTextColor(muted)
            gravity = Gravity.END
            maxLines = 1
        }
        weatherTextColumn.addView(temperatureText, matchWrap())
        weatherTextColumn.addView(weatherDescription, matchWrap())
        weatherColumn.addView(weatherIcon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        weatherColumn.addView(weatherTextColumn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        header.addView(weatherColumn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        settingsButton = control("⚙", panel, Color.WHITE, 23f)
        settingsButton.minWidth = dp(48)
        settingsButton.minimumWidth = dp(48)
        settingsButton.setPadding(dp(4), 0, dp(4), 0)
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(50), dp(48)).apply {
            setMargins(dp(8), 0, 0, 0)
        })
        settingsButton.setOnClickListener { showSettingsDialog() }

        screen.addView(header, matchWidth())

        val topSpace = View(this)
        screen.addView(topSpace, LinearLayout.LayoutParams(1, 0, 0.05f))

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 24)
            clipToOutline = true
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Albüm kapağı"
        }

        screen.addView(
            art,
            LinearLayout.LayoutParams(albumSizeDp(), albumSizeDp()).apply {
                gravity = Gravity.CENTER
            }
        )

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = titleSizeSp()
            maxLines = 2
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(10), dp(2), 0)
        }
        screen.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = artistSizeSp()
            maxLines = 1
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(3), 0, dp(4))
        }
        screen.addView(artist, matchWrap())

        status = TextView(this).apply {
            text = "●  MÜZİK BEKLENİYOR"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            gravity = Gravity.CENTER
        }
        screen.addView(status, matchWrap())

        val middleSpace = View(this)
        screen.addView(middleSpace, LinearLayout.LayoutParams(1, 0, 0.02f))

        progress = SeekBar(this).apply {
            max = 1000
            progressTintList =
                android.content.res.ColorStateList.valueOf(green)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(green)
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(panel)
            minimumHeight = dp(40)
            setPadding(dp(2), 0, dp(2), 0)
        }

        screen.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        val times = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        elapsed = smallTime("0:00")
        durationText = smallTime("0:00")

        times.addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(
            durationText,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                gravity = Gravity.END
            }
        )
        screen.addView(times, matchWidth())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
        }
        screen.addView(controls, matchWidth())

        val previous = control("⏮", panel, Color.WHITE, 26f)
        controls.addView(
            previous,
            LinearLayout.LayoutParams(0, dp(controlsSizeDp()), 1f).apply {
                setMargins(0, 0, dp(7), 0)
            }
        )
        previous.setOnClickListener {
            controller?.transportControls?.skipToPrevious()
            refreshAfterTrackChange()
        }

        playButton = control("▶", green, bg, 31f)
        controls.addView(
            playButton,
            LinearLayout.LayoutParams(0, dp(controlsSizeDp() + 8), 1.15f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
        )
        playButton.setOnClickListener {
            val c = controller
            if (c == null) {
                Toast.makeText(this, "Önce müzik başlatın", Toast.LENGTH_SHORT).show()
            } else {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    c.transportControls.pause()
                } else {
                    c.transportControls.play()
                }
                handler.postDelayed({ refreshMedia() }, 350)
            }
        }

        val next = control("⏭", panel, Color.WHITE, 26f)
        controls.addView(
            next,
            LinearLayout.LayoutParams(0, dp(controlsSizeDp()), 1f).apply {
                setMargins(dp(7), 0, 0, 0)
            }
        )
        next.setOnClickListener {
            controller?.transportControls?.skipToNext()
            refreshAfterTrackChange()
        }

        permissionButton = control(
            "◉  MEDYA ERİŞİMİNİ ETKİNLEŞTİR",
            panel,
            muted,
            13f
        )
        screen.addView(
            permissionButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )
        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val footer = TextView(this).apply {
            text = "M A D E   F O R   Y O U R   D R I V E"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(0))
        }
        screen.addView(footer, matchWrap())

        val bottomSpace = View(this)
        screen.addView(bottomSpace, LinearLayout.LayoutParams(1, 0, 0.01f))
    }

    private fun refreshMedia() {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MusicNotificationListener::class.java)

        val sessions = try {
            manager.getActiveSessions(component)
        } catch (_: SecurityException) {
            permissionButton.visibility = View.VISIBLE
            status.text = "●  MEDYA ERİŞİM İZNİ GEREKLİ"
            return
        } catch (_: Exception) {
            return
        }

        val supported = sessions.filter {
            val pkg = it.packageName.lowercase()
            pkg.contains("spotify") || pkg.contains("youtube") || pkg.contains("music")
        }

        val active = supported.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: supported.firstOrNull()

        permissionButton.visibility = if (active == null) View.VISIBLE else View.GONE

        if (active == null) {
            controller = null
            playButton.text = "▶"
            status.text = "●  SON ÇALAN PARÇA KAYITLI"
            return
        }

        controller = active
        val metadata = active.metadata
        val track = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val performer = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""

        val key = "${active.packageName}|$track|$performer"

        if (track.isNotBlank()) {
            title.text = track
            artist.text = performer.ifBlank { sourceName(active.packageName) }

            if (key != lastKey) {
                // Yeni parçaya geçildiğinde eski kapağı göstermeyelim.
                art.setImageResource(android.R.drawable.ic_media_play)
                pendingCoverKey = key
                coverRetryCount = 0

                prefs.edit()
                    .putString("title", track)
                    .putString("artist", artist.text.toString())
                    .putString("package", active.packageName)
                    .apply()

                lastKey = key
            }

            // Kapak bilgisi geç gelirse sonraki yenilemelerde tekrar dene.
            if (pendingCoverKey == key && coverRetryCount < 10) {
                val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

                if (bitmap != null) {
                    art.setImageBitmap(bitmap)
                    saveBitmap(bitmap)
                    pendingCoverKey = ""
                    coverRetryCount = 0
                } else {
                    coverRetryCount++
                }
            }
        }

        val state = active.playbackState
        val playing = state?.state == PlaybackState.STATE_PLAYING
        playButton.text = if (playing) "Ⅱ" else "▶"

        status.text = if (playing) {
            "●  ŞİMDİ ÇALIYOR"
        } else {
            "●  DURAKLATILDI"
        }

        val total = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L

        if (total > 0L) {
            progress.progress = ((position.toDouble() / total) * 1000)
                .toInt().coerceIn(0, 1000)
            elapsed.text = formatTime(position)
            durationText.text = formatTime(total)
        }
    }

    private fun albumSizeDp(): Int = uiPrefs.getInt("album_size", 400).coerceIn(220, 800)
    private fun titleSizeSp(): Float = uiPrefs.getInt("title_size", 37).coerceIn(24, 44).toFloat()
    private fun artistSizeSp(): Float = uiPrefs.getInt("artist_size", 26).coerceIn(16, 32).toFloat()
    private fun controlsSizeDp(): Int = uiPrefs.getInt("controls_size", 64).coerceIn(52, 76)
    private fun clockSizeSp(): Float = uiPrefs.getInt("clock_size", 32).coerceIn(24, 48).toFloat()
    private fun dateSizeSp(): Float = uiPrefs.getInt("date_size", 12).coerceIn(10, 18).toFloat()
    private fun weatherIconSizeSp(): Float = uiPrefs.getInt("weather_size", 32).coerceIn(20, 44).toFloat()
    private fun temperatureSizeSp(): Float = uiPrefs.getInt("temperature_size", 25).coerceIn(20, 40).toFloat()
    private fun weatherDescriptionSizeSp(): Float = uiPrefs.getInt("weather_description_size", 11).coerceIn(9, 16).toFloat()

    private fun showSettingsDialog() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val heading = TextView(this).apply {
            text = "Görünüm ayarları"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        }
        outer.addView(heading, matchWrap())

        fun addSlider(
            label: String,
            key: String,
            min: Int,
            max: Int,
            initial: Int,
            unit: String,
            onPreview: (Int) -> Unit
        ): SeekBar {
            val valueLabel = TextView(this).apply {
                text = "$label: ${initial}$unit"
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(0, dp(10), 0, dp(2))
            }
            outer.addView(valueLabel, matchWrap())
            val slider = SeekBar(this).apply {
                this.max = max - min
                progress = initial - min
                progressTintList = android.content.res.ColorStateList.valueOf(green)
                thumbTintList = android.content.res.ColorStateList.valueOf(green)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(panel)
            }
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = min + progress
                    valueLabel.text = "$label: ${value}$unit"
                    onPreview(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            outer.addView(slider, matchWidth())
            return slider
        }

        val albumSlider = addSlider(
            "Albüm kapağı", "album_size", 220, 800, albumSizeDp(), " dp"
        ) { }
        val titleSlider = addSlider(
            "Şarkı adı", "title_size", 24, 44, titleSizeSp().toInt(), " sp"
        ) { }
        val artistSlider = addSlider(
            "Sanatçı adı", "artist_size", 16, 32, artistSizeSp().toInt(), " sp"
        ) { }
        val controlsSlider = addSlider(
            "Kontrol butonları", "controls_size", 52, 76, controlsSizeDp(), " dp"
        ) { }
        val clockSlider = addSlider(
            "Saat boyutu", "clock_size", 24, 48, clockSizeSp().toInt(), " sp"
        ) { }
        val weatherSlider = addSlider(
            "Hava durumu boyutu", "weather_size", 20, 44, weatherIconSizeSp().toInt(), " sp"
        ) { }

        val themeLabel = TextView(this).apply {
            text = "Tema seçimi"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(4))
        }
        outer.addView(themeLabel, matchWrap())
        val themeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val themes = listOf(
            "midnight" to "Gece mavisi",
            "ocean" to "Okyanus",
            "emerald" to "Zümrüt",
            "purple" to "Mor gece",
            "black" to "Siyah / amber"
        )
        themes.forEach { (key, label) ->
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = label
                textSize = 14f
                setTextColor(Color.WHITE)
                buttonTintList = android.content.res.ColorStateList.valueOf(green)
                tag = key
                isChecked = selectedTheme == key
            }
            themeGroup.addView(radio, matchWrap())
            if (selectedTheme == key) themeGroup.check(radio.id)
        }
        outer.addView(themeGroup, matchWrap())

        val note = TextView(this).apply {
            text = "Değişiklikler kaydedildikten sonra ana ekrana uygulanır."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(8), 0, dp(4))
        }
        outer.addView(note, matchWrap())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(outer)
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton("Kaydet", null)
            .setNeutralButton("Varsayılana dön", null)
            .setNegativeButton("İptal", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(rounded(bg, 22))
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(green)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(muted)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setTextColor(green)

            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                uiPrefs.edit()
                    .putInt("album_size", 220 + albumSlider.progress)
                    .putInt("title_size", 24 + titleSlider.progress)
                    .putInt("artist_size", 16 + artistSlider.progress)
                    .putInt("controls_size", 52 + controlsSlider.progress)
                    .putInt("clock_size", 24 + clockSlider.progress)
                    .putInt("date_size", (10 + ((clockSlider.progress * 8f) / 24f).toInt()).coerceIn(10, 18))
                    .putInt("weather_size", 20 + weatherSlider.progress)
                    .putInt("temperature_size", (20 + ((weatherSlider.progress * 20f) / 24f).toInt()).coerceIn(20, 40))
                    .putInt("weather_description_size", (9 + ((weatherSlider.progress * 7f) / 24f).toInt()).coerceIn(9, 16))
                    .putString("theme", (themeGroup.findViewById<RadioButton>(themeGroup.checkedRadioButtonId)?.tag as? String) ?: selectedTheme)
                    .apply()
                dialog.dismiss()
                rebuildUiPreservingTrack()
                Toast.makeText(this, "Görünüm ayarları kaydedildi", Toast.LENGTH_SHORT).show()
            }

            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                uiPrefs.edit().clear().apply()
                dialog.dismiss()
                recreate()
            }
        }
        dialog.show()
    }

    private fun rebuildUiPreservingTrack() {
        val savedTitle = title.text.toString()
        val savedArtist = artist.text.toString()
        val savedStatus = status.text.toString()
        val savedBitmap = (art.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        title.text = savedTitle
        artist.text = savedArtist
        status.text = savedStatus
        if (savedBitmap != null) art.setImageBitmap(savedBitmap) else restoreLastTrack()
        // Keep the player session and playback state intact.
        refreshMedia()
        updateClockAndWeather()
    }

    private fun updateClockAndWeather() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            findCurrentLocation()
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        }
    }

    @Suppress("MissingPermission")
    private fun findCurrentLocation() {
        val manager = locationManager ?: return
        try {
            val lastKnown = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER
            ).firstNotNullOfOrNull { provider ->
                try {
                    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider)
                    else null
                } catch (_: Exception) {
                    null
                }
            }

            if (lastKnown != null) {
                loadWeather(lastKnown.latitude, lastKnown.longitude)
                return
            }

            weatherDescription.text = "Konum bulunuyor…"
            stopLocationUpdates()
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    stopLocationUpdates()
                    loadWeather(location.latitude, location.longitude)
                }
                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationListener = listener
            var requested = false
            for (provider in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)) {
                try {
                    if (manager.isProviderEnabled(provider)) {
                        manager.requestLocationUpdates(provider, 1000L, 0f, listener, Looper.getMainLooper())
                        requested = true
                    }
                } catch (_: Exception) {
                }
            }
            if (!requested) weatherDescription.text = "Konum kapalı"
        } catch (_: SecurityException) {
            weatherDescription.text = "Konum izni gerekli"
        } catch (_: Exception) {
            weatherDescription.text = "Konum alınamadı"
        }
    }

    private fun stopLocationUpdates() {
        val listener = locationListener
        if (listener != null) {
            try {
                locationManager?.removeUpdates(listener)
            } catch (_: Exception) {
            }
            locationListener = null
        }
    }

    private fun loadWeather(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherUpdate < 10 * 60 * 1000L) return
        lastWeatherUpdate = now
        weatherDescription.text = "Hava yükleniyor…"

        Thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code&timezone=auto"
                )
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val current = JSONObject(response).getJSONObject("current")
                val temperature = current.getDouble("temperature_2m").toInt()
                val code = current.getInt("weather_code")
                val (icon, description) = weatherForCode(code)

                runOnUiThread {
                    temperatureText.text = "${temperature}°"
                    weatherIcon.text = icon
                    weatherDescription.text = description
                }
            } catch (_: Exception) {
                runOnUiThread {
                    weatherDescription.text = "Hava alınamadı"
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    private fun weatherForCode(code: Int): Pair<String, String> = when (code) {
        0 -> "☀" to "Açık"
        1, 2 -> "🌤" to "Az bulutlu"
        3 -> "☁" to "Bulutlu"
        45, 48 -> "🌫" to "Sisli"
        51, 53, 55, 56, 57 -> "🌦" to "Çisenti"
        61, 63, 65, 66, 67 -> "🌧" to "Yağmurlu"
        71, 73, 75, 77 -> "❄" to "Karlı"
        80, 81, 82 -> "🌧" to "Sağanak"
        85, 86 -> "❄" to "Kar yağışı"
        95, 96, 99 -> "⛈" to "Fırtınalı"
        else -> "☁" to "Değişken"
    }

    private fun refreshAfterTrackChange() {
        // Medya uygulamasının yeni parça bilgilerini yayınlaması zaman alabilir.
        handler.postDelayed({ refreshMedia() }, 250)
        handler.postDelayed({ refreshMedia() }, 650)
        handler.postDelayed({ refreshMedia() }, 1100)
        handler.postDelayed({ refreshMedia() }, 1700)
        handler.postDelayed({ refreshMedia() }, 2400)
    }

    private fun restoreLastTrack() {
        title.text = prefs.getString("title", "Henüz müzik algılanmadı")
        artist.text = prefs.getString(
            "artist", "Spotify veya YouTube Music'i başlatın"
        )
        status.text = "●  SON ÇALAN PARÇA KAYITLI"

        val encoded = prefs.getString("art", null)
        if (!encoded.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) art.setImageBitmap(bitmap)
            } catch (_: Exception) {
                art.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun saveBitmap(bitmap: Bitmap) {
        try {
            val width = 500
            val height = (bitmap.height.toFloat() / bitmap.width * width)
                .toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
            prefs.edit().putString(
                "art",
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            ).apply()
        } catch (_: Exception) {
            // Kapak kaydedilemezse parça bilgisi yine korunur.
        }
    }

    private fun sourceName(pkg: String) =
        if (pkg.contains("youtube")) "YouTube Music" else "Spotify"

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0)
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    private fun smallTime(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(muted)
        setPadding(dp(4), dp(2), dp(4), 0)
    }

    private fun control(
        label: String,
        color: Int,
        textColor: Int,
        size: Float
    ) = Button(this).apply {
        text = label
        textSize = size
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        background = rounded(color, 22)
        isAllCaps = false
        stateListAnimator = null
    }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.rgb(39, 57, 79))
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 2041
    }
}
