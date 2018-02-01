package com.simples.j.whereami.tools

import com.google.android.gms.maps.model.LatLng
import org.w3c.dom.Node
import java.io.InputStream
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Created by james on 2018-01-22.
 *
 */

class KmlParser {

    fun parse(input: InputStream): ArrayList<KmlInfo> {

        val list = ArrayList<KmlInfo>()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(input)

        val root = document.documentElement
        val nodeDocument = root.getElementsByTagName("Placemark")
        val coordinateTags = root.getElementsByTagName("coordinates")
        if(nodeDocument.length != 0) {
            var index = 0
            while(index < nodeDocument.length) { // Placemark list
                if(nodeDocument.item(index).hasChildNodes()) {
                    val node = nodeDocument.item(index)
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

                    list.add(KmlInfo(name, description, styleUrl, pointList, type))
                }
                index++
            }
        }

        return list
    }

}