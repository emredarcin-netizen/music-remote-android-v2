package com.example.musicremote

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
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
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var art: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var status: TextView
    private lateinit var permissionButton: Button
    private var selectedController: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastKey = ""

    private val refresh = object : Runnable {
        override fun run() {
            refreshMedia()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 18, 18)
        window.navigationBarColor = Color.rgb(18, 18, 18)
        buildUi()
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        buildUi()
        refreshMedia()
    }

    private fun buildUi() {
        val landscape = resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(18, 18, 18))
            isFillViewport = true
        }

        root = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(Color.rgb(18, 18, 18))
        }
        scroll.addView(root)
        setContentView(scroll)

        val content = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        root.addView(
            content,
            if (landscape) LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ) else LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(42, 42, 42))
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Albüm kapağı"
            clipToOutline = true
        }

        val artworkSize = if (landscape) 270 else 300
        content.addView(
            art,
            LinearLayout.LayoutParams(
                if (landscape) dp(artworkSize) else minOf(dp(artworkSize), resources.displayMetrics.widthPixels - dp(48)),
                if (landscape) dp(artworkSize) else minOf(dp(artworkSize), resources.displayMetrics.widthPixels - dp(48))
            ).apply {
                gravity = Gravity.CENTER
                if (landscape) {
                    marginEnd = dp(28)
                } else {
                    bottomMargin = dp(24)
                }
            }
        )

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        content.addView(
            info,
            if (landscape) LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ) else LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val heading = TextView(this).apply {
            text = "MUSIC REMOTE"
            textSize = 14f
            letterSpacing = 0.15f
            setTextColor(Color.rgb(30, 215, 96))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        info.addView(heading, matchWrap())

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(4))
        }
        info.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = 16f
            setTextColor(Color.rgb(179, 179, 179))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(12))
        }
        info.addView(artist, matchWrap())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        info.addView(
            controls,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )

        controls.addView(controlButton("⏮", "Önceki") {
            selectedController?.transportControls?.skipToPrevious()
            handler.postDelayed({ refreshMedia() }, 300)
        })

        controls.addView(controlButton("▶", "Oynat veya duraklat") {
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
        })

        controls.addView(controlButton("⏭", "Sonraki") {
            selectedController?.transportControls?.skipToNext()
            handler.postDelayed({ refreshMedia() }, 350)
        })

        status = TextView(this).apply {
            text = "Medya oturumu bekleniyor"
            textSize = 12f
            setTextColor(Color.rgb(30, 215, 96))
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(8))
        }
        info.addView(status, matchWrap())

        permissionButton = Button(this).apply {
            text = "Medya erişimini etkinleştir"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        info.addView(
            permissionButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        )
    }

    private fun refreshMedia() {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MusicNotificationListener::class.java)

        val controllers = try {
            manager.getActiveSessions(component)
        } catch (_: SecurityException) {
            permissionButton.visibility = View.VISIBLE
            status.text = "Medya erişim iznini etkinleştirin"
            return
        } catch (_: Exception) {
            status.text = "Medya oturumlarına erişilemiyor"
            return
        }

        val supported = controllers.filter {
            val pkg = it.packageName.lowercase()
            pkg.contains("spotify") || pkg.contains("youtube") || pkg.contains("music")
        }

        val playing = supported.firstOrNull {
            it.playbackState?.state ==
                android.media.session.PlaybackState.STATE_PLAYING
        }
        val controller = playing ?: supported.firstOrNull()
        selectedController = controller

        if (controller == null) {
            title.text = "Henüz müzik algılanmadı"
            artist.text = "Spotify veya YouTube Music'i başlatın"
            status.text = "Müzik bekleniyor"
            art.setImageResource(android.R.drawable.ic_media_play)
            permissionButton.visibility = View.VISIBLE
            lastKey = ""
            return
        }

        permissionButton.visibility = View.GONE

        val metadata = controller.metadata
        val track = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val performer = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""

        title.text = track.ifBlank { "Bilinmeyen parça" }
        artist.text = performer.ifBlank { sourceName(controller.packageName) }

        status.text = when (controller.playbackState?.state) {
            android.media.session.PlaybackState.STATE_PLAYING ->
                "● ŞİMDİ ÇALIYOR · ${sourceName(controller.packageName)}"
            android.media.session.PlaybackState.STATE_PAUSED ->
                "DURAKLATILDI · ${sourceName(controller.packageName)}"
            else -> "MEDYA OTURUMU · ${sourceName(controller.packageName)}"
        }

        val key = "${controller.packageName}|$track|$performer"
        if (key != lastKey) {
            val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            if (bitmap != null) {
                art.setImageBitmap(bitmap)
            } else {
                art.setImageResource(android.R.drawable.ic_media_play)
            }
            lastKey = key
        }
    }

    private fun sourceName(pkg: String): String =
        if (pkg.contains("youtube")) "YouTube Music" else "Spotify"

    private fun controlButton(
        symbol: String,
        description: String,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = symbol
        contentDescription = description
        textSize = 22f
        setTextColor(Color.WHITE)
        setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(Color.rgb(38, 38, 38))
        )
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, dp(62), 1f).apply {
            setMargins(dp(5), 0, dp(5), 0)
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
