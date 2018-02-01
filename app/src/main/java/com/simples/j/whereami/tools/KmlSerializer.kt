package com.simples.j.whereami.tools

import android.content.Context
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.Polyline
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FileOutputStream

/**
 * Created by james on 2018-01-17.
 *
 */
class KmlSerializer(private var context: Context, private var items: ArrayList<KmlPlacemark>) {

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
        // Begin contents
        serializer.startTag(null, "Folder")
        serializer.startTag(null, "name")
        serializer.text("")
        serializer.endTag(null, "name")
        // Objects
        for(item in items) {
            val kmlItem = item.item
            when(kmlItem) {
                is Marker -> {
                    serializer.startTag(null, "Placemark")
                    // Name
                    serializer.startTag(null, "name")
                    serializer.text(item.name)
                    serializer.endTag(null, "name")
                    // Description
                    val description = item.description
                    if(description != null && description.isNotEmpty()) {
                        serializer.startTag(null, "description")
                        serializer.text(item.description)
                        serializer.endTag(null, "description")
                    }
                    // Point
                    serializer.startTag(null, "Point")
                    serializer.startTag(null, "coordinates")
                    serializer.text("${kmlItem.position.longitude},${kmlItem.position.latitude},0")
                    serializer.endTag(null, "coordinates")
                    serializer.endTag(null, "Point")
                    serializer.endTag(null, "Placemark")
                }
                is Polyline -> {
                    serializer.startTag(null, "Placemark")
                    // Name
                    serializer.startTag(null, "name")
                    serializer.text(item.name)
                    serializer.endTag(null, "name")
                    // Description
                    val description = item.description
                    if(description != null && description.isNotEmpty()) {
                        serializer.startTag(null, "description")
                        serializer.text(item.description)
                        serializer.endTag(null, "description")
                    }
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
                    serializer.endTag(null, "Placemark")
                }
                is Polygon -> {
                    serializer.startTag(null, "Placemark")
                    // Name
                    serializer.startTag(null, "name")
                    serializer.text(item.name)
                    serializer.endTag(null, "name")
                    // Description
                    val description = item.description
                    if(description != null && description.isNotEmpty()) {
                        serializer.startTag(null, "description")
                        serializer.text(item.description)
                        serializer.endTag(null, "description")
                    }
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
                    serializer.endTag(null, "Placemark")
                }
            }
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