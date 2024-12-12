package com.example.firstone1.ui.home

import com.google.android.gms.maps.model.LatLng

data class PlaceData(
    val name: String,
    val address: String,
    val rating: Double,
    val userRatingsTotal: Int,
    val openingNow: Boolean,
    val photoReference: String?,
    val latLng: LatLng // Add this property
)

