package com.example.tv_j

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
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

    private lateinit var progressBarView: View
    private lateinit var progressBar: ProgressBar

    private var lastChannelKeyPressed: Int = 0

    private val testUrl: String = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        progressBarView = findViewById(R.id.progressBarView)
        progressBar = findViewById(R.id.progressBar)

        playerView = findViewById(R.id.playerView)
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(HandlePlayerEvents())
        exoPlayer.playWhenReady = true
        playerView.player = exoPlayer
        playerView.useController = false

        setupChannels()

        exoPlayer.prepare()

        println("EXO PREPARADO")
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.release()
    }

    inner class HandlePlayerEvents: Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            if (cause is HttpDataSourceException) {
                val httpError = cause
                if (httpError is InvalidResponseCodeException) {
                    println("### Error ${httpError.responseCode}: ${httpError.responseMessage} ###")
                } else {
                    println("### Error ${httpError.cause.toString()}: ${httpError.message} ###")
                }
            } else {
                println("### Error ${error.errorCode}: ${error.message} ###")
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    if (exoPlayer.playerError != null) {
                        Toast.makeText(this@MainActivity,
                            "Error ${exoPlayer.playerError!!.errorCode}: ${exoPlayer.playerError!!.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        changeChannel(lastChannelKeyPressed)
                        exoPlayer.prepare()
                    }
                }
            }
        }
    }

    private fun setupChannels() = runBlocking {
        println("ENTRO EN setupChannels()")
        val newItems: MutableList<MediaItem> = mutableListOf(
            MediaItem.Builder().setUri(Uri.parse(testUrl)).setMediaId("0").build()
        )
        println("------------- LISTA -------------")
        val iterate = Channels.List.listIterator()
        while (iterate.hasNext()) {
            val listItem: Channels.Companion.ListItem = iterate.next()
            if (listItem.url.contains("youtube") || listItem.url.contains("render-py-6goa")) {
                listItem.url = fetchStreamUrl(listItem.url)
                iterate.set(listItem)
            }
            println("(${listItem.number.toString()}) ${listItem.name} - ${listItem.url}")
            newItems.add(genMediaItem(listItem.number.toString(), listItem.url))
        }
        println("---------------------------------")
        exoPlayer.setMediaItems(newItems)
        println("SETEO LOS MediaItems")
        setLoading(false)
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
                    val regex = (if (url.contains("youtube"))
                            """(?<=hlsManifestUrl":")[^"]*\.m3u8""".toRegex()
                        else
                            """(?<=stream_url":")[^"]*\.m3u8""".toRegex())
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
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                Toast.makeText(this,
                    "KeyEvent.KEYCODE_DPAD_CENTER",
                    Toast.LENGTH_SHORT
                ).show()
                progressBar.visibility = (if (progressBar.visibility == View.VISIBLE)
                        View.GONE
                    else
                        View.VISIBLE)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                changeChannel(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                changeChannel(1)
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

    private fun changeChannel(direction: Int) {
        if (direction > 0) {
            if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
            else exoPlayer.seekToDefaultPosition(0)
        } else {
            if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem()
            else exoPlayer.seekToDefaultPosition(exoPlayer.mediaItemCount - 1)
        }
        lastChannelKeyPressed = direction
    }

    private fun adjustVolume(direction: Int) {
        audioManager.adjustVolume(
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun setLoading(loading: Boolean) {
        progressBarView.visibility = if (loading) View.VISIBLE else View.GONE
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

}
