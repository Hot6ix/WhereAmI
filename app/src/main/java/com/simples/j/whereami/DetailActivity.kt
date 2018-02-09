package com.simples.j.whereami

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.simples.j.whereami.style.LineStyle
import com.simples.j.whereami.style.MarkerStyle
import com.simples.j.whereami.style.PolygonStyle
import com.simples.j.whereami.style.StyleItem
import com.simples.j.whereami.tools.DetailColorListAdapter
import com.simples.j.whereami.tools.KmlInfo
import com.simples.j.whereami.tools.KmlPlacemark
import com.simples.j.whereami.tools.Utils
import kotlinx.android.synthetic.main.activity_detail.*

class DetailActivity : AppCompatActivity(), OnMapReadyCallback, DetailColorListAdapter.OnItemClickListener, SeekBar.OnSeekBarChangeListener {

    private var itemId: String = ""
    private var mapHeight = 0f
    private var transparency = Utils.DEFAULT_TRANSPARENCY
    private var width = MapActivity.DEFAULT_LINE_WIDTH
    private lateinit var mMap: GoogleMap
    private lateinit var item: KmlInfo
    private lateinit var itemStyle: StyleItem
    private lateinit var mapItem: Any

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        mapHeight = resources.getDimension(R.dimen.mini_map)

        itemId = intent.getStringExtra(ITEM_ID)
        item = intent.extras[ITEM] as KmlInfo
        itemStyle = intent.extras[ITEM_STYLE] as StyleItem
        detail_name.setText(item.name)
        detail_description.setText(item.description)

        val style = itemStyle.item
        var defaultColor = 0
        when(style) {
            is MarkerStyle -> {
                defaultColor = style.color
                detail_transparency_layout.visibility = View.GONE
                detail_width_layout.visibility = View.GONE
            }
            is LineStyle -> {
                defaultColor = style.color
                width = style.width
                detail_transparency_layout.visibility = View.GONE
            }
            is PolygonStyle -> {
                defaultColor = style.color
                transparency = Color.alpha(style.fillColor)
                width = style.width
                detail_width_title.text = getString(R.string.detail_thickness_polygon_title)
            }
        }
        detail_transparency.progress = transparency
        detail_transparency.setOnSeekBarChangeListener(this)
        detail_width.progress = width
        detail_width.setOnSeekBarChangeListener(this)

        val array = resources.getIntArray(R.array.color_list)
        val adapter = DetailColorListAdapter(array, defaultColor)
        adapter.setOnItemClickListener(this)
        colorPicker.layoutManager = GridLayoutManager(applicationContext, 6)
        colorPicker.adapter = adapter
        colorPicker.isNestedScrollingEnabled = false
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK, setupResult())
        super.onBackPressed()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        val display = Utils.getDisplayResolution(applicationContext)
        mMap = googleMap
        mMap.uiSettings.setAllGesturesEnabled(false)
        when(item.type) {
            KmlPlacemark.TYPE_POINT -> {
                val style = itemStyle.item as MarkerStyle
                mapItem = googleMap.addMarker(MarkerOptions()
                        .position(item.coordinates[0])
                        .icon(BitmapDescriptorFactory.fromBitmap(Utils.getDefaultMarker(style, applicationContext))))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Utils.getPointsBound(item.coordinates).center, MapActivity.DEFAULT_CAMERA_ZOOM))
                detail_additional.text = getString(R.string.short_points_format, item.coordinates[0].latitude, item.coordinates[0].longitude)
            }
            KmlPlacemark.TYPE_LINE -> {
                val style = itemStyle.item as LineStyle
                mapItem = googleMap.addPolyline(PolylineOptions()
                        .color(style.color)
                        .width(style.width.toFloat())
                        .addAll(item.coordinates))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(Utils.getPointsBound(item.coordinates), display[0], mapHeight.toInt(), (display[0] * 0.1).toInt()))
                detail_additional.text = Utils.getDistance(item.coordinates, applicationContext)
            }
            KmlPlacemark.TYPE_POLYGON -> {
                val style = itemStyle.item as PolygonStyle
                mapItem = googleMap.addPolygon(PolygonOptions()
                        .fillColor(style.fillColor)
                        .strokeColor(style.color)
                        .strokeWidth(style.width.toFloat())
                        .addAll(item.coordinates))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(Utils.getPointsBound(item.coordinates), display[0], mapHeight.toInt(), (display[0] * 0.1).toInt()))
                detail_additional.text = Utils.getAreaOfPolygon(item.coordinates, applicationContext)
            }
        }
    }

    override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
        when(seekBar.id) {
            R.id.detail_transparency -> {
                val style = itemStyle.item as PolygonStyle
                val color = style.color

                transparency = value
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val fillColor = Color.argb(transparency, r, g, b)

                style.color = color
                style.fillColor = fillColor
                (mapItem as Polygon).strokeColor = style.color
                (mapItem as Polygon).fillColor = style.fillColor
            }
            R.id.detail_width -> {
                var style = itemStyle.item
                width = value

                when(style) {
                    is PolygonStyle -> {
                        style = itemStyle.item as PolygonStyle
                        style.width = width
                        (mapItem as Polygon).strokeWidth = width.toFloat()
                    }
                    is LineStyle -> {
                        style = itemStyle.item as LineStyle
                        style.width = width
                        (mapItem as Polyline).width = width.toFloat()
                    }
                }

            }
        }
    }

    override fun onStartTrackingTouch(p0: SeekBar?) {}

    override fun onStopTrackingTouch(p0: SeekBar?) {}

    override fun onColorItemClick(color: Int, view: View) {
        val fixedMapItem = mapItem
        when(fixedMapItem) {
            is Marker -> {
                val style = itemStyle.item as MarkerStyle
                style.color = color
                fixedMapItem.setIcon(BitmapDescriptorFactory.fromBitmap(Utils.getDefaultMarker(style, applicationContext)))
            }
            is Polyline -> {
                val style = itemStyle.item as LineStyle
                style.color = color
                fixedMapItem.color = style.color
            }
            is Polygon -> {
                val style = itemStyle.item as PolygonStyle

                transparency = Color.alpha(style.fillColor)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val fillColor = Color.argb(transparency, r, g, b)

                style.color = color
                style.fillColor = fillColor
                fixedMapItem.strokeColor = style.color
                fixedMapItem.fillColor = style.fillColor
            }
        }
    }

    private fun setupResult(): Intent {
        val outIntent = Intent()
        item.name = detail_name.text.toString()
        item.description = detail_description.text.toString()
        outIntent.putExtra(ITEM, item)
        outIntent.putExtra(ITEM_ID, itemId)
        outIntent.putExtra(ITEM_STYLE, itemStyle)

        return outIntent
    }

    companion object {
        const val ITEM = "ITEM"
        const val ITEM_ID = "ITEM_ID"
        const val ITEM_STYLE = "ITEM_STYLE"
    }
}