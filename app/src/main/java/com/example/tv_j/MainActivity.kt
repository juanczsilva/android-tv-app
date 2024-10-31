package com.example.tv_j

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.Switch
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
import java.io.File
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
    private lateinit var volumeView: LinearLayout

    private var lastChannelKeyPressed: Int = 1
    private var lastChannelPosition: Int = 0
    private var isDpadCenterPressed = false
    private var isDpadCenterLongPress = false
    private var isBackPressed = false
    private var isBackLongPress = false
    private var isMenuPressed = false
    private var isMenuLongPress = false
    private val longPressTimeout = 2000L
    private val autoHideTimeout = 4000L
    private var loadedFromCache = false
    private var currentQuality: String = ""
    private var currentVolume: Float = 0.50f
    private val REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 1
    private var exportData: String = ""
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
            Toast.makeText(this, "Loading from cache...", Toast.LENGTH_LONG).show()
        }

        numberView = findViewById(R.id.currentNumber)
        volumeView = findViewById(R.id.volume_view)

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
                if (listItem.url.contains("youtube") || listItem.url.contains("twitch")) {
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
            var stream = ""
            try {
                val urlConnection = URL(url).openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                val responseCode = urlConnection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = urlConnection.inputStream
                    val data = inputStream.bufferedReader().use { it.readText() }

                    if (url.contains("youtube")) {
                        val regex = """(?<=hlsManifestUrl":")[^"]*\.m3u8""".toRegex()
                        val match = regex.find(data)
                        stream = match?.value ?: url
                    } else {
                        val jsonObject = JSONObject(data)
                        val urlsObject = jsonObject.getJSONObject("urls")
                        var lastKey: String? = null
                        val keys = urlsObject.keys()
                        while (keys.hasNext()) { lastKey = keys.next() }
                        val lastValue = lastKey?.let { urlsObject.getString(it) }
                        stream = lastValue ?: url
                    }

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
                    if (!isDpadCenterPressed) {
                        isDpadCenterPressed = true
                        looperHandler.postDelayed(dpadCenterLongPressRunnable, longPressTimeout)
                    }
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
                    if (findViewById<Button>(id.select_quality_button).hasFocus()) {
                        findViewById<Button>(id.clear_all_button).requestFocus()
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
                    if (findViewById<Button>(id.clear_all_button).hasFocus()) {
                        findViewById<Button>(id.select_quality_button).requestFocus()
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
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (isDpadCenterPressed) {
                    isDpadCenterPressed = false
                    looperHandler.removeCallbacks(dpadCenterLongPressRunnable)
                    if (isDpadCenterLongPress) {
                        isDpadCenterLongPress = false
                    } else {
                        onDpadCenterButtonShortPressed()
                    }
                }
                true
            }
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
        if (findViewById<Switch>(id.control_switch).isChecked) {
            showAndHideVolumeView()
            if (direction > 0) {
                if (exoPlayer.volume < 1.00f) {
                    val theNewVolume = (exoPlayer.volume + 0.01f)
                    currentVolume = theNewVolume
                    setCustomVolume(theNewVolume)
                }
            } else {
                if (exoPlayer.volume > 0.00f) {
                    val theNewVolume = (exoPlayer.volume - 0.01f)
                    currentVolume = theNewVolume
                    setCustomVolume(theNewVolume)
                }
            }
        } else {
            audioManager.adjustVolume(
                if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
    }

    private val dpadCenterLongPressRunnable = Runnable {
        if (isDpadCenterPressed) {
            isDpadCenterLongPress = true
            onMenuButtonShortPressed()
        }
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

    private fun onDpadCenterButtonShortPressed() {
        showAndHideListView()
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
            findViewById<Button>(id.select_quality_button).requestFocus()
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

    private val hideVolumeViewRunnable = Runnable {
        if (volumeView.visibility == View.VISIBLE) {
            volumeView.visibility = View.GONE
            onPlayerVolumeChange(currentVolume)
        }
    }

    private fun showAndHideVolumeView() {
        looperHandler.removeCallbacks(hideVolumeViewRunnable)
        if (volumeView.visibility != View.VISIBLE) volumeView.visibility = View.VISIBLE
        looperHandler.postDelayed(hideVolumeViewRunnable, autoHideTimeout)
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

        val isCustomVolume = getCurrentControlSwitch()
        if (isCustomVolume) { setCustomVolume(currentVolume) }
        findViewById<Switch>(id.control_switch).isChecked = isCustomVolume
        findViewById<Switch>(id.control_switch).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { setCustomVolume(currentVolume) } else { exoPlayer.volume = 1.00f }
            onControlSwitchChange(isChecked)
        }

        findViewById<Button>(id.select_quality_button).setOnClickListener {
            val qualityAlertOptions = arrayOf("1080p", "720p", "480p", "360p", "240p", "144p", "Auto")
            val preselectedOption = qualityAlertOptions.indexOf(currentQuality).takeIf { it >= 0 } ?: 0
            val qualityAlertBuilder = AlertDialog.Builder(this, style.CustomAlertDialogTheme)
            qualityAlertBuilder.setTitle("Quality:").setItems(qualityAlertOptions) { _, which ->
                onQualityChange(qualityAlertOptions[which])
            }
            val qualityAlertDialog = qualityAlertBuilder.create()
            qualityAlertDialog.setOnShowListener {
                val qualityAlertDialogListView = qualityAlertDialog.listView
                qualityAlertDialogListView.setItemChecked(preselectedOption, true)
                qualityAlertDialogListView.setSelection(preselectedOption)
                qualityAlertDialogListView.setSelector(drawable.list_item_selector)
                qualityAlertDialogListView.setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        val currentItemPosition = qualityAlertDialogListView.selectedItemPosition
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (currentItemPosition == 0) {
                                    qualityAlertDialogListView.setItemChecked(qualityAlertOptions.size - 1, true)
                                    qualityAlertDialogListView.setSelection(qualityAlertOptions.size - 1)
                                    true
                                } else {
                                    false
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (currentItemPosition == qualityAlertOptions.size - 1) {
                                    qualityAlertDialogListView.setItemChecked(0, true)
                                    qualityAlertDialogListView.setSelection(0)
                                    true
                                } else {
                                    false
                                }
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                }
            }
            qualityAlertDialog.show()
        }
        findViewById<Button>(id.select_quality_button).text = "Quality: " + currentQuality

        findViewById<Button>(id.import_export_button).setOnClickListener {
            val impExpDialogView = layoutInflater.inflate(R.layout.dialog_custom, null)
            val impExpDialogText = impExpDialogView.findViewById<EditText>(R.id.channels_data)
            val impExpDialogImpBtn = impExpDialogView.findViewById<Button>(R.id.import_button)
            val impExpDialogExpBtn = impExpDialogView.findViewById<Button>(R.id.export_button)
            val impExpAlertBuilder = AlertDialog.Builder(this,
                style.CustomAlertDialogTheme).setView(impExpDialogView)
            val impExpAlertDialog = impExpAlertBuilder.create()
            impExpAlertDialog.setOnShowListener {
                val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
                val jsonList = sharedPreferences.getString("custom_list", null)
                if (jsonList != null) {
                    val jsonArray = JSONArray(jsonList)
                    impExpDialogText.setText(jsonArray.toString(2))
                }
                impExpDialogImpBtn.setOnClickListener {
                    val currentChannelsData = impExpDialogText.text.toString()
                    val jsonArray = try { JSONArray(currentChannelsData) } catch (e: Exception) { null }
                    if (jsonArray != null) {
                        if (jsonArray.length() > 0) {
                            var jsonArrayHasError = false
                            for (i in 0 until jsonArray.length()) {
                                val jsonObject = jsonArray.getJSONObject(i)
                                val isValidNumber = (jsonObject.has("number") && jsonObject.getInt("number") >= 0)
                                val isValidName = (jsonObject.has("name") && jsonObject.getString("name").isNotEmpty())
                                val isValidUrl = (jsonObject.has("url") && jsonObject.getString("url").isNotEmpty())
                                if (!isValidNumber || !isValidName || !isValidUrl) jsonArrayHasError = true
                            }
                            if (!jsonArrayHasError) {
                                clearCachedList()
                                val editor = sharedPreferences.edit()
                                editor.putString("custom_list", jsonArray.toString())
                                editor.apply()
                                restartApp()
                            } else {
                                Toast.makeText(this@MainActivity, "Error: data error", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Error: no data", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Error: format error", Toast.LENGTH_LONG).show()
                    }
                }
                impExpDialogExpBtn.setOnClickListener {
                    exportData = impExpDialogText.text.toString()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Para Android 10 y versiones posteriores
                        saveFileToExternalStorageNew()
                    } else {
                        // Para Android 9 y versiones anteriores
                        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_EXTERNAL_STORAGE)
                        } else {
                            saveFileToExternalStorageOld()
                        }
                    }
                }
            }
            impExpAlertDialog.show()
        }

        findViewById<Button>(id.rebuild_cache_button).setOnClickListener {
            clearCachedList()
            restartApp()
        }

        findViewById<Button>(id.clear_all_button).setOnClickListener {
            clearCustomList()
            clearCachedList()
            restartApp()
        }
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
                "1080p" -> {
                    maxVideoWidth = 1920
                    maxVideoHeight = 1080
                    currentQuality = "1080p"
                }
                "720p" -> {
                    maxVideoWidth = 1280
                    maxVideoHeight = 720
                    currentQuality = "720p"
                }
                "480p" -> {
                    maxVideoWidth = 854
                    maxVideoHeight = 480
                    currentQuality = "480p"
                }
                "360p" -> {
                    maxVideoWidth = 640
                    maxVideoHeight = 360
                    currentQuality = "360p"
                }
                "240p" -> {
                    maxVideoWidth = 426
                    maxVideoHeight = 240
                    currentQuality = "240p"
                }
                "144p" -> {
                    maxVideoWidth = 256
                    maxVideoHeight = 144
                    currentQuality = "144p"
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

    private fun getCurrentControlSwitch(): Boolean {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val controlSwitch = sharedPreferences.getBoolean("controlSwitch", false)
        currentVolume = sharedPreferences.getFloat("cachedVolume", 0.50f)
        return controlSwitch
    }

    private fun onControlSwitchChange(value: Boolean) {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("controlSwitch", value)
        editor.apply()
    }

    private fun onPlayerVolumeChange(value: Float) {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putFloat("cachedVolume", value)
        editor.apply()
    }

    private fun setCustomVolume(value: Float) {
        exoPlayer.volume = value
        findViewById<TextView>(id.volume_percent).text = (value * 100).toInt().toString()
        findViewById<ProgressBar>(id.volume_bar).progress = (value * 100).toInt()
    }

    private fun restartApp() {
        Channels.reset()
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun clearCachedList() {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("cached_list")
        editor.apply()
    }

    private fun clearCustomList() {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("custom_list")
        editor.apply()
    }

    private fun saveFileToExternalStorageNew() {
        val fileName = "TV-J_ChannelsData.txt"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }
        val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(exportData.toByteArray())
                Toast.makeText(this@MainActivity, "File saved in \"Documents/$fileName\"", Toast.LENGTH_LONG).show()
            } ?: Toast.makeText(this@MainActivity, "Error: file not saved", Toast.LENGTH_LONG).show()
        } ?: Toast.makeText(this@MainActivity, "Error: file not saved", Toast.LENGTH_LONG).show()
    }

    private fun saveFileToExternalStorageOld() {
        val fileName = "TV-J_ChannelsData.txt"
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDir.exists()) documentsDir.mkdirs()
        val file = File(documentsDir, fileName)
        try {
            file.writeText(exportData)
            Toast.makeText(this@MainActivity, "File saved in \"Documents/$fileName\"", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Error: file not saved", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                saveFileToExternalStorageOld()
            } else {
                Toast.makeText(this@MainActivity, "Error: access denied", Toast.LENGTH_LONG).show()
            }
        }
    }

}
