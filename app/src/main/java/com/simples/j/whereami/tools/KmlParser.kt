package com.simples.j.whereami.tools

import android.util.Log
import android.util.Xml
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Created by james on 2018-01-22.
 *
 */

private const val a = "TAG"

class KmlParser {

    fun parse(input: InputStream): ArrayList<KmlPlacemark> {

        val list = ArrayList<KmlPlacemark>()
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
                    var points = ""
                    var type = ""
                    while(subIndex < children.length) { // items of Placemark
                        if(children.item(subIndex).nodeType != Node.TEXT_NODE) {
                            when(children.item(subIndex).nodeName) {
                                "name" -> name = children.item(subIndex).firstChild.nodeValue
                                "description" -> description = children.item(subIndex).firstChild.nodeValue
                                "styleUrl" -> styleUrl = children.item(subIndex).firstChild.nodeValue
                                "Point", "LineString", "Polygon" -> {
                                    type = children.item(subIndex).nodeName
                                    points = coordinateTags.item(index).firstChild.nodeValue
                                }
                            }
                        }
                        subIndex++
                    }
                    list.add(KmlPlacemark(name, description, styleUrl, points, type))
                }
                index++
            }
        }

        return list
    }

    private fun getChildNode(node: Node, coordinates: NodeList) {
        var list = ArrayList<KmlPlacemark>()
        val children = node.childNodes
        var index = 0

        var name = ""
        var description = ""
        var styleUrl = ""
        var points = ""
        var type = ""
        while(index < children.length) {
            if(children.item(index).nodeType != Node.TEXT_NODE) {
                when(children.item(index).nodeName) {
                    "name" -> name = children.item(index).firstChild.nodeValue
                    "description" -> description = children.item(index).firstChild.nodeValue
                    "styleUrl" -> styleUrl = children.item(index).firstChild.nodeValue
                    "Point", "LineString", "Polygon" -> {
                        type = children.item(index).nodeName
                        points = coordinates.item(index).firstChild.nodeValue
                    }
                }
            }
            index++
        }
        list.add(KmlPlacemark(name, description, styleUrl, points, type))
        Log.d(a, KmlPlacemark(name, description, styleUrl, points, type).toString())
    }

}