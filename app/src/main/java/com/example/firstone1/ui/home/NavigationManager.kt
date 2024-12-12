package com.example.firstone1.ui.navigation

import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import android.graphics.Color


class NavigationManager(
    private val context: Context,
    private val mMap: GoogleMap,
) {

    private var userMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    fun fetchDirections(origin: LatLng, destination: LatLng, onSuccess: (JSONArray) -> Unit) {
        val apiKey = "AlzaSyDVs2HrgI3oBNlOWavUOO-elazFB1Q8Pwu" // Replace with your actual API key
        val url = "https://maps.gomaps.pro/maps/api/directions/json" +
                "?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=driving" +
                "&key=$apiKey"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NavigationManager", "Failed to fetch directions: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { responseBody ->
                        val jsonObject = JSONObject(responseBody)
                        val routes = jsonObject.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")
                            val legs = route.getJSONArray("legs").getJSONObject(0)
                            val steps = legs.getJSONArray("steps")

                            // Draw the route
                            val decodedPath = PolyUtil.decode(overviewPolyline)
                            mMap.clear()
                            val polylineOptions = PolylineOptions()
                                .addAll(decodedPath)
                                .width(8f)
                                .color(Color.BLUE)
                                .zIndex(1f)
                            mMap.addPolyline(polylineOptions)

                            // Pass the steps to the callback
                            onSuccess(steps)
                        }
                    }
                } else {
                    Log.e("NavigationManager", "Failed response: ${response.code}, ${response.message}")
                }
            }
        })
    }

    fun startTurnByTurnNavigation(steps: JSONArray) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)

                    // Update user marker
                    updateUserMarker(currentLatLng)

                    // Provide turn-by-turn instructions
                    provideTurnByTurnInstructions(currentLatLng, steps)
                }
            }
        }

        val locationRequest = LocationRequest.create().apply {
            interval = 5000 // Update every 5 seconds
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
    }

    fun stopNavigation() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        userMarker?.remove()
        mMap.clear()
        Toast.makeText(context, "Navigation stopped.", Toast.LENGTH_SHORT).show()
    }

    private fun updateUserMarker(currentLatLng: LatLng) {
        userMarker?.remove()
        userMarker = mMap.addMarker(
            MarkerOptions().position(currentLatLng).title("You").icon(
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
            )
        )
        mMap.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng))
    }

    private fun provideTurnByTurnInstructions(currentLocation: LatLng, steps: JSONArray) {
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val endLocation = step.getJSONObject("end_location")
            val endLatLng = LatLng(endLocation.getDouble("lat"), endLocation.getDouble("lng"))

            val distance = FloatArray(1)
            Location.distanceBetween(
                currentLocation.latitude, currentLocation.longitude,
                endLatLng.latitude, endLatLng.longitude, distance
            )

            if (distance[0] < 30) { // User is within 30 meters of the turn
                val instruction = step.getString("html_instructions").replace(Regex("<[^>]*>"), "")
                Toast.makeText(context, instruction, Toast.LENGTH_SHORT).show()
                break
            }
        }
    }
}
