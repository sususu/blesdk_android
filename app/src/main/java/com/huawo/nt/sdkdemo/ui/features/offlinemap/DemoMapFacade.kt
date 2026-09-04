package com.huawo.nt.sdkdemo.ui.features.offlinemap

import android.graphics.Point

enum class DemoMapProvider {
    GAODE,
    GOOGLE,
}

data class DemoLatLng(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Thin map surface used by the offline-map crop screen (高德 / Google).
 */
interface DemoMapFacade {
    val provider: DemoMapProvider

    fun moveTo(lat: Double, lon: Double, zoom: Float, animate: Boolean)

    fun fromScreen(point: Point): DemoLatLng?

    fun setOnCameraIdle(listener: (() -> Unit)?)

    fun currentCenter(): DemoLatLng?

    fun currentZoom(): Float
}
