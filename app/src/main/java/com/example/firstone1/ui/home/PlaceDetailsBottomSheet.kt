package com.example.firstone1.ui.home

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.firstone1.R
import com.example.firstone1.databinding.FragmentPlaceDetailsBinding
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class PlaceDetailsBottomSheet : BottomSheetDialogFragment() {

    private var placeName: String? = null
    private var placeAddress: String? = null
    private var placePhoto: Bitmap? = null
    private var isDarkModeEnabled: Boolean = false
    private var rating: Double = -1.0
    private var userRatingsTotal: Int = 0
    private var openingNow: Boolean = false
    private var destinationLatLng: LatLng? = null

    private lateinit var binding: FragmentPlaceDetailsBinding
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var startNavigationListener: OnStartNavigationListener? = null

    interface OnStartNavigationListener {
        fun onStartNavigation(destination: LatLng)
    }


    companion object {
        fun newInstance(
            name: String,
            address: String,
            photo: Bitmap?,
            isDarkMode: Boolean,
            rating: Double,
            userRatingsTotal: Int,
            openingNow: Boolean,
            destinationLatLng: LatLng
        ): PlaceDetailsBottomSheet {
            val fragment = PlaceDetailsBottomSheet()
            val args = Bundle()
            args.putString("name", name)
            args.putString("address", address)
            args.putParcelable("photo", photo)
            args.putBoolean("isDarkMode", isDarkMode)
            args.putDouble("rating", rating)
            args.putInt("userRatingsTotal", userRatingsTotal)
            args.putBoolean("openingNow", openingNow)
            args.putParcelable("destinationLatLng", destinationLatLng)
            fragment.arguments = args
            return fragment
        }
    }

    fun setOnStartNavigationListener(listener: OnStartNavigationListener) {
        startNavigationListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isDarkModeEnabled = it.getBoolean("isDarkMode", false)
            placeName = it.getString("name")
            placeAddress = it.getString("address")
            placePhoto = it.getParcelable("photo")
            rating = it.getDouble("rating", -1.0)
            userRatingsTotal = it.getInt("userRatingsTotal", 0)
            openingNow = it.getBoolean("openingNow", false)
            destinationLatLng = it.getParcelable("destinationLatLng")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentPlaceDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up BottomSheetBehavior
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet!!)

        // Set initial state to mini
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // Handle state changes
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> showMiniState()
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> showHalfState()
                    BottomSheetBehavior.STATE_EXPANDED -> showFullState()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional: Handle slide animations
            }
        })



        binding.btnStart.setOnClickListener {
            destinationLatLng?.let { dest ->
                // Trigger turn-by-turn navigation functionality
                startNavigationListener?.drawRoute(dest)
                dismiss()
            } ?: Toast.makeText(context, "Destination is not available.", Toast.LENGTH_SHORT).show()
        }

        // Set button listeners
        binding.btnDirections.setOnClickListener {
            destinationLatLng?.let { dest ->
                startNavigationListener?.onStartNavigation(dest)
                dismiss()
            } ?: Log.e("ButtonClick", "Destination is null")
        }

        // Populate UI elements
        binding.placeName.text = placeName
        binding.placeAddress.text = placeAddress
        binding.placeImage.setImageBitmap(placePhoto)
        binding.ratingText.text =
            if (rating >= 0) "$rating ★ ($userRatingsTotal reviews)" else "No rating"
        binding.openStatus.text = if (openingNow) "Open Now" else "Closed"
    }

    private fun showMiniState() {
        binding.placeName.visibility = View.VISIBLE
        binding.btnStart.visibility = View.VISIBLE

        binding.placeImage.visibility = View.GONE
        binding.ratingText.visibility = View.GONE
        binding.placeAddress.visibility = View.GONE
        binding.openStatus.visibility = View.GONE
    }

    private fun showHalfState() {
        binding.placeName.visibility = View.VISIBLE
        binding.btnStart.visibility = View.VISIBLE
        binding.placeImage.visibility = View.VISIBLE

        binding.ratingText.visibility = View.GONE
        binding.placeAddress.visibility = View.GONE
        binding.openStatus.visibility = View.GONE
    }

    private fun showFullState() {
        binding.placeName.visibility = View.VISIBLE
        binding.btnStart.visibility = View.VISIBLE
        binding.placeImage.visibility = View.VISIBLE
        binding.ratingText.visibility = View.VISIBLE
        binding.placeAddress.visibility = View.VISIBLE
        binding.openStatus.visibility = View.VISIBLE
    }

    override fun onDetach() {
        super.onDetach()
        startNavigationListener = null
    }
}

