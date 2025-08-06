package com.juanczsilva.tv_j

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.juanczsilva.tv_j.R.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@UnstableApi
class EditActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: CustomListAdapter
    private lateinit var numberField: EditText
    private lateinit var nameField: EditText
    private lateinit var urlField: EditText
    private lateinit var formatField: CheckBox
    private lateinit var formatMpdField: CheckBox

    private val customList: MutableList<Channels.Companion.ListItem> = mutableListOf()

    private enum class ChannelActionType { ADD, MOD, DEL }
    private val channelLastActionMap: MutableMap<Int, ChannelActionType> = mutableMapOf()
    private val urlsBackup: MutableMap<Int, String> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_edit)

        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        numberField = findViewById(id.number)
        nameField = findViewById(id.name)
        urlField = findViewById(id.url)
        formatField = findViewById(id.format)
        formatMpdField = findViewById(id.format_mpd)

        addButtonsEvents()
        loadCustomList()
        populateList()
    }

    private fun addButtonsEvents() {
        formatField.setOnCheckedChangeListener { _, checked ->
            if (checked) formatMpdField.isChecked = false
        }
        formatMpdField.setOnCheckedChangeListener { _, checked ->
            if (checked) formatField.isChecked = false
        }
        findViewById<Button>(id.btnAdd).setOnClickListener { _ ->
            val number: Int = (numberField.text.toString()).toInt()
            customList.add(
                Channels.Companion.ListItem(
                    number = number,
                    name = nameField.text.toString(),
                    url = urlField.text.toString().substringBefore('|'),
                    opts = parseOptsToString()
                )
            )
            customList.sortBy { it.number }
            adapter.notifyDataSetChanged()
            channelLastActionMap[number] = ChannelActionType.ADD
            alert("ADD: $number")
        }
        findViewById<Button>(id.btnMod).setOnClickListener { _ ->
            val number: Int = (numberField.text.toString()).toInt()
            val index: Int = customList.indexOfFirst { it.number == number }
            customList[index].name = nameField.text.toString()
            customList[index].url = urlField.text.toString().substringBefore('|')
            customList[index].opts = parseOptsToString()
            adapter.notifyDataSetChanged()
            channelLastActionMap[number] = ChannelActionType.MOD
            alert("MOD: $number")
        }
        findViewById<Button>(id.btnDel).setOnClickListener { _ ->
            val number: Int = (numberField.text.toString()).toInt()
            val index: Int = customList.indexOfFirst { it.number == number }
            customList.removeAt(index)
            adapter.notifyDataSetChanged()
            channelLastActionMap[number] = ChannelActionType.DEL
            alert("DEL: $number")
        }
        findViewById<Button>(id.btnOk).setOnClickListener { _ ->
            saveAndExit()
        }
    }

    private fun loadCustomList() {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val jsonList = sharedPreferences.getString("custom_list", null)
        if (jsonList != null) {
            val jsonArray = JSONArray(jsonList)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                customList.add(
                    Channels.Companion.ListItem(
                        number = jsonObject.getInt("number"),
                        name = jsonObject.getString("name"),
                        url = jsonObject.getString("url"),
                        opts = jsonObject.getString("opts")
                    )
                )
                urlsBackup[jsonObject.getInt("number")] = jsonObject.getString("url")
            }
        }
    }

    private fun populateList() {
        listView = findViewById(id.listView)
        adapter = CustomListAdapter(this@EditActivity, customList)
        listView.adapter = adapter
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val item = (listView.getItemAtPosition(position) as Channels.Companion.ListItem)
            numberField.setText(item.number.toString())
            nameField.setText(item.name)
            val parsedOpts = parseOptsToJSONObject(item.opts)
            val urlOpts: MutableList<String> = mutableListOf()
            if (parsedOpts.has("userAgent")) {
                urlOpts.add("User-Agent=" + parsedOpts.getString("userAgent"))
            }
            if (parsedOpts.has("key")) {
                urlOpts.add("key=" + parsedOpts.getString("key"))
            }
            if (parsedOpts.has("keyId")) {
                urlOpts.add("keyId=" + parsedOpts.getString("keyId"))
            }
            urlField.setText(
                if (urlOpts.size > 0) item.url + "|" + urlOpts.joinToString("&")
                else item.url
            )
            formatField.isChecked = (
                    parsedOpts.has("notM3u8") && parsedOpts.getBoolean("notM3u8")
                ).not()
            formatMpdField.isChecked = (
                    parsedOpts.has("isMpd") && parsedOpts.getBoolean("isMpd")
                )
        }
    }

    private fun saveAndExit() = runBlocking {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val jsonArrayCustom = JSONArray()
        for (item in customList) {
            val jsonObject = JSONObject()
            jsonObject.put("number", item.number)
            jsonObject.put("name", item.name)
            jsonObject.put("url", item.url)
            jsonObject.put("opts", item.opts)
            jsonArrayCustom.put(jsonObject)
        }
        editor.putString("custom_list", jsonArrayCustom.toString())
        channelLastActionMap.forEach { channelLastAction ->
            if (channelLastAction.value == ChannelActionType.ADD
                || channelLastAction.value == ChannelActionType.MOD) {
                val customItem: Channels.Companion.ListItem? = customList.find { it.number == channelLastAction.key }
                if (customItem != null) {
                    if (Channels.List.any { it.number == channelLastAction.key }) {
                        val index: Int = Channels.List.indexOfFirst { it.number == channelLastAction.key }
                        Channels.List[index].name = customItem.name
                        Channels.List[index].opts = customItem.opts
                        if (customItem.url != urlsBackup[channelLastAction.key]) {
                            Channels.List[index].url = fetchStreamUrl(customItem.url)
                        }
                    } else {
                        Channels.List.add(
                            Channels.Companion.ListItem(
                                number = customItem.number,
                                name = customItem.name,
                                opts = customItem.opts,
                                url = fetchStreamUrl(customItem.url)
                            )
                        )
                    }
                }
            } else if (channelLastAction.value == ChannelActionType.DEL) {
                if (Channels.List.any { it.number == channelLastAction.key }) {
                    val index: Int = Channels.List.indexOfFirst { it.number == channelLastAction.key }
                    Channels.List.removeAt(index)
                }
            }
        }
        Channels.List.sortBy { it.number }
        val jsonArrayCache = JSONArray()
        for (item in Channels.List) {
            val jsonObject = JSONObject()
            jsonObject.put("number", item.number)
            jsonObject.put("name", item.name)
            jsonObject.put("url", item.url)
            jsonObject.put("opts", item.opts)
            jsonArrayCache.put(jsonObject)
        }
        editor.putString("cached_list", jsonArrayCache.toString())
        editor.apply()

        val theIntent = Intent(this@EditActivity, MainActivity::class.java)
        theIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(theIntent)
        finish()
    }

    private suspend fun fetchStreamUrl(url: String): String {
        if (url.contains("youtube") || url.contains("twitch")) {
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
        return url
    }

    private fun alert(text: String) {
        Toast.makeText(this@EditActivity, text, Toast.LENGTH_LONG).show()
    }

    private fun parseOptsToString(): String {
        val optsParsedObject = JSONObject()
        val urlFieldText = urlField.text.toString()
        val delimiterIndex = urlFieldText.indexOf('|')
        if (delimiterIndex > -1) {
            val parametersPart = urlFieldText.substring(delimiterIndex + 1)
            val parameters = parametersPart.split("&")
            for (parameter in parameters) {
                val keyValue = parameter.split("=")
                if (keyValue.size == 2) {
                    if (keyValue[0].lowercase() == "User-Agent".lowercase()) {
                        optsParsedObject.put("userAgent", keyValue[1])
                    }
                    if (keyValue[0].lowercase() == "key".lowercase()) {
                        optsParsedObject.put("key", keyValue[1])
                    }
                    if (keyValue[0].lowercase() == "keyId".lowercase()) {
                        optsParsedObject.put("keyId", keyValue[1])
                    }
                }
            }
        }
        optsParsedObject.put("notM3u8", (formatField.isChecked).not())
        optsParsedObject.put("isMpd", (formatMpdField.isChecked))
        return optsParsedObject.toString()
    }

    private fun parseOptsToJSONObject(opts: String?): JSONObject {
        if ((opts.isNullOrEmpty()).not()) {
            return JSONObject(opts as String)
        }
        return JSONObject()
    }

}
