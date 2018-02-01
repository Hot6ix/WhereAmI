package com.simples.j.whereami

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.simples.j.whereami.tools.KmlInfo
import com.simples.j.whereami.tools.KmlPlacemark
import com.simples.j.whereami.tools.Utils
import kotlinx.android.synthetic.main.activity_detail.*

class DetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private var itemId: String = ""
    private lateinit var item: KmlInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        itemId = intent.getStringExtra(ITEM_ID)
        item = intent.extras[ITEM] as KmlInfo
        detail_name.setText(item.name)
        detail_description.setText(item.description)

        detail_save.setOnClickListener {
            setResult(Activity.RESULT_OK, setupResult())
            finish()
        }
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_OK, setupResult())
        super.onBackPressed()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        val display = Utils.getDisplayResolution(applicationContext)
        googleMap.uiSettings.setAllGesturesEnabled(false)
        when(item.type) {
            KmlPlacemark.TYPE_POINT -> {
                googleMap.addMarker(MarkerOptions()
                        .icon(BitmapDescriptorFactory.defaultMarker())
                        .position(item.coordinates[0]))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(Utils.getPointsBound(item.coordinates).center, MapActivity.DEFAULT_CAMERA_ZOOM))
                detail_additional.text = getString(R.string.short_points_format, item.coordinates[0].latitude, item.coordinates[0].longitude)
            }
            KmlPlacemark.TYPE_LINE -> {
                googleMap.addPolyline(PolylineOptions()
                        .color(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
                        .addAll(item.coordinates))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(Utils.getPointsBound(item.coordinates), display[0], display[1], (display[0] * 0.2).toInt()))
                detail_additional.text = Utils.getDistance(item.coordinates, applicationContext)
            }
            KmlPlacemark.TYPE_POLYGON -> {
                googleMap.addPolygon(PolygonOptions()
                        .addAll(item.coordinates))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(Utils.getPointsBound(item.coordinates), display[0], display[1], (display[0] * 0.2).toInt()))
                detail_additional.text = Utils.getAreaOfPolygon(item.coordinates, applicationContext)
            }
        }
    }

    private fun setupResult(): Intent {
        val outIntent = Intent()
        item.name = detail_name.text.toString()
        item.description = detail_description.text.toString()
        outIntent.putExtra(ITEM, item)
        outIntent.putExtra(ITEM_ID, itemId)

        return outIntent
    }

    companion object {
        const val ITEM = "ITEM"
        const val ITEM_ID = "ITEM_ID"
    }
}