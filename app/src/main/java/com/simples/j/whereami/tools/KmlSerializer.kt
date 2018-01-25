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
class KmlSerializer(context: Context, markers: ArrayList<Marker>, lines: ArrayList<Polyline>, polygons: ArrayList<Polygon>) {

    private var markerList: ArrayList<Marker> = markers
    private var lineList: ArrayList<Polyline> = lines
    private var polygonList: ArrayList<Polygon> = polygons

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
        for(item in markerList) {
            serializer.startTag(null, "Placemark")
            serializer.startTag(null, "name")
            serializer.text(item.tag.toString())
            serializer.endTag(null, "name")
            serializer.startTag(null, "Point")
            serializer.startTag(null, "coordinates")
            serializer.text("${item.position.longitude},${item.position.latitude},0")
            serializer.endTag(null, "coordinates")
            serializer.endTag(null, "Point")
            serializer.endTag(null, "Placemark")
        }
        for(item in lineList) {
            serializer.startTag(null, "Placemark")
            serializer.startTag(null, "name")
            serializer.text(item.tag.toString())
            serializer.endTag(null, "name")
            serializer.startTag(null, "LineString")
            serializer.startTag(null, "coordinates")
            val points = StringBuilder()
            for(point in item.points) {
                points.append("${point.longitude},${point.latitude},0\n")
            }
            serializer.text(points.toString())
            serializer.endTag(null, "coordinates")
            serializer.endTag(null, "LineString")
            serializer.endTag(null, "Placemark")
        }
        for(item in polygonList) {

            serializer.startTag(null, "Placemark")
            serializer.startTag(null, "name")
            serializer.text(item.tag.toString())
            serializer.endTag(null, "name")
            serializer.startTag(null, "Polygon")
            serializer.startTag(null, "outerBoundaryIs")
            serializer.startTag(null, "LinearRing")
            serializer.startTag(null, "coordinates")
            val points = StringBuilder()
            for(point in item.points) {
                points.append("${point.longitude},${point.latitude},0\n")
            }
            serializer.text(points.toString())
            serializer.endTag(null, "coordinates")
            serializer.endTag(null, "LinearRing")
            serializer.endTag(null, "outerBoundaryIs")
            serializer.endTag(null, "Polygon")
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