package com.example.tv_j

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.example.tv_j.R.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var trackSelector: DefaultTrackSelector
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var audioManager: AudioManager
    private lateinit var listView: ListView
    private lateinit var numberView: TextView
    private lateinit var menuView: LinearLayout

    private var lastChannelKeyPressed: Int = 0
    private var lastChannelPosition: Int = 0
    private var isBackPressed = false
    private var isBackLongPress = false
    private var isMenuPressed = false
    private var isMenuLongPress = false
    private val longPressTimeout = 2000L
    private val autoHideTimeout = 4000L
    private var loadedFromCache = false
    private var currentQuality: String = ""
    private val looperHandler = Handler(Looper.getMainLooper())

//    private val testUrl: String =
//        "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_main)

        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        initPlayer()
        loadCustomOrDefault()
        setupChannels()
        populateList()
        handleMenu()

        if (loadedFromCache) {
            Toast.makeText(this, "Cache...", Toast.LENGTH_LONG).show()
        }

        numberView = findViewById(R.id.currentNumber)

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
                if (cause is InvalidResponseCodeException) {
                    println("### Error ${cause.responseCode}: ${cause.responseMessage} ###")
                } else {
                    println("### Error ${cause.cause.toString()}: ${cause.message} ###")
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

    private fun initPlayer() {
        val (w, h) = getQualityByName(getCurrentCachedQuality())
        playerView = findViewById(id.playerView)
        trackSelector = DefaultTrackSelector(this)
        if (w > 0 && h > 0) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setMaxVideoSize(w, h)
                .build()
        }
        exoPlayer = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        exoPlayer.addListener(HandlePlayerEvents())
        exoPlayer.playWhenReady = true
        playerView.player = exoPlayer
        playerView.useController = false
    }

    private fun loadCustomOrDefault() {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val jsonList = sharedPreferences.getString("custom_list", null)
        if (jsonList != null) {
            val jsonArray = JSONArray(jsonList)
            Channels.List.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                Channels.List.add(
                    Channels.Companion.ListItem(
                        number = jsonObject.getInt("number"),
                        name = jsonObject.getString("name"),
                        url = jsonObject.getString("url"),
                        opts = jsonObject.getString("opts")
                    )
                )
            }
        } else {
            val editor = sharedPreferences.edit()
            val jsonArray = JSONArray()
            for (item in Channels.List) {
                val jsonObject = JSONObject()
                jsonObject.put("number", item.number)
                jsonObject.put("name", item.name)
                jsonObject.put("url", item.url)
                jsonObject.put("opts", item.opts)
                jsonArray.put(jsonObject)
            }
            editor.putString("custom_list", jsonArray.toString())
            editor.apply()
        }
    }

    private fun setupChannels() = runBlocking {
        println("ENTRO EN setupChannels()")
        val mediaItems: MutableList<MediaItem> = mutableListOf(
//            MediaItem.Builder().setUri(Uri.parse(testUrl)).setMediaId("0").build()
        )
        if (loadListFromCache()) {
            Channels.List.forEach { listItem ->
                mediaItems.add(genMediaItem(listItem.number.toString(), listItem.url, listItem.opts))
            }
            loadedFromCache = true
        } else {
            println("------------- LISTA -------------")
            val iterate = Channels.List.listIterator()
            while (iterate.hasNext()) {
                val listItem: Channels.Companion.ListItem = iterate.next()
                if (listItem.url.contains("youtube") || listItem.url.contains("render-py-6goa")) {
                    listItem.url = fetchStreamUrl(listItem.url)
                    iterate.set(listItem)
                }
                println("(${listItem.number.toString()}) ${listItem.name} - ${listItem.url}")
                mediaItems.add(genMediaItem(listItem.number.toString(), listItem.url, listItem.opts))
            }
            saveListToCache()
            println("---------------------------------")
        }
        exoPlayer.setMediaItems(mediaItems)
        println("SETEO LOS MediaItems")
    }

    private fun genMediaItem(id: String, url: String, opts: String?): MediaItem {
        if (opts != null && !(opts.isEmpty())) {
            if (JSONObject(opts).getBoolean("notM3u8")) {
                return MediaItem.Builder()
                    .setMediaId(id)
                    .setUri(Uri.parse(url)).build()
            }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(url))
            .setMimeType(MimeTypes.APPLICATION_M3U8).build()
    }

    private suspend fun fetchStreamUrl(url: String): String {
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

    private fun populateList() {
        listView = findViewById(R.id.listView)
        val adapter = CustomListAdapter(this, Channels.List)
        listView.adapter = adapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val currentChannelPosition = exoPlayer.currentMediaItemIndex
            if (position != currentChannelPosition) {
                exoPlayer.seekToDefaultPosition(position)
                lastChannelPosition = currentChannelPosition
            } else {
                listView.visibility = View.GONE
            }
            showAndHideNumberView(
                (listView.getItemAtPosition(position) as Channels.Companion.ListItem).number)
        }
        listView.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val offset = (listView.height / 2) - (view.height / 2)
                listView.setSelectionFromTop(position, offset)
            }
            override fun onNothingSelected(parent: AdapterView<*>) { }
        }
        listView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val currentItemPosition = listView.selectedItemPosition
                val lastListItemPosition = listView.count - 1
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER -> {
                        showAndHideListView()
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        showAndHideListView()
                        lastChannelKeyPressed = -1
                        if (currentItemPosition == 0) {
                            listView.setSelection(lastListItemPosition)
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        showAndHideListView()
                        lastChannelKeyPressed = 1
                        if (currentItemPosition == lastListItemPosition) {
                            listView.setSelection(0)
                            return@setOnKeyListener true
                        }
                    }
                }
            }
            false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (listView.visibility != View.VISIBLE && menuView.visibility != View.VISIBLE) {
                    showAndHideListView()
                    return@onKeyDown true
                }
                false
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!isBackPressed) {
                    isBackPressed = true
                    looperHandler.postDelayed(backLongPressRunnable, longPressTimeout)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (listView.visibility != View.VISIBLE && menuView.visibility != View.VISIBLE) {
                    changeChannel(-1)
                    return@onKeyDown true
                } else if (menuView.visibility == View.VISIBLE) {
                    if (findViewById<Button>(id.go_edit_button).hasFocus()) {
                        findViewById<Button>(id.select_quality_button).requestFocus()
                        return@onKeyDown true
                    }
                }
                false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (listView.visibility != View.VISIBLE && menuView.visibility != View.VISIBLE) {
                    changeChannel(1)
                    return@onKeyDown true
                } else if (menuView.visibility == View.VISIBLE) {
                    if (findViewById<Button>(id.select_quality_button).hasFocus()) {
                        findViewById<Button>(id.go_edit_button).requestFocus()
                        return@onKeyDown true
                    }
                }
                false
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                adjustVolume(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                adjustVolume(1)
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (!isMenuPressed) {
                    isMenuPressed = true
                    looperHandler.postDelayed(menuLongPressRunnable, longPressTimeout)
                }
                true
            }
            else -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (isBackPressed) {
                    isBackPressed = false
                    looperHandler.removeCallbacks(backLongPressRunnable)
                    if (isBackLongPress) {
                        isBackLongPress = false
                    } else {
                        onBackButtonShortPressed()
                    }
                }
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (isMenuPressed) {
                    isMenuPressed = false
                    looperHandler.removeCallbacks(menuLongPressRunnable)
                    if (isMenuLongPress) {
                        isMenuLongPress = false
                    } else {
                        onMenuButtonShortPressed()
                    }
                }
                true
            }
            else -> {
                super.onKeyUp(keyCode, event)
            }
        }
    }

    private fun changeChannel(direction: Int) {
        lastChannelPosition = exoPlayer.currentMediaItemIndex
        var changedChannelPosition: Int
        if (direction > 0) {
            if (exoPlayer.hasNextMediaItem()) {
                changedChannelPosition = exoPlayer.nextMediaItemIndex
                exoPlayer.seekToNextMediaItem()
            } else {
                changedChannelPosition = 0
                exoPlayer.seekToDefaultPosition(0)
            }
        } else {
            if (exoPlayer.hasPreviousMediaItem()) {
                changedChannelPosition = exoPlayer.previousMediaItemIndex
                exoPlayer.seekToPreviousMediaItem()
            } else {
                changedChannelPosition = (exoPlayer.mediaItemCount - 1)
                exoPlayer.seekToDefaultPosition(exoPlayer.mediaItemCount - 1)
            }
        }
        listView.setSelection(changedChannelPosition)
        lastChannelKeyPressed = direction
        showAndHideNumberView(
            (listView.getItemAtPosition(changedChannelPosition) as Channels.Companion.ListItem).number)
    }

    private fun adjustVolume(direction: Int) {
        audioManager.adjustVolume(
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private val backLongPressRunnable = Runnable {
        if (isBackPressed) {
            isBackLongPress = true
            finishAffinity()
        }
    }

    private val menuLongPressRunnable = Runnable {
        if (isMenuPressed) {
            isMenuLongPress = true
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun onBackButtonShortPressed() {
//        println("Botón de volver atrás presionado y liberado rápidamente")
        if (menuView.visibility == View.VISIBLE) {
            menuView.visibility = View.GONE
        } else if (listView.visibility == View.VISIBLE) {
            listView.visibility = View.GONE
            if (listView.selectedItemPosition != exoPlayer.currentMediaItemIndex) {
                listView.setSelection(exoPlayer.currentMediaItemIndex)
            }
        } else {
            val currentChannelPosition = exoPlayer.currentMediaItemIndex
            if (currentChannelPosition != lastChannelPosition) {
                exoPlayer.seekToDefaultPosition(lastChannelPosition)
                listView.setSelection(lastChannelPosition)
                showAndHideNumberView(
                    (listView.getItemAtPosition(lastChannelPosition) as Channels.Companion.ListItem).number)
                lastChannelPosition = currentChannelPosition
            }
        }
    }

    private fun onMenuButtonShortPressed() {
//        println("Botón de menu presionado y liberado rápidamente (adb shell input keyevent 82)")
        if (menuView.visibility == View.VISIBLE) {
            menuView.visibility = View.GONE
        } else {
            findViewById<Button>(id.go_edit_button).requestFocus()
            menuView.visibility = View.VISIBLE
        }
    }

    private val hideNumberViewRunnable = Runnable {
        numberView.visibility = View.GONE
    }

    private fun showAndHideNumberView(channelNumber: Int) {
        looperHandler.removeCallbacks(hideNumberViewRunnable)
        numberView.text = channelNumber.toString()
        numberView.visibility = View.VISIBLE
        looperHandler.postDelayed(hideNumberViewRunnable, autoHideTimeout)
    }

    private val hideListViewRunnable = Runnable {
        if (listView.visibility == View.VISIBLE) {
            listView.visibility = View.GONE
            if (listView.selectedItemPosition != exoPlayer.currentMediaItemIndex) {
                listView.setSelection(exoPlayer.currentMediaItemIndex)
            }
        }
    }

    private fun showAndHideListView() {
        looperHandler.removeCallbacks(hideListViewRunnable)
        if (listView.visibility != View.VISIBLE) listView.visibility = View.VISIBLE
        looperHandler.postDelayed(hideListViewRunnable, autoHideTimeout * 2)
    }

    private fun saveListToCache() {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val jsonArray = JSONArray()
        for (item in Channels.List) {
            val jsonObject = JSONObject()
            jsonObject.put("number", item.number)
            jsonObject.put("name", item.name)
            jsonObject.put("url", item.url)
            jsonObject.put("opts", item.opts)
            jsonArray.put(jsonObject)
        }
        editor.putString("cached_list", jsonArray.toString())
        editor.putLong("timestamp", System.currentTimeMillis())
        editor.apply()
    }

    private fun loadListFromCache(): Boolean {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val timestamp = sharedPreferences.getLong("timestamp", 0L)
        if (System.currentTimeMillis() - timestamp > 6 * 60 * 60 * 1000) {
            return false
        }
        val jsonList = sharedPreferences.getString("cached_list", null) ?: return false
        val jsonArray = JSONArray(jsonList)
        Channels.List.clear()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            Channels.List.add(Channels.Companion.ListItem(
                number = jsonObject.getInt("number"),
                name = jsonObject.getString("name"),
                url = jsonObject.getString("url"),
                opts = jsonObject.getString("opts")
            ))
        }
        return true
    }

    private fun handleMenu() {
        menuView = findViewById(id.right_menu)
        findViewById<Button>(id.go_edit_button).setOnClickListener {
            menuView.visibility = View.GONE
            startActivity(Intent(this@MainActivity, EditActivity::class.java))
        }
        findViewById<Button>(id.select_quality_button).setOnClickListener {
            val qualityAlertOptions = arrayOf("360p", "Auto")
            val qualityAlertBuilder = AlertDialog.Builder(this, style.CustomAlertDialogTheme)
            qualityAlertBuilder.setTitle("Quality:").setItems(qualityAlertOptions) { _, which ->
                onQualityChange(qualityAlertOptions[which])
            }
            val qualityAlertDialog = qualityAlertBuilder.create()
            val listView = qualityAlertDialog.listView
            listView.setSelector(drawable.list_item_selector)
            qualityAlertDialog.show()
        }
        findViewById<Button>(id.select_quality_button).text = "Quality: " + currentQuality
    }

    private fun getCurrentCachedQuality(): String? {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val cachedQuality = sharedPreferences.getString("cachedQuality", null)
        return cachedQuality
    }

    private fun getQualityByName(qualityName: String?): Pair<Int, Int> {
        var maxVideoWidth: Int = 0
        var maxVideoHeight: Int = 0
        if (qualityName != null && !qualityName.isEmpty()) {
            when (qualityName) {
                "360p" -> {
                    maxVideoWidth = 640
                    maxVideoHeight = 360
                    currentQuality = "360p"
                }
                "Auto" -> {
                    currentQuality = "Auto"
                }
            }
        } else {
            currentQuality = "Auto"
        }
        return Pair(maxVideoWidth, maxVideoHeight)
    }

    private fun onQualityChange(theNewQuality: String) {
        var (w, h) = getQualityByName(theNewQuality)
        if (w > 0 && h > 0) {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .setMaxVideoSize(w, h)
                .build()
        } else {
            trackSelector.parameters = trackSelector.buildUponParameters()
                .clearVideoSizeConstraints()
                .build()
        }
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("cachedQuality", theNewQuality)
        editor.apply()
        findViewById<Button>(id.select_quality_button).text = "Quality: " + currentQuality
    }

}
