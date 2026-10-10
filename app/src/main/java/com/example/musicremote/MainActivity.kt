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
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream

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
        getSharedPreferences("last_music", MODE_PRIVATE)
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
        buildUi()
        restoreLastTrack()
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
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(Color.rgb(13, 20, 32))
        }

        scroll.addView(root)
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = "MUSIC REMOTE"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWrap())

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(45, 48, 58))
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Albüm kapağı"
        }

        root.addView(art, LinearLayout.LayoutParams(dp(130), dp(130)).apply {
            gravity = Gravity.CENTER
            topMargin = dp(18)
            bottomMargin = dp(18)
        })

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = 15f
            setTextColor(Color.rgb(185, 198, 216))
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(8))
        }
        root.addView(artist, matchWrap())

        status = TextView(this).apply {
            text = "Müzik bekleniyor"
            textSize = 12f
            setTextColor(Color.rgb(70, 235, 100))
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(status, matchWrap())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(controls, matchWrap())

        controls.addView(controlButton("⏮", "Önceki") {
            selectedController?.transportControls?.skipToPrevious()
            handler.postDelayed({ refreshMedia() }, 350)
        })

        controls.addView(controlButton("▶", "Oynat / duraklat") {
            selectedController?.let { controller ->
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

        permissionButton = Button(this).apply {
            text = "MEDYA ERİŞİMİNİ ETKİNLEŞTİR"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
        }

        root.addView(permissionButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(18)
        })
    }

    private fun restoreLastTrack() {
        title.text = prefs.getString("title", "Henüz müzik algılanmadı")
        artist.text = prefs.getString(
            "artist", "Spotify veya YouTube Music'i başlatın"
        )

        val encoded = prefs.getString("art", null)
        if (!encoded.isNullOrBlank()) {
            try {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(
                    bytes, 0, bytes.size
                )
                if (bitmap != null) art.setImageBitmap(bitmap)
            } catch (_: Exception) {
                // Kayıtlı kapak okunamazsa varsayılan görsel kalır.
            }
        }

        status.text = "Son çalınan parça"
    }

    private fun saveTrack(
        track: String,
        performer: String,
        bitmap: Bitmap?
    ) {
        val editor = prefs.edit()
            .putString("title", track)
            .putString("artist", performer)

        if (bitmap != null) {
            try {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                val encoded = Base64.encodeToString(
                    output.toByteArray(), Base64.NO_WRAP
                )
                editor.putString("art", encoded)
            } catch (_: Exception) {
                // Önceki kapak kaydı korunur.
            }
        }

        editor.apply()
    }

    private fun refreshMedia() {
        val manager =
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(
            this, MusicNotificationListener::class.java
        )

        val controllers = try {
            manager.getActiveSessions(component)
        } catch (_: SecurityException) {
            permissionButton.visibility = View.VISIBLE
            status.text = "Önce medya erişim iznini açın"
            return
        } catch (_: Exception) {
            status.text = "Medya oturumları bekleniyor"
            return
        }

        permissionButton.visibility = View.GONE

        val supported = controllers.filter {
            val pkg = it.packageName.lowercase()
            pkg.contains("spotify") ||
                pkg.contains("youtube") ||
                pkg.contains("music")
        }

        val controller = supported.firstOrNull {
            it.playbackState?.state ==
                android.media.session.PlaybackState.STATE_PLAYING
        } ?: supported.firstOrNull()

        selectedController = controller

        if (controller == null) {
            if (prefs.contains("title")) {
                status.text = "Son çalınan parça"
            } else {
                title.text = "Henüz müzik algılanmadı"
                artist.text = "Spotify veya YouTube Music'i başlatın"
                status.text = "Müzik bekleniyor"
            }
            return
        }

        val metadata = controller.metadata
        val track = metadata?.getString(
            MediaMetadata.METADATA_KEY_TITLE
        ).orEmpty()

        val performer = metadata?.getString(
            MediaMetadata.METADATA_KEY_ARTIST
        ) ?: metadata?.getString(
            MediaMetadata.METADATA_KEY_ALBUM_ARTIST
        ) ?: ""

        if (track.isBlank()) return

        val key = "${controller.packageName}|$track|$performer"

        val bitmap = metadata?.getBitmap(
            MediaMetadata.METADATA_KEY_ART
        ) ?: metadata?.getBitmap(
            MediaMetadata.METADATA_KEY_ALBUM_ART
        ) ?: metadata?.getBitmap(
            MediaMetadata.METADATA_KEY_DISPLAY_ICON
        )

        title.text = track
        artist.text = performer.ifBlank {
            if (controller.packageName.contains("youtube"))
                "YouTube Music" else "Spotify"
        }

        status.text = when (
            controller.playbackState?.state
        ) {
            android.media.session.PlaybackState.STATE_PLAYING ->
                "Şimdi çalıyor"
            android.media.session.PlaybackState.STATE_PAUSED ->
                "Duraklatıldı"
            else -> "Medya oturumu bulundu"
        }

        if (bitmap != null) art.setImageBitmap(bitmap)

        if (key != lastKey) {
            saveTrack(
                track,
                artist.text.toString(),
                bitmap
            )
            lastKey = key
        }
    }

    private fun controlButton(
        symbol: String,
        description: String,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = symbol
        contentDescription = description
        textSize = 17f
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            0, dp(54), 1f
        ).apply {
            setMargins(dp(4), 0, dp(4), 0)
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
