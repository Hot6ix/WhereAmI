package com.simples.j.whereami.tools

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.simples.j.whereami.MapActivity
import com.simples.j.whereami.R
import com.simples.j.whereami.style.LineStyle
import com.simples.j.whereami.style.MarkerStyle
import com.simples.j.whereami.style.PolygonStyle
import com.simples.j.whereami.style.StyleItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Created by james on 2018-01-20.
 *
 */

class KmlManager(private var context: Context, private var googleMap: GoogleMap) {

    var itemList = ArrayList<KmlPlacemark>()
    var itemStyleList = ArrayList<StyleItem>()
    private var itemInfoList = ArrayList<KmlInfo>()
    var lineDistanceList = ArrayList<Marker>()
    var polygonAreaList = ArrayList<Marker>()

    fun checkStorageState(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    fun saveKmlToExternal(items: ArrayList<KmlPlacemark>, styles: ArrayList<StyleItem>) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if(!dir.exists()) dir.mkdirs()
        val file = File(dir, context.getString(R.string.save_file_name))
        val output = FileOutputStream(file)
        KmlSerializer(items, styles).serialize(output)
    }

    fun loadKmlFromExternal(): Boolean {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), context.getString(R.string.save_file_name))
        if(file.exists()) {
            val inputStream = FileInputStream(file)
            val kmlParser = KmlParser(inputStream)
            kmlParser.parseItem()
            itemInfoList = kmlParser.getItems()
            itemStyleList = kmlParser.getStyles()
            addItemsToMap()
            return true
        }
        return false
    }

    private fun addItemsToMap() {
        for(item in itemInfoList) {
            var styleItem: StyleItem? = null
            if(itemStyleList.isNotEmpty()) {
                try {
                    styleItem = itemStyleList.single { item.styleUrl?.removePrefix("#") == it.id }
                }
                catch (e: NoSuchElementException) {
                    styleItem = null
                }
            }
            when(item.type) {
                KmlPlacemark.TYPE_POINT -> {
                    if(styleItem == null) {
                        styleItem = Utils.getDefaultMarkerStyle(context)
                        itemStyleList.add(styleItem)
                    }
                    val style = styleItem.item as MarkerStyle
                    val marker = googleMap.addMarker(MarkerOptions()
                            .draggable(true)
                            .position(item.coordinates[0])
                            .icon(BitmapDescriptorFactory.fromBitmap(Utils.getDefaultMarker(style, context))))
                    itemList.add(KmlPlacemark(marker, item.name, item.description, item.styleUrl, item.coordinates, item.type))
                }
                KmlPlacemark.TYPE_LINE -> {
                    if(styleItem == null) {
                        styleItem = Utils.getDefaultLineStyle(context)
                        itemStyleList.add(styleItem)
                    }
                    val style = styleItem.item as LineStyle
                    val line = googleMap.addPolyline(PolylineOptions()
                            .color(style.color)
                            .width(style.width.toFloat())
                            .clickable(true)
                            .addAll(item.coordinates))
                    line.tag = item.name
                    itemList.add(KmlPlacemark(line, item.name, item.description, item.styleUrl, item.coordinates, item.type))

                    item.coordinates.mapIndexed { index, latLng ->
                        if(index+1 < item.coordinates.size) {
                            val distanceMarker = googleMap.addMarker(MarkerOptions()
                                    .position(Utils.getPointsBound(arrayListOf(latLng, item.coordinates[index+1])).center)
                                    .icon(BitmapDescriptorFactory.fromBitmap(Utils.getDistanceIcon(latLng, item.coordinates[index+1], context))))
                            distanceMarker.title = MapActivity.TAG_DISTANCE
                            distanceMarker.tag = line.id
                            lineDistanceList.add(distanceMarker)
                        }
                    }
                }
                KmlPlacemark.TYPE_POLYGON -> {
                    if(styleItem == null) {
                        styleItem = Utils.getDefaultPolygonStyle(context)
                        itemStyleList.add(styleItem)
                    }
                    val style = styleItem.item as PolygonStyle
                    val polygon = googleMap.addPolygon(PolygonOptions()
                            .fillColor(style.fillColor)
                            .strokeColor(style.color)
                            .strokeWidth(style.width.toFloat())
                            .clickable(true)
                            .addAll(item.coordinates))
                    itemList.add(KmlPlacemark(polygon, item.name, item.description, item.styleUrl, item.coordinates, item.type))

                    val areaMarker = googleMap.addMarker(MarkerOptions()
                            .position(Utils.getPointsBound(item.coordinates).center)
                            .icon(BitmapDescriptorFactory.fromBitmap(Utils.getAreaIcon(item.coordinates, context))))
                    areaMarker.title = MapActivity.TAG_AREA
                    areaMarker.tag = polygon.id
                    polygonAreaList.add(areaMarker)
                }
            }
        }
    }
}