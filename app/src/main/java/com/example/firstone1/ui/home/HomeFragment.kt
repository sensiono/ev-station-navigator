package com.example.firstone1.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.firstone1.R
import com.example.firstone1.databinding.FragmentHomeBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import android.graphics.Color
import android.os.Looper
import android.widget.Toast
import com.example.firstone1.ui.navigation.NavigationManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.maps.android.PolyUtil
import com.google.android.gms.maps.model.PolylineOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray

class HomeFragment : Fragment(), OnMapReadyCallback, PlaceDetailsBottomSheet.OnStartNavigationListener {

    private var mMap: GoogleMap? = null
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isDarkModeEnabled = false
    private var userLocation: LatLng? = null // Store user location
    private var userMarker: Marker? = null // For real-time navigation
    private var locationCallback: LocationCallback? = null
    private lateinit var navigationManager: NavigationManager



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Initialize the map fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnToggleTheme.setOnClickListener { toggleMapStyle() }
        return binding.root
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap?.isMyLocationEnabled = true
            getUserLocation()
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 1001
            )
        }

        setInitialMapStyle()

        mMap?.setOnMarkerClickListener { marker ->
            val placeData = marker.tag as? PlaceData
            if (placeData != null) {
                fetchPlacePhoto(placeData.photoReference) { bitmap ->
                    displayBottomSheet(placeData, bitmap)
                }
            }
            true // Indicate that the click has been handled
        }
    }

    override fun onStartNavigation(destination: LatLng) {
        if (userLocation == null) {
            Toast.makeText(requireContext(), "User location not available.", Toast.LENGTH_SHORT).show()
            return
        }

        // Use NavigationManager for turn-by-turn navigation
        navigationManager.fetchDirections(userLocation!!, destination) { steps ->
            navigationManager.startTurnByTurnNavigation(steps)
            Log.d("HomeFragment", "Turn-by-turn navigation started")
        }
    }

    fun drawRoute(destination: LatLng) {
        if (userLocation == null) {
            Toast.makeText(requireContext(), "User location not available.", Toast.LENGTH_SHORT).show()
            return
        }

        // Use NavigationManager for route drawing
        navigationManager.fetchDirections(userLocation!!, destination) { steps ->
            Log.d("HomeFragment", "Route drawn without starting turn-by-turn navigation")
            // Do nothing further since only the route is displayed
        }
    }


    private fun displayBottomSheet(placeData: PlaceData, photo: Bitmap?) {
        val bottomSheetFragment = PlaceDetailsBottomSheet.newInstance(
            name = placeData.name,
            address = placeData.address,
            photo = photo,
            isDarkMode = isDarkModeEnabled,
            rating = placeData.rating,
            userRatingsTotal = placeData.userRatingsTotal,
            openingNow = placeData.openingNow,
            destinationLatLng = placeData.latLng
        )

        // Pass the listener directly
        bottomSheetFragment.setOnStartNavigationListener(this)
        bottomSheetFragment.show(parentFragmentManager, bottomSheetFragment.tag)
    }


    private fun setInitialMapStyle() {
        try {
            val styleRes = if (isDarkModeEnabled) R.raw.map_style_dark else R.raw.map_style_light
            mMap?.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), styleRes))
        } catch (e: Exception) {
            Log.e("HomeFragment", "Error setting map style: ${e.message}")
        }
    }

    private fun toggleMapStyle() {
        isDarkModeEnabled = !isDarkModeEnabled
        setInitialMapStyle()
    }

    private fun getUserLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                userLocation = LatLng(location.latitude, location.longitude)
                mMap?.addMarker(MarkerOptions().position(userLocation!!).title("You are here"))
                mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation!!, 15f))

                fetchNearbyEVStations(userLocation!!)
            } else {
                Log.e("HomeFragment", "User location is null")
            }
        }
    }

    private fun fetchDirectionsForNavigation(origin: LatLng, destination: LatLng) {
        val apiKey = "YOUR_API_KEY" // Replace with your API key
        val url = "https://maps.gomaps.pro/maps/api/directions/json" +
                "?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=driving" +
                "&key=$apiKey"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FetchDirections", "Failed to fetch directions: ${e.message}")
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

                            activity?.runOnUiThread {
                                startTurnByTurnNavigation(overviewPolyline, steps)
                            }
                        }
                    }
                } else {
                    Log.e("FetchDirections", "Failed response: ${response.code}, ${response.message}")
                }
            }
        })
    }

    private fun startTurnByTurnNavigation(polyline: String, steps: JSONArray) {
        val decodedPath = PolyUtil.decode(polyline)

        // Draw the route on the map
        mMap?.clear()
        val polylineOptions = PolylineOptions()
            .addAll(decodedPath)
            .width(8f)
            .color(Color.BLUE)
        mMap?.addPolyline(polylineOptions)

        // Start real-time location tracking
        trackUserLocation(steps)
    }

    private fun trackUserLocation(steps: JSONArray) {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // High accuracy for driving navigation
            5000 // 5-second interval
        ).apply {
            setMinUpdateIntervalMillis(2000) // Minimum update interval
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)

                    // Update user's marker
                    updateUserMarker(currentLatLng)

                    // Provide turn-by-turn instructions
                    provideTurnByTurnInstructions(currentLatLng, steps)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
    }


    private fun updateUserMarker(currentLatLng: LatLng) {
        userMarker?.remove()
        userMarker = mMap?.addMarker(
            MarkerOptions()
                .position(currentLatLng)
                .title("You")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )
        mMap?.animateCamera(CameraUpdateFactory.newLatLng(currentLatLng))
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

            if (distance[0] < 30) { // User is within 30 meters of the next turn
                val instruction = step.getString("html_instructions").replace(Regex("<[^>]*>"), "")
                Toast.makeText(context, instruction, Toast.LENGTH_SHORT).show()
                break
            }
        }
    }

    private fun stopNavigation() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
        userMarker?.remove()
        Toast.makeText(requireContext(), "Navigation stopped.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopNavigation()
    }




    private fun fetchNearbyEVStations(userLocation: LatLng) {
        val apiKey = "AlzaSyDVs2HrgI3oBNlOWavUOO-elazFB1Q8Pwu" // Replace with your actual API key
        val url = "https://maps.gomaps.pro/maps/api/place/nearbysearch/json" +
                "?location=${userLocation.latitude},${userLocation.longitude}" +
                "&radius=50000" +
                "&name=electric+vehicle+charging+station" +
                "&key=$apiKey"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HomeFragment", "Failed to fetch EV stations: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { json ->
                        Log.d("HomeFragment", "Nearby EV Response: $json")
                        val jsonObject = JSONObject(json)
                        val results = jsonObject.getJSONArray("results")
                        for (i in 0 until results.length()) {
                            val place = results.getJSONObject(i)
                            val location = place.getJSONObject("geometry").getJSONObject("location")
                            val lat = location.getDouble("lat")
                            val lng = location.getDouble("lng")
                            val name = place.optString("name", "Unknown Place")
                            val address = place.optString("vicinity", "No address available")
                            val rating = place.optDouble("rating", -1.0)
                            val userRatingsTotal = place.optInt("user_ratings_total", 0)
                            val openingNow = place.optJSONObject("opening_hours")?.optBoolean("open_now", false)
                            val photoReference = place.optJSONArray("photos")?.optJSONObject(0)
                                ?.optString("photo_reference")

                            val latLng = LatLng(lat, lng) // Create LatLng object

                            // Add marker to the map
                            activity?.runOnUiThread {
                                val marker = mMap?.addMarker(
                                    MarkerOptions()
                                        .position(latLng)
                                        .title(name)
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                                )
                                marker?.tag = PlaceData(
                                    name = name,
                                    address = address,
                                    rating = rating,
                                    userRatingsTotal = userRatingsTotal,
                                    openingNow = openingNow ?: false,
                                    photoReference = photoReference,
                                    latLng = latLng // Pass LatLng to PlaceData
                                )
                            }
                        }
                    }
                }
            }
        })
    }


    private fun fetchPlacePhoto(photoReference: String?, callback: (Bitmap?) -> Unit) {
        if (photoReference == null) {
            callback(BitmapFactory.decodeResource(resources, R.drawable.noimage))
            return
        }

        val apiKey = "AlzaSyDVs2HrgI3oBNlOWavUOO-elazFB1Q8Pwu" // Replace with your actual API key
        val url = "https://maps.gomaps.pro/maps/api/place/photo" +
                "?photo_reference=$photoReference" +
                "&maxwidth=400" +
                "&key=$apiKey"

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HomeFragment", "Error fetching photo: ${e.message}")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val inputStream = response.body?.byteStream()
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    callback(bitmap)
                } else {
                    Log.e("HomeFragment", "Failed to fetch photo: ${response.message}")
                    callback(null)
                }
            }
        })
    }




    private fun fetchDirections(origin: LatLng, destination: LatLng) {
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
                Log.e("FetchDirections", "Failed to fetch directions: ${e.message}")
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
                            val duration = legs.getJSONObject("duration").getString("text")
                            val distance = legs.getJSONObject("distance").getString("text")

                            val steps = legs.getJSONArray("steps").let { stepsArray ->
                                (0 until stepsArray.length()).map { i ->
                                    stepsArray.getJSONObject(i).getString("html_instructions")
                                        .replace(Regex("<[^>]*>"), "") // Remove HTML tags
                                }
                            }

                            activity?.runOnUiThread {
                                drawRoute(overviewPolyline, duration, distance)
                                displayDirectionsBottomSheet(duration, distance, steps)
                            }
                        }
                    }
                } else {
                    Log.e("FetchDirections", "Failed response: ${response.code}, ${response.message}")
                }
            }
        })
    }

    private fun displayDirectionsBottomSheet(
        duration: String,
        distance: String,
        steps: List<String>
    ) {
        val bottomSheet = DirectionsBottomSheet.newInstance(duration, distance, steps)
        bottomSheet.setOnExitRouteListener(object : DirectionsBottomSheet.OnExitRouteListener {
            override fun onExitRoute() {
                clearRoute() // Clear the map route when the user exits
            }
        })
        bottomSheet.show(parentFragmentManager, bottomSheet.tag)
    }

    private fun clearRoute() {
        mMap?.clear() // Clear all polylines and markers from the map

        // Re-add the user's location marker
        if (userLocation != null) {
            mMap?.addMarker(
                MarkerOptions().position(userLocation!!).title("You are here")
            )
            mMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation!!, 15f))
        }

        // Re-fetch and display EV stations
        if (userLocation != null) {
            fetchNearbyEVStations(userLocation!!)
        }
    }







    private fun drawRoute(polyline: String, duration: String, distance: String) {
        val decodedPath = PolyUtil.decode(polyline)

        // Clear previous markers and polylines
        mMap?.clear()

        // Draw the route polyline
        val polylineOptions = PolylineOptions()
            .addAll(decodedPath)
            .width(8f)
            .color(Color.BLUE)
            .zIndex(1f)
        mMap?.addPolyline(polylineOptions)

        // Add a marker at the origin with travel information
        if (userLocation != null) {
            mMap?.addMarker(
                MarkerOptions()
                    .position(userLocation!!)
                    .title("Travel Info")
                    .snippet("Duration: $duration, Distance: $distance")
            )?.showInfoWindow()
        }

        // Adjust camera to show the full route
        val bounds = LatLngBounds.Builder()
        decodedPath.forEach { bounds.include(it) }
        mMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
    }


    data class PlaceData(
        val name: String,
        val address: String,
        val rating: Double,
        val userRatingsTotal: Int,
        val openingNow: Boolean,
        val photoReference: String?,
        val latLng: LatLng
    )
}