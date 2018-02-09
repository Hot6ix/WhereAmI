package com.simples.j.whereami.tools

import android.support.constraint.ConstraintLayout
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.simples.j.whereami.R
import kotlinx.android.synthetic.main.color_list_item.view.*

/**
 * Created by j on 07/02/2018.
 *
 */
class DetailColorListAdapter(private val list:IntArray, private val defaultColor: Int): RecyclerView.Adapter<DetailColorListAdapter.ViewHolder>() {

    private lateinit var colorItemClickListener: OnItemClickListener
    private var lastChecked: Button? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.color_list_item, parent, false))
    }

    override fun getItemCount(): Int { return list.size }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if(list[position] == defaultColor) {
            lastChecked = holder.button
            holder.button.isSelected = true
        }

        holder.layout.setBackgroundColor(list[position])
        holder.button.setOnClickListener {
            colorItemClickListener.onColorItemClick(list[position], holder.itemView)
            if(lastChecked != null) {
                if(lastChecked != holder.button) {
                    lastChecked?.isSelected = false
                }
            }
            lastChecked = holder.button
            holder.button.isSelected = true
        }
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.colorItemClickListener = listener
    }

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        var layout: ConstraintLayout = view.color_item_layout
        var button: Button = view.color_item
    }

    interface OnItemClickListener {
        fun onColorItemClick(color: Int, view: View)
    }
}