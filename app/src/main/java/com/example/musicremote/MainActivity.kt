
package com.example.musicremote

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
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
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import android.util.Base64

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

    private var controller: MediaController? = null
    private var lastKey = ""

    private val prefs by lazy {
        getSharedPreferences("music_remote_saved", MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshMedia()
            handler.postDelayed(this, 1000)
        }
    }

    private val bg = Color.rgb(9, 17, 29)
    private val panel = Color.rgb(25, 39, 57)
    private val green = Color.rgb(22, 245, 132)
    private val muted = Color.rgb(167, 185, 208)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        buildUi()
        restoreLastTrack()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    private fun buildUi() {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(12), dp(22), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(18, 32, 51), bg, Color.rgb(5, 12, 21))
            )
        }

        setContentView(screen)

        val header = TextView(this).apply {
            text = "MUSIC REMOTE"
            textSize = 25f
            letterSpacing = 0.06f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        screen.addView(header, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Y O U R   M U S I C   •   Y O U R   M O M E N T"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(14))
        }
        screen.addView(subtitle, matchWrap())

        val spacerTop = View(this)
        screen.addView(spacerTop, LinearLayout.LayoutParams(1, 0, 0.35f))

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 24)
            clipToOutline = true
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Albüm kapağı"
        }

        screen.addView(
            art,
            LinearLayout.LayoutParams(dp(230), dp(230)).apply {
                gravity = Gravity.CENTER
            }
        )

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = 29f
            maxLines = 2
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(18), dp(4), 0)
        }
        screen.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = 21f
            maxLines = 1
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(12))
        }
        screen.addView(artist, matchWrap())

        status = TextView(this).apply {
            text = "●  MÜZİK BEKLENİYOR"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(green)
            gravity = Gravity.CENTER
        }
        screen.addView(status, matchWrap())

        val spacerMiddle = View(this)
        screen.addView(spacerMiddle, LinearLayout.LayoutParams(1, 0, 0.65f))

        progress = SeekBar(this).apply {
            max = 1000
            progressTintList =
                android.content.res.ColorStateList.valueOf(green)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(green)
            progressBackgroundTintList =
                android.content.res.ColorStateList.valueOf(panel)
        }
        screen.addView(progress, matchWidth())

        val times = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        elapsed = smallTime("0:00")
        durationText = smallTime("0:00")

        times.addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f))
        times.addView(durationText, LinearLayout.LayoutParams(0, -2, 1f).apply {
            gravity = Gravity.END
        })
        screen.addView(times, matchWidth())

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(16))
        }
        screen.addView(controls, matchWidth())

        val previous = control("⏮", panel, Color.WHITE)
        controls.addView(previous, LinearLayout.LayoutParams(0, dp(76), 1f).apply {
            setMargins(0, 0, dp(8), 0)
        })
        previous.setOnClickListener {
            controller?.transportControls?.skipToPrevious()
            handler.postDelayed({ refreshMedia() }, 400)
        }

        playButton = control("▶", green, bg)
        controls.addView(playButton, LinearLayout.LayoutParams(0, dp(84), 1.15f).apply {
            setMargins(dp(2), 0, dp(2), 0)
        })
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

        val next = control("⏭", panel, Color.WHITE)
        controls.addView(next, LinearLayout.LayoutParams(0, dp(76), 1f).apply {
            setMargins(dp(8), 0, 0, 0)
        })
        next.setOnClickListener {
            controller?.transportControls?.skipToNext()
            handler.postDelayed({ refreshMedia() }, 400)
        }

        permissionButton = control("◉  MEDYA ERİŞİMİNİ ETKİNLEŞTİR", panel, muted)
        permissionButton.textSize = 13f
        screen.addView(permissionButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(62)
        ))
        permissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val footer = TextView(this).apply {
            text = "M A D E   F O R   Y O U R   D R I V E"
            textSize = 10f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(6))
        }
        screen.addView(footer, matchWrap())

        val spacerBottom = View(this)
        screen.addView(spacerBottom, LinearLayout.LayoutParams(1, 0, 0.25f))
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
            pkg.contains("spotify") ||
                pkg.contains("youtube") ||
                pkg.contains("music")
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

            val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

            if (key != lastKey) {
                if (bitmap != null) {
                    art.setImageBitmap(bitmap)
                    saveBitmap(bitmap)
                }
                prefs.edit()
                    .putString("title", track)
                    .putString("artist", artist.text.toString())
                    .putString("package", active.packageName)
                    .apply()
                lastKey = key
            }
        }

        val state = active.playbackState
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        playButton.text = if (isPlaying) "Ⅱ" else "▶"
        status.text = if (isPlaying) {
            "●  ŞİMDİ ÇALIYOR"
        } else {
            "●  DURAKLATILDI"
        }

        val total = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = state?.position ?: 0L
        if (total > 0) {
            progress.max = 1000
            progress.progress = ((position.toDouble() / total) * 1000)
                .toInt().coerceIn(0, 1000)
            elapsed.text = formatTime(position)
            durationText.text = formatTime(total)
        } else {
            elapsed.text = "0:00"
            durationText.text = "0:00"
        }
    }

    private fun restoreLastTrack() {
        title.text = prefs.getString("title", "Henüz müzik algılanmadı")
        artist.text = prefs.getString("artist", "Spotify veya YouTube Music'i başlatın")
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
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                minOf(bitmap.width, 500),
                minOf(bitmap.height, 500),
                true
            )
            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
            prefs.edit().putString(
                "art",
                Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            ).apply()
        } catch (_: Exception) {
            // Şarkı bilgileri yine de kaydedilir.
        }
    }

    private fun sourceName(pkg: String) =
        if (pkg.contains("youtube")) "YouTube Music" else "Spotify"

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    private fun smallTime(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(muted)
        setPadding(dp(4), dp(2), dp(4), 0)
    }

    private fun control(label: String, color: Int, textColor: Int) =
        Button(this).apply {
            text = label
            textSize = 21f
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
}
