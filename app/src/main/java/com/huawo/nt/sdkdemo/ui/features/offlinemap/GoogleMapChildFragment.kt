package com.huawo.nt.sdkdemo.ui.features.offlinemap

import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.huawo.nt.sdkdemo.R

class GoogleMapChildFragment : Fragment(), OnMapReadyCallback {

    interface Host {
        fun onGoogleMapReady(facade: DemoMapFacade)
    }

    private var googleMap: GoogleMap? = null
    private var cameraIdle: (() -> Unit)? = null
    private var supportMapFragment: SupportMapFragment? = null

    private val facade =
        object : DemoMapFacade {
            override val provider: DemoMapProvider = DemoMapProvider.GOOGLE

            override fun moveTo(lat: Double, lon: Double, zoom: Float, animate: Boolean) {
                val map = googleMap ?: return
                val update =
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(lat, lon))
                            .zoom(zoom)
                            .build(),
                    )
                if (animate) map.animateCamera(update) else map.moveCamera(update)
            }

            override fun fromScreen(point: Point): DemoLatLng? {
                val ll = googleMap?.projection?.fromScreenLocation(point) ?: return null
                return DemoLatLng(ll.latitude, ll.longitude)
            }

            override fun setOnCameraIdle(listener: (() -> Unit)?) {
                cameraIdle = listener
            }

            override fun currentCenter(): DemoLatLng? {
                val t = googleMap?.cameraPosition?.target ?: return null
                return DemoLatLng(t.latitude, t.longitude)
            }

            override fun currentZoom(): Float = googleMap?.cameraPosition?.zoom ?: 13f
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_google_map_child, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = SupportMapFragment.newInstance()
        supportMapFragment = mapFragment
        childFragmentManager.beginTransaction()
            .replace(R.id.map, mapFragment)
            .commitNowAllowingStateLoss()
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isRotateGesturesEnabled = false
        map.setOnCameraIdleListener { cameraIdle?.invoke() }
        (parentFragment as? Host)?.onGoogleMapReady(facade)
    }

    override fun onDestroyView() {
        supportMapFragment?.let {
            childFragmentManager.beginTransaction()
                .remove(it)
                .commitNowAllowingStateLoss()
        }
        supportMapFragment = null
        googleMap = null
        super.onDestroyView()
    }
}
