package com.example.tv_j

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var audioManager: AudioManager

    private val testUrl: String = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        playerView = findViewById(R.id.playerView)
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer

        setupChannels()

        playerView.useController = false
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        println("EXO PREPARADO")
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.release()
    }

    private fun setupChannels() = runBlocking {
        println("ENTRO EN setupChannels()")

        val newItems: List<MediaItem> = listOf(
            MediaItem.Builder().setUri(Uri.parse(testUrl)).setMediaId("0").build()
        )

        val httpUrlTest: String = fetchStreamUrl("https://www.youtube.com/@TVPublicaArgentina/live")
        println("OBTUVO httpUrlTest: $httpUrlTest")
//        newItems.

        exoPlayer.setMediaItems(newItems)
        println("SETEO LOS MediaItems")
    }

    private fun genMediaItem(id: String, url: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(url))
            .setMimeType(MimeTypes.APPLICATION_M3U8).build()
    }

    suspend fun fetchStreamUrl(url: String): String {
        return withContext(Dispatchers.IO) {
            var stream: String = ""
            try {
                val urlConnection = URL(url).openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = urlConnection.inputStream
                    val data = inputStream.bufferedReader().use { it.readText() }
                    val regex = """(?<=hlsManifestUrl":")[^"]*\.m3u8""".toRegex()
                    val match = regex.find(data)
                    stream = match?.value ?: url
                    inputStream.close()
                } else {
                    println("Request failed with response code $responseCode")
                }
                urlConnection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stream
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem()
                else exoPlayer.seekToDefaultPosition(exoPlayer.mediaItemCount - 1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
                else exoPlayer.seekToDefaultPosition(0)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                adjustVolume(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                adjustVolume(1)
                true
            }
            else -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }

    private fun adjustVolume(direction: Int) {
        audioManager.adjustVolume(
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

}
