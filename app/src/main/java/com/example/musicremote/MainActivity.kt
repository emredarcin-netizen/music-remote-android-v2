package com.example.musicremote

import android.app.Activity
import android.app.Notification
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

    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(13, 20, 32))
        }
        scroll.addView(root)
        setContentView(scroll)

        val heading = TextView(this).apply {
            text = "MUSIC REMOTE"
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(heading, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Spotify ve YouTube Music kumandası"
            textSize = 14f
            setTextColor(Color.rgb(170, 185, 205))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(18))
        }
        root.addView(subtitle, matchWrap())

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(34, 46, 64))
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Albüm kapağı"
        }
        root.addView(art, LinearLayout.LayoutParams(dp(260), dp(260)).apply {
            gravity = Gravity.CENTER
            bottomMargin = dp(22)
        })

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = 16f
            setTextColor(Color.rgb(185, 198, 216))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(12))
        }
        root.addView(artist, matchWrap())

        status = TextView(this).apply {
            text = "Medya erişimi kontrol ediliyor…"
            textSize = 13f
            setTextColor(Color.rgb(95, 205, 165))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(22))
        }
        root.addView(status, matchWrap())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(controls, matchWrap())

        controls.addView(controlButton("⏮", "Önceki") {
            selectedController?.transportControls?.skipToPrevious()
            refreshMedia()
        })
        controls.addView(controlButton("▶ / ⏸", "Oynat / duraklat") {
            val c = selectedController
            if (c != null) {
                val state = c.playbackState?.state
                if (state == android.media.session.PlaybackState.STATE_PLAYING) {
                    c.transportControls.pause()
                } else {
                    c.transportControls.play()
                }
            }
            handler.postDelayed({ refreshMedia() }, 350)
        })
        controls.addView(controlButton("⏭", "Sonraki") {
            selectedController?.transportControls?.skipToNext()
            handler.postDelayed({ refreshMedia() }, 350)
        })

        permissionButton = Button(this).apply {
            text = "Medya erişimini etkinleştir"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        root.addView(permissionButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        val note = TextView(this).apply {
            text = "Not: Android'in bildirim erişimi izni gerekir. Bazı uygulamalar parça bilgisi veya kapak görselini paylaşmayabilir."
            textSize = 12f
            setTextColor(Color.rgb(145, 158, 177))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(note, matchWrap())
    }

    private fun refreshMedia() {
        val manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(this, MusicNotificationListener::class.java)
        val controllers = try {
            manager.getActiveSessions(component)
        } catch (_: SecurityException) {
            permissionButton.visibility = View.VISIBLE
            status.text = "Önce medya erişim iznini açın."
            return
        } catch (_: Exception) {
            status.text = "Medya oturumlarına erişilemiyor."
            return
        }

        permissionButton.visibility = View.GONE
        val supported = controllers.filter {
            val pkg = it.packageName.lowercase()
            pkg.contains("spotify") ||
                pkg.contains("youtube") ||
                pkg.contains("music")
        }
        val playing = supported.firstOrNull {
            it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        }
        val controller = playing ?: supported.firstOrNull()
        selectedController = controller

        if (controller == null) {
            title.text = "Henüz müzik algılanmadı"
            artist.text = "Spotify veya YouTube Music'i başlatın"
            status.text = "Bekleniyor"
            art.setImageResource(android.R.drawable.ic_media_play)
            lastKey = ""
            return
        }

        val metadata = controller.metadata
        val track = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val performer = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val key = "${controller.packageName}|$track|$performer"
        title.text = track.ifBlank { "Bilinmeyen parça" }
        artist.text = performer.ifBlank {
            if (controller.packageName.contains("youtube")) "YouTube Music" else "Spotify"
        }
        val state = controller.playbackState?.state
        status.text = when (state) {
            android.media.session.PlaybackState.STATE_PLAYING -> "Şimdi çalıyor • ${sourceName(controller.packageName)}"
            android.media.session.PlaybackState.STATE_PAUSED -> "Duraklatıldı • ${sourceName(controller.packageName)}"
            else -> "Medya oturumu bulundu • ${sourceName(controller.packageName)}"
        }

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

    private fun controlButton(symbol: String, description: String, action: () -> Unit): Button =
        Button(this).apply {
            text = symbol
            contentDescription = description
            textSize = 17f
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, dp(62), 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
