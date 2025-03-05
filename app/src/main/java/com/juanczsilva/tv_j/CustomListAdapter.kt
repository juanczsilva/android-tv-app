package com.juanczsilva.tv_j

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class CustomListAdapter(context: Context, private val dataSource: List<Channels.Companion.ListItem>) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int {
        return dataSource.size
    }

    override fun getItem(position: Int): Channels.Companion.ListItem {
        return dataSource[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = convertView ?: inflater.inflate(R.layout.list_item, parent, false)

        val itemTextNumber: TextView = view.findViewById(R.id.item_text_number)
        val itemTextName: TextView = view.findViewById(R.id.item_text_name)

        val item = getItem(position)

        itemTextNumber.text = item.number.toString()
        itemTextName.text = item.name

        return view
    }
}