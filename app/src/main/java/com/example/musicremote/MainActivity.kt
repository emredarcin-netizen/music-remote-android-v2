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
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream

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
    private var pendingCoverKey = ""
    private var coverRetryCount = 0

    private val prefs by lazy {
        getSharedPreferences("music_remote_saved", MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val bg = Color.rgb(9, 17, 29)
    private val panel = Color.rgb(25, 39, 57)
    private val green = Color.rgb(22, 245, 132)
    private val muted = Color.rgb(167, 185, 208)

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

        val header = TextView(this).apply {
            text = "MUSIC REMOTE"
            textSize = 24f
            letterSpacing = 0.06f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        screen.addView(header, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Y O U R   M U S I C   •   Y O U R   M O M E N T"
            textSize = 9f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }
        screen.addView(subtitle, matchWrap())

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
            LinearLayout.LayoutParams(dp(330), dp(330)).apply {
                gravity = Gravity.CENTER
            }
        )

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = 37f
            maxLines = 2
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(10), dp(2), 0)
        }
        screen.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = 26f
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
            LinearLayout.LayoutParams(0, dp(64), 1f).apply {
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
            LinearLayout.LayoutParams(0, dp(72), 1.15f).apply {
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
            LinearLayout.LayoutParams(0, dp(64), 1f).apply {
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
}
