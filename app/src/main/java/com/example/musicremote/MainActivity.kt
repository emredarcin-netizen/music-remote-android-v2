
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
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
        getSharedPreferences("music_remote_memory", MODE_PRIVATE)
    }

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
            setPadding(dp(20), dp(12), dp(20), dp(16))
            setBackgroundColor(Color.rgb(13, 16, 25))
        }

        scroll.addView(root)
        setContentView(scroll)

        val heading = TextView(this).apply {
            text = "MUSIC REMOTE"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        root.addView(heading, matchWrap())

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(55, 60, 72))
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Albüm kapağı"
            clipToOutline = true
            background = roundedBackground(Color.rgb(55, 60, 72), 14)
        }

        // Küçük albüm kapağı: dikey ekranda da taşmaz.
        root.addView(
            art,
            LinearLayout.LayoutParams(dp(130), dp(130)).apply {
                gravity = Gravity.CENTER
                topMargin = dp(14)
                bottomMargin = dp(14)
            }
        )

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
        }
        root.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = 15f
            setTextColor(Color.rgb(190, 198, 211))
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(8))
            maxLines = 2
        }
        root.addView(artist, matchWrap())

        status = TextView(this).apply {
            text = "Müzik bekleniyor"
            textSize = 12f
            setTextColor(Color.rgb(55, 230, 110))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(10))
        }
        root.addView(status, matchWrap())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(controls, matchWrap())

        controls.addView(controlButton("⏮") {
            selectedController?.transportControls?.skipToPrevious()
            handler.postDelayed({ refreshMedia() }, 350)
        })

        controls.addView(controlButton("▶") {
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

        controls.addView(controlButton("⏭") {
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

        root.addView(
            permissionButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        )
    }

    private fun refreshMedia() {
        val manager =
            getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component =
            ComponentName(this, MusicNotificationListener::class.java)

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
            status.text = "Son çalan parça gösteriliyor"
            return
        }

        val metadata = controller.metadata
        val track =
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val performer =
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                )
                ?: ""

        val bitmap =
            metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ALBUM_ART
                )
                ?: metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_DISPLAY_ICON
                )

        if (track.isNotBlank()) {
            title.text = track
            artist.text = performer.ifBlank {
                if (controller.packageName.contains("youtube")) {
                    "YouTube Music"
                } else {
                    "Spotify"
                }
            }

            prefs.edit()
                .putString("last_title", track)
                .putString("last_artist", artist.text.toString())
                .putString("last_package", controller.packageName)
                .apply()

            if (bitmap != null) {
                art.setImageBitmap(bitmap)
                saveCover(bitmap)
            }
        }

        status.text =
            if (controller.playbackState?.state ==
                android.media.session.PlaybackState.STATE_PLAYING
            ) {
                "Şimdi çalıyor"
            } else {
                "Duraklatıldı"
            }
    }

    private fun saveCover(bitmap: Bitmap) {
        try {
            val file = File(filesDir, "last_album_cover.png")
            FileOutputStream(file).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            prefs.edit().putBoolean("has_cover", true).apply()
        } catch (_: Exception) {
            // Kapak kaydedilemezse uygulama çalışmaya devam eder.
        }
    }

    private fun restoreLastTrack() {
        val savedTitle = prefs.getString("last_title", null)
        val savedArtist = prefs.getString("last_artist", null)

        if (!savedTitle.isNullOrBlank()) {
            title.text = savedTitle
            artist.text = savedArtist ?: ""
            status.text = "Son çalan parça"

            try {
                val file = File(filesDir, "last_album_cover.png")
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) art.setImageBitmap(bitmap)
                }
            } catch (_: Exception) {
                // Kayıtlı kapak okunamazsa varsayılan görsel kalır.
            }
        }
    }

    private fun controlButton(
        symbol: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = symbol
            textSize = 21f
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(40, 220, 85), 40)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                0, dp(54), 1f
            ).apply {
                setMargins(dp(5), dp(4), dp(5), dp(4))
            }
        }
    }

    private fun roundedBackground(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
