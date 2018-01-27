package com.simples.j.whereami.tools

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.Polyline
import com.simples.j.whereami.R
import kotlinx.android.synthetic.main.drawer_list_item.view.*

/**
 * Created by j on 26/01/2018.
 *
 */
class DrawerListAdapter(private val list: ArrayList<Any>, private val context: Context): RecyclerView.Adapter<DrawerListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder{
        val view = LayoutInflater.from(parent.context).inflate(R.layout.drawer_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int { return list.size }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when(list[position]) {
            is Marker -> {
                holder.item_name.text = (list[position] as Marker).tag.toString()
                holder.item_icon.setImageDrawable(context.getDrawable(R.drawable.ic_action_markers))
            }
            is Polyline -> {
                holder.item_name.text = (list[position] as Polyline).tag.toString()
                holder.item_icon.setImageDrawable(context.getDrawable(R.drawable.ic_line))
            }
            is Polygon -> {
                holder.item_name.text = (list[position] as Polygon).tag.toString()
                holder.item_icon.setImageDrawable(context.getDrawable(R.drawable.ic_action_markers))
            }
        }
    }

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val item_name = view.drawer_item_name
        val item_icon = view.drawer_item_icon
    }

    companion object {
    }

}