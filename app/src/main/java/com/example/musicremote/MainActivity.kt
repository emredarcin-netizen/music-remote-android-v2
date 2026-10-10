
package com.example.musicremote

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.content.pm.ActivityInfo
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private var controller: MediaController? = null
    private lateinit var art: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var status: TextView
    private lateinit var permission: Button
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
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        window.statusBarColor = Color.rgb(16, 17, 20)
        window.navigationBarColor = Color.rgb(16, 17, 20)
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

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = true
    }

    private fun buildUi() {
        val green = Color.rgb(30, 215, 96)
        val white = Color.WHITE
        val muted = Color.rgb(179, 179, 179)
        val black = Color.rgb(16, 17, 20)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(black)
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(18))
            setBackgroundColor(black)
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(38)
        ))

        header.addView(text("♫", 27f, green, true))
        header.addView(text("  MUSIC REMOTE", 17f, white, true),
            LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
        header.addView(text("●", 13f, muted))

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_media_play)
            setBackground(rounded(Color.rgb(38, 39, 44), 16))
            clipToOutline = true
            contentDescription = "Albüm kapağı"
        }
        root.addView(art, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply {
            topMargin = dp(20)
            bottomMargin = dp(22)
        })

        title = text("Henüz müzik algılanmadı", 24f, white, true)
        title.maxLines = 2
        title.setPadding(dp(4), dp(2), dp(4), dp(2))
        root.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        artist = text("Spotify veya YouTube Music'i başlatın", 16f, muted)
        artist.maxLines = 2
        root.addView(artist, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(5)
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        root.addView(controls, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(82)
        ).apply {
            topMargin = dp(20)
        })

        controls.addView(iconButton("⏮", false) {
            controller?.transportControls?.skipToPrevious()
            handler.postDelayed({ refreshMedia() }, 300)
        }, LinearLayout.LayoutParams(0, dp(58), 1f))

        val play = TextView(this).apply {
            text = "▶"
            textSize = 28f
            setTextColor(black)
            gravity = Gravity.CENTER
            background = rounded(green, 100)
            contentDescription = "Oynat veya duraklat"
            setOnClickListener {
                val c = controller
                if (c != null) {
                    if (c.playbackState?.state ==
                        android.media.session.PlaybackState.STATE_PLAYING
                    ) c.transportControls.pause()
                    else c.transportControls.play()
                }
                handler.postDelayed({ refreshMedia() }, 350)
            }
        }
        controls.addView(play, LinearLayout.LayoutParams(dp(68), dp(68)).apply {
            setMargins(dp(12), 0, dp(12), 0)
        })

        controls.addView(iconButton("⏭", false) {
            controller?.transportControls?.skipToNext()
            handler.postDelayed({ refreshMedia() }, 300)
        }, LinearLayout.LayoutParams(0, dp(58), 1f))

        status = text("● ŞİMDİ ÇALIYOR", 12f, green, true)
        root.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(32)
        ).apply {
            topMargin = dp(4)
        })

        permission = Button(this).apply {
            text = "MEDYA ERİŞİMİNİ ETKİNLEŞTİR"
            textSize = 12f
            setTextColor(white)
            background = rounded(Color.rgb(43, 44, 49), 8)
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
        root.addView(permission, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        ).apply {
            topMargin = dp(8)
        })
    }

    private fun iconButton(
        symbol: String,
        unused: Boolean,
        action: () -> Unit
    ) = TextView(this).apply {
        text = symbol
        textSize = 25f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        contentDescription = if (symbol == "⏮") "Önceki" else "Sonraki"
        setOnClickListener { action() }
    }

    private fun refreshMedia() {
        val manager = getSystemService(MEDIA_SESSION_SERVICE)
            as MediaSessionManager
        val component = ComponentName(this, MusicNotificationListener::class.java)

        val sessions = try {
            manager.getActiveSessions(component)
        } catch (_: SecurityException) {
            permission.visibility = View.VISIBLE
            status.text = "Medya erişim iznini etkinleştirin"
            return
        } catch (_: Exception) {
            status.text = "Medya oturumlarına erişilemiyor"
            return
        }

        val supported = sessions.filter {
            val pkg = it.packageName.lowercase()
            pkg.contains("spotify") || pkg.contains("youtube") ||
                pkg.contains("music")
        }
        controller = supported.firstOrNull {
            it.playbackState?.state ==
                android.media.session.PlaybackState.STATE_PLAYING
        } ?: supported.firstOrNull()

        val c = controller
        if (c == null) {
            title.text = "Henüz müzik algılanmadı"
            artist.text = "Spotify veya YouTube Music'i başlatın"
            status.text = "Müzik bekleniyor"
            art.setImageResource(android.R.drawable.ic_media_play)
            permission.visibility = View.VISIBLE
            lastKey = ""
            return
        }

        permission.visibility = View.GONE
        val metadata = c.metadata
        val track = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val performer = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""

        title.text = track.ifBlank { "Bilinmeyen parça" }
        artist.text = performer.ifBlank {
            if (c.packageName.contains("youtube")) "YouTube Music" else "Spotify"
        }

        status.text = when (c.playbackState?.state) {
            android.media.session.PlaybackState.STATE_PLAYING ->
                "● ŞİMDİ ÇALIYOR"
            android.media.session.PlaybackState.STATE_PAUSED ->
                "DURAKLATILDI"
            else -> "MEDYA OTURUMU BAĞLANDI"
        }

        val key = "${c.packageName}|$track|$performer"
        if (key != lastKey) {
            val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            if (bitmap != null) art.setImageBitmap(bitmap)
            else art.setImageResource(android.R.drawable.ic_media_play)
            lastKey = key
        }
    }
}
