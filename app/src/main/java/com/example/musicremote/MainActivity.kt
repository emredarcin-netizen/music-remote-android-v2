
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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshMedia()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 19, 28)
        window.navigationBarColor = Color.rgb(15, 19, 28)
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
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(18), dp(22), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(22, 29, 43),
                    Color.rgb(11, 15, 24),
                    Color.rgb(19, 24, 36)
                )
            )
        }

        setContentView(
            root,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        )

        val heading = TextView(this).apply {
            text = "MUSIC REMOTE"
            textSize = 19f
            letterSpacing = 0.16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(heading, matchWrap())

        val subtitle = TextView(this).apply {
            text = "YOUR MUSIC • YOUR MOMENT"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(135, 151, 174))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }
        root.addView(subtitle, matchWrap())

        addFlexibleSpace(0.55f)

        val coverCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = rounded(
                Color.rgb(38, 47, 64), 22
            )
            elevation = dp(8).toFloat()
        }

        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.rgb(42, 49, 63))
            setImageResource(android.R.drawable.ic_media_play)
            contentDescription = "Albüm kapağı"
            clipToOutline = true
            background = rounded(Color.rgb(42, 49, 63), 18)
        }

        coverCard.addView(
            art,
            LinearLayout.LayoutParams(dp(166), dp(166))
        )

        root.addView(
            coverCard,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        addSpace(dp(22))

        title = TextView(this).apply {
            text = "Henüz müzik algılanmadı"
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(4), 0, dp(4), 0)
        }
        root.addView(title, matchWrap())

        artist = TextView(this).apply {
            text = "Spotify veya YouTube Music'i başlatın"
            textSize = 15f
            setTextColor(Color.rgb(171, 185, 205))
            gravity = Gravity.CENTER
            maxLines = 2
            setPadding(dp(4), dp(7), dp(4), 0)
        }
        root.addView(artist, matchWrap())

        addSpace(dp(12))

        status = TextView(this).apply {
            text = "●  MÜZİK BEKLENİYOR"
            textSize = 11f
            letterSpacing = 0.04f
            setTextColor(Color.rgb(68, 231, 132))
            gravity = Gravity.CENTER
        }
        root.addView(status, matchWrap())

        addFlexibleSpace(0.8f)

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        root.addView(controls, matchWrap())

        controls.addView(
            controlButton("⏮", "Önceki parça", false) {
                selectedController?.transportControls?.skipToPrevious()
                handler.postDelayed({ refreshMedia() }, 400)
            }
        )

        controls.addView(
            controlButton("▶", "Oynat / duraklat", true) {
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
                handler.postDelayed({ refreshMedia() }, 400)
            }
        )

        controls.addView(
            controlButton("⏭", "Sonraki parça", false) {
                selectedController?.transportControls?.skipToNext()
                handler.postDelayed({ refreshMedia() }, 400)
            }
        )

        addSpace(dp(16))

        permissionButton = Button(this).apply {
            text = "MEDYA ERİŞİMİNİ ETKİNLEŞTİR"
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(39, 48, 65), 14)
            setPadding(dp(12), dp(5), dp(12), dp(5))
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
                dp(48)
            )
        )

        addSpace(dp(8))

        val footer = TextView(this).apply {
            text = "MADE FOR YOUR DRIVE"
            textSize = 9f
            letterSpacing = 0.14f
            setTextColor(Color.rgb(105, 120, 143))
            gravity = Gravity.CENTER
        }
        root.addView(footer, matchWrap())

        addFlexibleSpace(0.25f)
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
            status.text = "●  MEDYA ERİŞİM İZNİ GEREKLİ"
            return
        } catch (_: Exception) {
            status.text = "●  MEDYA OTURUMUNA ERİŞİLEMİYOR"
            return
        }

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
            permissionButton.visibility = View.VISIBLE
            status.text = "●  SON ÇALAN PARÇA KAYITLI"
            return
        }

        permissionButton.visibility = View.GONE

        val metadata = controller.metadata
        val track =
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()

        val performer =
            metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                )
                ?: ""

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
            }
        }

        val playing =
            controller.playbackState?.state ==
                android.media.session.PlaybackState.STATE_PLAYING

        status.text = if (playing) {
            "●  ŞİMDİ ÇALIYOR"
        } else {
            "●  DURAKLATILDI"
        }
    }

    private fun saveCover(bitmap: Bitmap) {
        try {
            val file = File(filesDir, "last_album_cover.png")
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        } catch (_: Exception) {
            // Kayıt başarısız olursa uygulama çalışmaya devam eder.
        }
    }

    private fun restoreLastTrack() {
        val savedTitle = prefs.getString("last_title", null)
        val savedArtist = prefs.getString("last_artist", null)

        if (!savedTitle.isNullOrBlank()) {
            title.text = savedTitle
            artist.text = savedArtist ?: ""
            status.text = "●  SON ÇALAN PARÇA KAYITLI"
        }

        try {
            val file = File(filesDir, "last_album_cover.png")
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) art.setImageBitmap(bitmap)
            }
        } catch (_: Exception) {
            // Kayıtlı görsel okunamazsa varsayılan kapak gösterilir.
        }
    }

    private fun controlButton(
        symbol: String,
        description: String,
        primary: Boolean,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = symbol
            contentDescription = description
            textSize = if (primary) 25f else 21f
            isAllCaps = false
            setTextColor(
                if (primary) Color.rgb(12, 25, 19) else Color.WHITE
            )
            background = rounded(
                if (primary) Color.rgb(69, 235, 133)
                else Color.rgb(39, 48, 65),
                if (primary) 30 else 20
            )
            elevation = if (primary) dp(5).toFloat() else 0f
            setOnClickListener { action() }

            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(if (primary) 62 else 54),
                if (primary) 1.15f else 1f
            ).apply {
                setMargins(dp(5), 0, dp(5), 0)
            }
        }
    }

    private fun addSpace(height: Int) {
        root.addView(
            View(this),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height
            )
        )
    }

    private fun addFlexibleSpace(weight: Float) {
        root.addView(
            View(this),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                weight
            )
        )
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
