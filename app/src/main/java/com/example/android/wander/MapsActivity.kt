package com.example.android.wander

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.*
import java.io.IOException
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap

    private val TAG = MapsActivity::class.java.simpleName

    private val REQUEST_LOCATION_PERMISSION = 1

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    private val myRef2: DatabaseReference = database.getReference("marker")


    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val searchView = findViewById<SearchView>(R.id.sv_location)

        val queryListener = object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(location: String?): Boolean {
                var addressList: List<Address>? = null
                if (location != null || location == "") {
                    val geocoder = Geocoder(applicationContext)
                    try {
                        addressList = geocoder.getFromLocationName(location, 1)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    if (addressList != null) {
                        try {
                            val address = addressList[0]
                            val latLng = LatLng(address.latitude, address.longitude)
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                        } catch (e: IndexOutOfBoundsException) {
                            e.printStackTrace()
                            Toast.makeText(
                                applicationContext,
                                "Search error: Try being more specific",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    }
                }
                return false
            }

            override fun onQueryTextChange(p0: String?): Boolean {
                return true
            }

        }
        searchView.setOnQueryTextListener(queryListener)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.map_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        // Change the map type based on the user's selection.
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        setMapLongClick(map)
        setPoiClick(map)
        setMyLocation()

        //setMapStyle(map)
        //map.addGroundOverlay(androidOverlay)

        myRef2.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                map.clear()
                Toast.makeText(applicationContext, "Data changed", Toast.LENGTH_LONG).show()
                snapshot.children.forEach { dataSnapshop ->
                    val latlngMarker = dataSnapshop.getValue(LatlngMarker::class.java)
                    if (latlngMarker != null) {
                        val latLng = LatLng(latlngMarker.lat, latlngMarker.lng)

                        val snippet = String.format(
                            Locale.getDefault(),
                            "Lat: %1$.5f, Long: %2$.5f",
                            latLng.latitude,
                            latLng.longitude,
                        )
                        map.addMarker(
                            MarkerOptions().position(latLng).title(getString(R.string.dropped_pin))
                                .snippet(snippet).icon(
                                    BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_BLUE
                                    )
                                )
                        )
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
            }

        })
    }


    private fun setMapLongClick(map: GoogleMap) {
        map.setOnMapLongClickListener { latLng ->
            val myRef: DatabaseReference = database.getReference("marker").push()

            val marker = LatlngMarker(latLng.latitude, latLng.longitude)

            myRef.setValue(marker)

        }
    }

    private fun setPoiClick(map: GoogleMap) {
        map.setOnPoiClickListener { poi ->
            val poiMarker = map.addMarker(MarkerOptions().position(poi.latLng).title(poi.name))
            poiMarker.showInfoWindow()
        }

    }

    private fun setMapStyle(map: GoogleMap) {
        try {
            val success =
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style))
            if (!success) {
                Log.e(TAG, "Style parsing failed.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Can't find style. Error: ", e)
        }
    }

    private fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation(): Boolean {
        return if (isPermissionGranted()) {
            map.isMyLocationEnabled = true
            true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun setMyLocation() {
        var currentLoc: Location?
        val zoomLevel = 15f
        if (enableMyLocation()) {
            mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                currentLoc = location
                if (location != null) {
                    val currentLocationLatLng = LatLng(
                        currentLoc!!.latitude,
                        currentLoc!!.longitude
                    )
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            currentLocationLatLng,
                            zoomLevel
                        )
                    )
                } else {
                    Toast.makeText(this, "Location Off", Toast.LENGTH_LONG).show()
                }

            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                enableMyLocation()
            }
        }
    }
}