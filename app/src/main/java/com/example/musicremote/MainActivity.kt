
package com.example.musicremote

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button
import android.graphics.drawable.GradientDrawable
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var art: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var status: TextView
    private lateinit var permissionButton: Button

    private var selectedController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy {
        getSharedPreferences("music_remote_saved", MODE_PRIVATE)
    }

    private var lastKey = ""

    private val refresh = object : Runnable {
        override fun run() {
            refreshMedia()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 12, 15)
        window.navigationBarColor = Color.rgb(10, 12, 15)
        window.decorView.systemUiVisibility = 0

        buildUi()
        showSavedTrack()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setFillViewport(true)
            setBackgroundColor(Color.rgb(10, 12, 15))
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            setBackgroundColor(Color.rgb(10, 12, 15))
        }

        scroll.addView(
            root,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(scroll)

        val header = TextView(this).apply {
            text = "♫     MUSIC REMOTE"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(header, matchWrap())
        addSpace(24)

        // Albüm kapağı: önceki 260 dp yerine 130 dp
        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(35, 40, 48))
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Albüm kapağı"
            clipToOutline = true
            background = roundedBackground(
                Color.rgb(35, 40, 48), 18
            )
        }

        root.addView(
            art,
            LinearLayout.LayoutParams(dp(130), dp(130)).apply {
                gravity = Gravity.CENTER
            }
        )

        addSpace(18)

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
        }
        root.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = 16f
            setTextColor(Color.rgb(165, 170, 185))
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(artist, matchWrap())

        addSpace(26)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        root.addView(controls, matchWrap())

        val previous = makeControl("⏮", 30f, Color.WHITE)
        previous.setOnClickListener {
            selectedController?.transportControls?.skipToPrevious()
            handler.postDelayed({ refreshMedia() }, 350)
        }

        val play = TextView(this).apply {
            text = "▶"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = roundedBackground(
                Color.rgb(29, 220, 103), 100
            )
            contentDescription = "Oynat veya duraklat"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val controller = selectedController
                if (controller != null) {
                    if (controller.playbackState?.state ==
                        android.media.session.PlaybackState.STATE_PLAYING
                    ) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                }
                handler.postDelayed({ refreshMedia() }, 350)
            }
        }

        val next = makeControl("⏭", 30f, Color.WHITE)
        next.setOnClickListener {
            selectedController?.transportControls?.skipToNext()
            handler.postDelayed({ refreshMedia() }, 350)
        }

        controls.addView(
            previous,
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )
        controls.addView(
            play,
            LinearLayout.LayoutParams(dp(66), dp(66)).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(12)
                rightMargin = dp(12)
            }
        )
        controls.addView(
            next,
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )

        addSpace(22)

        status = TextView(this).apply {
            text = "Müzik bekleniyor"
            textSize = 14f
            setTextColor(Color.rgb(29, 220, 103))
            gravity = Gravity.CENTER
        }
        root.addView(status, matchWrap())

        permissionButton = Button(this).apply {
            text = "MEDYA ERİŞİMİNİ ETKİNLEŞTİR"
            textSize = 13f
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
        }

        root.addView(
            permissionButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(24)
            }
        )
    }

    private fun refreshMedia() {
        val manager =
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

        val component = ComponentName(
            this,
            MusicNotificationListener::class.java
        )

        val controllers = try {
            manager.getActiveSessions(component)
        } catch (_: SecurityException) {
            permissionButton.visibility = View.VISIBLE
            status.text = "Medya erişim iznini açın"
            return
        } catch (_: Exception) {
            status.text = "Medya oturumlarına erişilemiyor"
            return
        }

        val supported = controllers.filter {
            val pkg = it.packageName.lowercase()
            pkg.contains("spotify") ||
                pkg.contains("youtube") ||
                pkg.contains("music")
        }

        val playing = supported.firstOrNull {
            it.playbackState?.state ==
                android.media.session.PlaybackState.STATE_PLAYING
        }

        val controller = playing ?: supported.firstOrNull()
        selectedController = controller

        if (controller == null) {
            permissionButton.visibility = View.VISIBLE
            showSavedTrack()
            return
        }

        permissionButton.visibility = View.GONE

        val metadata = controller.metadata
        val track = metadata
            ?.getString(MediaMetadata.METADATA_KEY_TITLE)
            .orEmpty()

        val performer =
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                )
                ?: ""

        val key = "${controller.packageName}|$track|$performer"

        if (track.isNotBlank()) {
            title.text = track
            artist.text = performer.ifBlank {
                sourceName(controller.packageName)
            }

            if (key != lastKey) {
                val bitmap =
                    metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        ?: metadata?.getBitmap(
                            MediaMetadata.METADATA_KEY_ALBUM_ART
                        )
                        ?: metadata?.getBitmap(
                            MediaMetadata.METADATA_KEY_DISPLAY_ICON
                        )

                if (bitmap != null) {
                    art.setImageBitmap(bitmap)
                    saveCover(bitmap)
                } else {
                    // Yeni parçanın kapağı paylaşılmıyorsa
                    // önceki kayıtlı kapağı silmeyiz.
                    showSavedCover()
                }

                prefs.edit()
                    .putString("track_title", track)
                    .putString("track_artist", performer)
                    .putString("track_source", controller.packageName)
                    .apply()

                lastKey = key
            }
        } else {
            showSavedTrack()
        }

        status.text = when (
            controller.playbackState?.state
        ) {
            android.media.session.PlaybackState.STATE_PLAYING ->
                "● ŞİMDİ ÇALIYOR"
            android.media.session.PlaybackState.STATE_PAUSED ->
                "DURAKLATILDI"
            else -> "MEDYA OTURUMU BULUNDU"
        }
    }

    private fun saveCover(bitmap: Bitmap) {
        try {
            val file = File(filesDir, "last_album_cover.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            prefs.edit().putBoolean("has_cover", true).apply()
        } catch (_: Exception) {
            // Görsel kaydedilemezse uygulama çalışmaya devam eder.
        }
    }

    private fun showSavedCover() {
        val file = File(filesDir, "last_album_cover.jpg")
        if (file.exists()) {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) art.setImageBitmap(bitmap)
        }
    }

    private fun showSavedTrack() {
        if (!::title.isInitialized) return

        val savedTitle = prefs.getString("track_title", "").orEmpty()
        val savedArtist = prefs.getString("track_artist", "").orEmpty()

        if (savedTitle.isNotBlank()) {
            title.text = savedTitle
            artist.text = savedArtist.ifBlank { "Son çalan parça" }
            showSavedCover()

            if (selectedController == null) {
                status.text = "SON ÇALAN PARÇA"
            }
        } else {
            title.text = "Henüz müzik algılanmadı"
            artist.text = "Spotify veya YouTube Music'i başlatın"
            status.text = "Müzik bekleniyor"
        }
    }

    private fun sourceName(pkg: String): String =
        if (pkg.contains("youtube")) "YouTube Music"
        else "Spotify"

    private fun makeControl(
        symbol: String,
        size: Float,
        color: Int
    ): TextView =
        TextView(this).apply {
            text = symbol
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            contentDescription = when (symbol) {
                "⏮" -> "Önceki parça"
                else -> "Sonraki parça"
            }
            isClickable = true
            isFocusable = true
        }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun addSpace(size: Int) {
        root.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(size))
        )
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
