package com.simples.j.whereami

import android.content.Context
import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker

/**
 * Created by j on 04/01/2018.
 *
 */
class InfoWindow: GoogleMap.InfoWindowAdapter {

    private var context: Context
    private var inflater: LayoutInflater
    private var view: View

    constructor(context: Context) {
        this.context = context
        inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        view = inflater.inflate(R.layout.info_window, null)
    }

    override fun getInfoContents(marker: Marker?): View? {

        var textView = view.findViewById<TextView>(R.id.textView)
        textView.text = marker!!.title
        var textView2 = view.findViewById<TextView>(R.id.textView2)
        textView2.text = marker.snippet

        return view
    }

    override fun getInfoWindow(marker: Marker?): View? {

        return null
    }

}