package com.simples.j.whereami.tools

import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.Polyline
import com.simples.j.whereami.style.LineStyle
import com.simples.j.whereami.style.MarkerStyle
import com.simples.j.whereami.style.PolygonStyle
import com.simples.j.whereami.style.StyleItem
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FileOutputStream

/**
 * Created by james on 2018-01-17.
 *
 */
class KmlSerializer(private var items: ArrayList<KmlPlacemark>, private var styles: ArrayList<StyleItem>) {

    fun serialize(output: FileOutputStream) {
        val serializer = XmlPullParserFactory.newInstance().newSerializer()

        serializer.setOutput(output.bufferedWriter())
        serializer.startDocument("UTF-8", true)
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
        serializer.startTag(null, "kml")
        serializer.attribute(null, "xmlns", "http://www.opengis.net/kml/2.2")
        serializer.startTag(null, "Document")
        // Name
        serializer.startTag(null, "name")
        serializer.text("")
        serializer.endTag(null, "name")
        // Description
        serializer.startTag(null, "description")
        serializer.text("")
        serializer.endTag(null, "description")
        // Begin styles
        for(item in styles) {
            val styleItem = item.item
            serializer.startTag(null, "Style")
            serializer.attribute(null, "id", item.id)
            when(styleItem) {
                is MarkerStyle -> {
                    serializer.startTag(null, "IconStyle")
                    // Color
                    serializer.startTag(null, "color")
                    serializer.text(String.format("#%08X", (0xFFFFFFFF and styleItem.color.toLong())))
                    serializer.endTag(null, "color")
                    // ColorMode
                    serializer.startTag(null, "colorMode")
                    serializer.text(styleItem.colorMode)
                    serializer.endTag(null, "colorMode")
                    // Scale
                    serializer.startTag(null, "scale")
                    serializer.text(styleItem.scale.toString())
                    serializer.endTag(null, "scale")
                    // Icon
                    serializer.startTag(null, "Icon")
                    serializer.startTag(null, "href")
                    serializer.text(styleItem.icon)
                    serializer.endTag(null, "href")
                    serializer.endTag(null, "Icon")
                    serializer.endTag(null, "IconStyle")
                }
                is LineStyle -> {
                    serializer.startTag(null, "LineStyle")
                    // Color
                    serializer.startTag(null, "color")
                    serializer.text(String.format("#%08X", (0xFFFFFFFF and styleItem.color.toLong())))
                    serializer.endTag(null, "color")
                    // ColorMode
                    serializer.startTag(null, "colorMode")
                    serializer.text(styleItem.colorMode)
                    serializer.endTag(null, "colorMode")
                    // Width
                    serializer.startTag(null, "width")
                    serializer.text(styleItem.width.toString())
                    serializer.endTag(null, "width")
                    serializer.endTag(null, "LineStyle")
                }
                is PolygonStyle -> {
                    // Line
                    serializer.startTag(null, "LineStyle")
                    // Color
                    serializer.startTag(null, "color")
                    serializer.text(String.format("#%08X", (0xFFFFFFFF and styleItem.color.toLong())))
                    serializer.endTag(null, "color")
                    // Width
                    serializer.startTag(null, "width")
                    serializer.text(styleItem.width.toString())
                    serializer.endTag(null, "width")
                    serializer.endTag(null, "LineStyle")
                    // Polygon
                    serializer.startTag(null, "PolyStyle")
                    // Color
                    serializer.startTag(null, "color")
                    serializer.text(String.format("#%08X", (0xFFFFFFFF and styleItem.fillColor.toLong())))
                    serializer.endTag(null, "color")
                    // Fill
                    serializer.startTag(null, "fill")
                    serializer.text(styleItem.fill.toString())
                    serializer.endTag(null, "fill")
                    serializer.endTag(null, "PolyStyle")
                }
            }
            serializer.endTag(null, "Style")
        }
        // Begin contents
        serializer.startTag(null, "Folder")
        serializer.startTag(null, "name")
        serializer.text("")
        serializer.endTag(null, "name")
        // Objects
        for(item in items) {
            val kmlItem = item.item
            serializer.startTag(null, "Placemark")
            // Name
            serializer.startTag(null, "name")
            serializer.text(item.name)
            serializer.endTag(null, "name")
            // Style
            serializer.startTag(null, "styleUrl")
            val styleId = item.styleUrl
            if(styleId != null && styleId.startsWith("#"))
                serializer.text(styleId)
            else
                serializer.text("#" + styleId)
            serializer.endTag(null, "styleUrl")
            // Description
            val description = item.description
            if(description != null && description.isNotEmpty()) {
                serializer.startTag(null, "description")
                serializer.text(item.description)
                serializer.endTag(null, "description")
            }
            when(kmlItem) {
                is Marker -> {
                    // Point
                    serializer.startTag(null, "Point")
                    serializer.startTag(null, "coordinates")
                    serializer.text("${kmlItem.position.longitude},${kmlItem.position.latitude},0")
                    serializer.endTag(null, "coordinates")
                    serializer.endTag(null, "Point")
                }
                is Polyline -> {
                    // Points
                    serializer.startTag(null, "LineString")
                    serializer.startTag(null, "coordinates")
                    val points = StringBuilder()
                    for(point in kmlItem.points) {
                        points.append("${point.longitude},${point.latitude},0\n")
                    }
                    serializer.text(points.toString())
                    serializer.endTag(null, "coordinates")
                    serializer.endTag(null, "LineString")
                }
                is Polygon -> {
                    // Points
                    serializer.startTag(null, "Polygon")
                    serializer.startTag(null, "outerBoundaryIs")
                    serializer.startTag(null, "LinearRing")
                    serializer.startTag(null, "coordinates")
                    val points = StringBuilder()
                    for(point in kmlItem.points) {
                        points.append("${point.longitude},${point.latitude},0\n")
                    }
                    serializer.text(points.toString())
                    serializer.endTag(null, "coordinates")
                    serializer.endTag(null, "LinearRing")
                    serializer.endTag(null, "outerBoundaryIs")
                    serializer.endTag(null, "Polygon")
                }
            }
            serializer.endTag(null, "Placemark")
        }
        serializer.endTag(null, "Folder")
        // End contents
        serializer.endTag(null, "Document")
        serializer.endTag(null, "kml")
        serializer.endDocument()

        serializer.flush()
        output.close()
    }

}