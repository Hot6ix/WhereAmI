package com.simples.j.whereami.tools

import android.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.simples.j.whereami.style.LineStyle
import com.simples.j.whereami.style.MarkerStyle
import com.simples.j.whereami.style.PolygonStyle
import com.simples.j.whereami.style.StyleItem
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Created by james on 2018-01-22.
 *
 */

class KmlParser(private var input: InputStream) {

    private val itemList = ArrayList<KmlInfo>()
    private val styleList = ArrayList<StyleItem>()
    private var id = ""
    private var type = ""
    private var color = ""
    private var colorMode = ""
    private var scale = ""
    private var href = ""
    private var width = ""
    private var fill = ""
    private var fillColor = ""

    fun parseItem() {

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(input)

        val root = document.documentElement

        // Style
        val style = root.getElementsByTagName("Style")
        if(style.length != 0) {
            var index = 0
            while(index < style.length) {
                val child = style.item(index)

                empty()
                if(child.hasAttributes()) id = child.attributes.getNamedItem("id").nodeValue
                if(child.hasChildNodes()) getStyles(child.childNodes)
                when(type) {
                    TYPE_MARKER_STYLE -> {
                        styleList.add(StyleItem(id, MarkerStyle(id, Color.parseColor(color), colorMode, scale.toDouble(), href)))
                    }
                    TYPE_LINE_STYLE -> {
                        styleList.add(StyleItem(id, LineStyle(id, Color.parseColor(color), colorMode, width.toInt())))
                    }
                    TYPE_POLYGON_STYLE -> {
                        styleList.add(StyleItem(id, PolygonStyle(id, Color.parseColor(color), width = width.toInt(), fill = fill.toInt(), fillColor = Color.parseColor(fillColor))))
                    }
                }
                index++
            }
        }

        // Item
        val placemark = root.getElementsByTagName("Placemark")
        val coordinateTags = root.getElementsByTagName("coordinates")
        if(placemark.length != 0) {
            var index = 0
            while(index < placemark.length) { // Placemark list
                if(placemark.item(index).hasChildNodes()) {
                    val node = placemark.item(index)
                    val children = node.childNodes
                    var subIndex = 0

                    var name = ""
                    var description = ""
                    var styleUrl = ""
                    val pointList = ArrayList<LatLng>()
                    var type = ""
                    while(subIndex < children.length) { // items of Placemark
                        if(children.item(subIndex).nodeType != Node.TEXT_NODE) {
                            when(children.item(subIndex).nodeName) {
                                "name" -> {
                                    if(children.item(subIndex).firstChild != null) {
                                        name = children.item(subIndex).firstChild.nodeValue
                                    }
                                }
                                "description" -> {
                                    if(children.item(subIndex).firstChild != null) {
                                        description = children.item(subIndex).firstChild.nodeValue
                                    }
                                }
                                "styleUrl" -> {
                                    if(children.item(subIndex).firstChild != null) {
                                        styleUrl = children.item(subIndex).firstChild.nodeValue
                                    }
                                }
                                "Point" -> {
                                    type = children.item(subIndex).nodeName
                                    val split = coordinateTags.item(index).firstChild.nodeValue.split(",")
                                    pointList.add(LatLng(split[1].toDouble(), split[0].toDouble()))
                                }
                                "LineString", "Polygon" -> {
                                    type = children.item(subIndex).nodeName
                                    val split = coordinateTags.item(index).firstChild.nodeValue.split("\n")
                                    split.map {
                                        val subSplit = it.split(",")
                                        if(subSplit.size > 2) pointList.add(LatLng(subSplit[1].toDouble(), subSplit[0].toDouble()))
                                    }

                                }
                            }
                        }
                        subIndex++
                    }

                    itemList.add(KmlInfo(name, description, styleUrl, pointList, type))
                }
                index++
            }
        }
    }

    fun getItems(): ArrayList<KmlInfo> {
        return itemList
    }

    fun getStyles(): ArrayList<StyleItem> {
        return styleList
    }

    private fun getStyles(items: NodeList) {
        var index = 0
        while(index < items.length) {
            val item = items.item(index)
            when(item.nodeName) {
                "IconStyle" -> {
                    type = TYPE_MARKER_STYLE
                }
                "LineStyle" -> {
                    type = TYPE_LINE_STYLE
                }
                "PolyStyle" -> {
                    type = TYPE_POLYGON_STYLE
                }
                "color" -> { // Common
                    when(item.parentNode.nodeName) {
                        "IconStyle", "LineStyle" -> {
                            if(item.firstChild != null) color = item.firstChild.nodeValue
                        }
                        "PolyStyle" -> {
                            if(item.firstChild != null) fillColor = item.firstChild.nodeValue
                        }
                    }
                }
                "colorMode" -> { // Common
                    if(item.firstChild != null) colorMode = item.firstChild.nodeValue
                }
                "scale" -> { // Marker only
                    if(item.firstChild != null) scale = item.firstChild.nodeValue
                }
                "href" -> { // Marker only
                    if(item.firstChild != null) href = item.firstChild.nodeValue
                }
                "width" -> { // Line or Polygon
                    if(item.firstChild != null) width = item.firstChild.nodeValue
                }
                "fill" -> { // Polygon only
                    if(item.firstChild != null) fill = item.firstChild.nodeValue
                }
            }

            if(item.hasChildNodes()) getStyles(item.childNodes)
            index++
        }
    }

    private fun empty() {
        id = ""
        type = ""
        color = ""
        colorMode = ""
        scale = ""
        href = ""
        width = ""
        fill = ""
    }

    companion object {
        const val TYPE_MARKER_STYLE = "IconStyle"
        const val TYPE_LINE_STYLE = "LineStyle"
        const val TYPE_POLYGON_STYLE = "PolyStyle"
    }

}