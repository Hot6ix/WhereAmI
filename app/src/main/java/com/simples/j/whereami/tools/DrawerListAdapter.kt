package com.simples.j.whereami.tools

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.Polyline
import com.simples.j.whereami.R
import com.simples.j.whereami.style.LineStyle
import com.simples.j.whereami.style.MarkerStyle
import com.simples.j.whereami.style.PolygonStyle
import com.simples.j.whereami.style.StyleItem
import kotlinx.android.synthetic.main.drawer_header.view.*
import kotlinx.android.synthetic.main.drawer_list_item.view.*

/**
 * Created by j on 26/01/2018.
 *
 */

class DrawerListAdapter(private val itemList: ArrayList<KmlPlacemark>, private val styleList: ArrayList<StyleItem>, private val context: Context): RecyclerView.Adapter<DrawerListAdapter.ViewHolder>() {

    private lateinit var drawerItemClickListener: OnItemClickListener

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder{
        var view: View? = null
        when(viewType) {
            TYPE_HEADER -> {
                view = LayoutInflater.from(parent.context).inflate(R.layout.drawer_header, parent, false)
            }
            TYPE_CONTENT -> {
                view = LayoutInflater.from(parent.context).inflate(R.layout.drawer_list_item, parent, false)
            }
        }
        return ViewHolder(view!!, viewType)
    }

    override fun getItemViewType(position: Int): Int {
        return when(position) {
            TYPE_HEADER -> TYPE_HEADER
            else -> TYPE_CONTENT
        }
    }

    override fun getItemCount(): Int { return itemList.size + 1 }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        when(holder.type) {
            TYPE_CONTENT -> {
                val item = itemList[position - 1]

                try {
                    val style = styleList.single { it.id == item.styleUrl?.removePrefix("#") }

                    holder.itemName?.text = item.name
                    when(item.item) {
                        is Marker -> {
                            val styleItem = style.item as MarkerStyle
                            holder.itemIcon?.setImageDrawable(context.getDrawable(R.drawable.ic_marker_light))
                            holder.itemIcon?.setColorFilter(styleItem.color)
                        }
                        is Polyline -> {
                            val styleItem = style.item as LineStyle
                            holder.itemIcon?.setImageDrawable(context.getDrawable(R.drawable.ic_line_light))
                            holder.itemIcon?.setColorFilter(styleItem.color)
                        }
                        is Polygon -> {
                            val styleItem = style.item as PolygonStyle
                            holder.itemIcon?.setImageDrawable(context.getDrawable(R.drawable.ic_polygon_light))
                            holder.itemIcon?.setColorFilter(styleItem.color)
                        }
                    }

                    holder.itemView.setOnClickListener {
                        drawerItemClickListener.onDrawerItemClick(itemList[position - 1].item, holder.itemView)
                    }
                }
                catch (e: NoSuchElementException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.drawerItemClickListener = listener
    }

    inner class ViewHolder(view: View, viewType: Int): RecyclerView.ViewHolder(view) {
        var itemName: TextView? = null
        var itemIcon: ImageView? = null
        private var headerTitle: TextView? = null
        var type: Int = 999

        init {
            when(viewType) {
                TYPE_HEADER -> {
                    type = TYPE_HEADER
                    headerTitle = view.drawer_header_title
                }
                TYPE_CONTENT -> {
                    type = TYPE_CONTENT
                    itemName = view.drawer_item_name
                    itemIcon = view.drawer_item_icon
                }
            }
        }
    }

    interface OnItemClickListener {
        fun onDrawerItemClick(item: Any, view: View)
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CONTENT = 1
    }

}