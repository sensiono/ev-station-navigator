package com.example.firstone1.ui.home

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.example.firstone1.R
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DirectionsBottomSheet : BottomSheetDialogFragment() {

    private var duration: String? = null
    private var distance: String? = null
    private var steps: List<String>? = null
    private var listener: OnExitRouteListener? = null
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    interface OnExitRouteListener {
        fun onExitRoute()
    }

    fun setOnExitRouteListener(listener: OnExitRouteListener) {
        this.listener = listener
    }

    companion object {
        fun newInstance(
            duration: String,
            distance: String,
            steps: List<String>
        ): DirectionsBottomSheet {
            val fragment = DirectionsBottomSheet()
            val args = Bundle()
            args.putString("duration", duration)
            args.putString("distance", distance)
            args.putStringArrayList("steps", ArrayList(steps))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            duration = it.getString("duration")
            distance = it.getString("distance")
            steps = it.getStringArrayList("steps")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_directions_bottom_sheet, container, false)

        val tvTime = view.findViewById<TextView>(R.id.tvTime)
        val tvDistance = view.findViewById<TextView>(R.id.tvDistance)
        val llStepsContainer = view.findViewById<LinearLayout>(R.id.llStepsContainer)
        val btnDirectionsStart = view.findViewById<Button>(R.id.btnDirectionsStart)
        val btnReturn = view.findViewById<Button>(R.id.btnReturn)
        val nestedScrollView = view.findViewById<NestedScrollView>(R.id.nestedScrollView)

        tvTime.text = "Time: $duration"
        tvDistance.text = "Distance: $distance"

        // Populate steps dynamically
        steps?.forEach { step ->
            val stepView = TextView(context)
            stepView.text = "• $step"
            stepView.setPadding(16, 8, 16, 8)
            stepView.setTextAppearance(android.R.style.TextAppearance_Material_Body1)
            llStepsContainer.addView(stepView)
        }

        // Handle return button
        btnReturn.setOnClickListener {
            listener?.onExitRoute()
            dismiss()
        }

        // Handle start navigation button
        btnDirectionsStart.setOnClickListener {
            Toast.makeText(context, "Starting Navigation...", Toast.LENGTH_SHORT).show()
            dismiss()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet!!)

        // Set initial state
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.isFitToContents = true
        bottomSheetBehavior.peekHeight = 400 // Adjust the mini state height as needed
        bottomSheetBehavior.skipCollapsed = false

        // Handle BottomSheet state changes
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        // Mini state (show minimal content)
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        // Full state (show all content)
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional: Handle UI changes during slide
            }
        })
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        listener?.onExitRoute() // Notify the listener to exit the route view
    }
}
