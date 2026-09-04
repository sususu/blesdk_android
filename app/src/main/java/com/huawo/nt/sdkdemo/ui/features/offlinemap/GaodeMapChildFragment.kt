package com.huawo.nt.sdkdemo.ui.features.offlinemap

import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.TextureMapView
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.huawo.nt.sdkdemo.R

class GaodeMapChildFragment : Fragment() {

    interface Host {
        fun onGaodeMapReady(facade: DemoMapFacade)
    }

    private var mapView: TextureMapView? = null
    private var aMap: AMap? = null
    private var cameraIdle: (() -> Unit)? = null

    private val facade =
        object : DemoMapFacade {
            override val provider: DemoMapProvider = DemoMapProvider.GAODE

            override fun moveTo(lat: Double, lon: Double, zoom: Float, animate: Boolean) {
                val map = aMap ?: return
                val update =
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition(LatLng(lat, lon), zoom, 0f, 0f),
                    )
                if (animate) map.animateCamera(update) else map.moveCamera(update)
            }

            override fun fromScreen(point: Point): DemoLatLng? {
                val ll = aMap?.projection?.fromScreenLocation(point) ?: return null
                return DemoLatLng(ll.latitude, ll.longitude)
            }

            override fun setOnCameraIdle(listener: (() -> Unit)?) {
                cameraIdle = listener
            }

            override fun currentCenter(): DemoLatLng? {
                val t = aMap?.cameraPosition?.target ?: return null
                return DemoLatLng(t.latitude, t.longitude)
            }

            override fun currentZoom(): Float = aMap?.cameraPosition?.zoom ?: 13f
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_gaode_map_child, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mv = view.findViewById<TextureMapView>(R.id.map)
        mapView = mv
        mv.onCreate(savedInstanceState)
        aMap = mv.map.apply {
            uiSettings.isZoomControlsEnabled = false
            uiSettings.isRotateGesturesEnabled = false
            setOnCameraChangeListener(
                object : AMap.OnCameraChangeListener {
                    override fun onCameraChange(position: CameraPosition?) = Unit

                    override fun onCameraChangeFinish(position: CameraPosition?) {
                        cameraIdle?.invoke()
                    }
                },
            )
            setOnMapLoadedListener {
                (parentFragment as? Host)?.onGaodeMapReady(facade)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        mapView?.onDestroy()
        mapView = null
        aMap = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }
}
