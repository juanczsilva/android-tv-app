package com.example.tv_j

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tv_j.R.*
import org.json.JSONArray
import org.json.JSONObject

class EditActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var adapter: CustomListAdapter
    private lateinit var numberField: EditText
    private lateinit var nameField: EditText
    private lateinit var urlField: EditText
    private lateinit var optsField: EditText

    private val customList: MutableList<Channels.Companion.ListItem> = mutableListOf()

    private enum class ChannelActionType { ADD, MOD, DEL }
    private data class ChannelLastAction(var number: Int, var action: ChannelActionType)
    private val channelLastActionList: MutableList<ChannelLastAction> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_edit)

        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        numberField = findViewById(id.number)
        nameField = findViewById(id.name)
        urlField = findViewById(id.url)
        optsField = findViewById(id.opts)

        addBtnsEvts()
        loadCustomList()
        populateList()
    }

    private fun addBtnsEvts() {
        findViewById<Button>(id.btnAdd).setOnClickListener { _ ->
            val number: Int = (numberField.text.toString()).toInt()
            customList.add(
                Channels.Companion.ListItem(
                    number = number,
                    name = nameField.text.toString(),
                    url = urlField.text.toString(),
                    opts = optsField.text.toString()
                )
            )
            customList.sortBy { it.number }
            adapter.notifyDataSetChanged()
            alert("ADD: $number")
        }
        findViewById<Button>(id.btnMod).setOnClickListener { _ ->
            val number: Int = (numberField.text.toString()).toInt()
            val index: Int = customList.indexOfFirst { it.number == number }
            customList[index].name = nameField.text.toString()
            customList[index].url = urlField.text.toString()
            customList[index].opts = optsField.text.toString()
            adapter.notifyDataSetChanged()
            alert("MOD: $number")
        }
        findViewById<Button>(id.btnDel).setOnClickListener { _ ->
            val number: Int = (numberField.text.toString()).toInt()
            val index: Int = customList.indexOfFirst { it.number == number }
            customList.removeAt(index)
            adapter.notifyDataSetChanged()
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
            urlField.setText(item.url)
            optsField.setText(item.opts)
        }
    }

    private fun saveAndExit() {
        val sharedPreferences = getSharedPreferences("cache", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val jsonArray = JSONArray()
        for (item in customList) {
            val jsonObject = JSONObject()
            jsonObject.put("number", item.number)
            jsonObject.put("name", item.name)
            jsonObject.put("url", item.url)
            jsonObject.put("opts", item.opts)
            jsonArray.put(jsonObject)
        }
        editor.putString("custom_list", jsonArray.toString())
        editor.remove("cached_list")
        editor.apply()

        val theIntent = Intent(this@EditActivity, MainActivity::class.java)
        theIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(theIntent)
        finish()
    }

    private fun alert(text: String) {
        Toast.makeText(this@EditActivity, text, Toast.LENGTH_LONG).show()
    }

}
