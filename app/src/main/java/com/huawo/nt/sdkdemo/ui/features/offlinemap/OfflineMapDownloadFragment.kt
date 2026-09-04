package com.huawo.nt.sdkdemo.ui.features.offlinemap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.huawo.nt.sdkdemo.R
import com.huawo.nt.sdkdemo.databinding.DialogOfflineMapProgressBinding
import com.huawo.nt.sdkdemo.databinding.DialogOfflineMapThemeBinding
import com.huawo.nt.sdkdemo.databinding.FragmentOfflineMapDownloadBinding
import com.huawo.nt.sdkdemo.ui.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * Offline map crop + install demo.
 *
 * Map stack matches HaWoFit: 高德 / Google (not OSM). Toolbar subtitle switches provider;
 * default is [DemoMapProvider.GAODE].
 */
class OfflineMapDownloadFragment :
    Fragment(),
    GaodeMapChildFragment.Host,
    GoogleMapChildFragment.Host {

    private var _binding: FragmentOfflineMapDownloadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OfflineMapDownloadViewModel by viewModels { ViewModelFactory() }

    private var mapFacade: DemoMapFacade? = null
    private var currentProvider: DemoMapProvider = DemoMapProvider.GAODE
    private var pendingCenter: DemoLatLng? = null
    private var pendingZoom: Float = DEFAULT_ZOOM

    private var progressDialog: BottomSheetDialog? = null
    private var progressBinding: DialogOfflineMapProgressBinding? = null
    private var themeDialog: BottomSheetDialog? = null
    private var spaceDialogShown = false
    private var themeLightSelected = true
    private var lastErrorShown: String? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) {
                installFromCrop()
            } else {
                Toast.makeText(requireContext(), R.string.permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOfflineMapDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }
        binding.toolbar.setOnClickListener { showProviderPicker() }
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_switch_map) {
                showProviderPicker()
                true
            } else {
                false
            }
        }
        binding.toolbar.inflateMenu(R.menu.menu_offline_map)
        refreshProviderSubtitle()

        binding.btnInstallToWatch.setOnClickListener { onInstallClicked() }
        showMapProvider(DemoMapProvider.GAODE, preserveCamera = false)
        locateAndCenter()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { bindState(it) }
            }
        }
    }

    private fun refreshProviderSubtitle() {
        binding.toolbar.subtitle =
            when (currentProvider) {
                DemoMapProvider.GAODE -> getString(R.string.offline_map_provider_gaode)
                DemoMapProvider.GOOGLE -> getString(R.string.offline_map_provider_google)
            }
    }

    private fun showProviderPicker() {
        val labels =
            arrayOf(
                getString(R.string.offline_map_provider_gaode),
                getString(R.string.offline_map_provider_google),
            )
        val checked = if (currentProvider == DemoMapProvider.GAODE) 0 else 1
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.offline_map_switch_provider)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val next =
                    if (which == 0) DemoMapProvider.GAODE else DemoMapProvider.GOOGLE
                if (next != currentProvider) {
                    showMapProvider(next, preserveCamera = true)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showMapProvider(provider: DemoMapProvider, preserveCamera: Boolean) {
        if (provider == DemoMapProvider.GOOGLE &&
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(requireContext()) != ConnectionResult.SUCCESS
        ) {
            Toast.makeText(
                requireContext(),
                R.string.offline_map_google_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        if (preserveCamera) {
            mapFacade?.currentCenter()?.let { pendingCenter = it }
            mapFacade?.let { pendingZoom = it.currentZoom() }
        }

        currentProvider = provider
        refreshProviderSubtitle()
        mapFacade = null

        val child: Fragment =
            when (provider) {
                DemoMapProvider.GAODE -> GaodeMapChildFragment()
                DemoMapProvider.GOOGLE -> GoogleMapChildFragment()
            }
        childFragmentManager.beginTransaction()
            .replace(R.id.map_container, child, TAG_MAP)
            .commitNowAllowingStateLoss()
    }

    override fun onGaodeMapReady(facade: DemoMapFacade) {
        onMapFacadeReady(facade)
    }

    override fun onGoogleMapReady(facade: DemoMapFacade) {
        onMapFacadeReady(facade)
    }

    private fun onMapFacadeReady(facade: DemoMapFacade) {
        mapFacade = facade
        facade.setOnCameraIdle { updateCropDistanceLabels() }
        val center = pendingCenter ?: DemoLatLng(DEFAULT_LAT, DEFAULT_LON)
        facade.moveTo(center.latitude, center.longitude, pendingZoom, animate = false)
        binding.viewfinderView.post { updateCropDistanceLabels() }
    }

    @SuppressLint("MissingPermission")
    private fun locateAndCenter() {
        fun apply(lat: Double, lon: Double) {
            pendingCenter = DemoLatLng(lat, lon)
            pendingZoom = DEFAULT_ZOOM
            mapFacade?.moveTo(lat, lon, DEFAULT_ZOOM, animate = true)
            binding.viewfinderView.post { updateCropDistanceLabels() }
        }
        if (!hasLocationPermission()) {
            apply(DEFAULT_LAT, DEFAULT_LON)
            return
        }
        LocationServices.getFusedLocationProviderClient(requireContext())
            .lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    apply(loc.latitude, loc.longitude)
                } else {
                    applyLastKnownOrDefault(::apply)
                }
            }
            .addOnFailureListener { applyLastKnownOrDefault(::apply) }
    }

    @SuppressLint("MissingPermission")
    private fun applyLastKnownOrDefault(apply: (Double, Double) -> Unit) {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc =
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                ?: runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        if (loc != null) apply(loc.latitude, loc.longitude) else apply(DEFAULT_LAT, DEFAULT_LON)
    }

    private fun updateCropDistanceLabels() {
        val facade = mapFacade ?: return
        val frame = binding.viewfinderView.getFrameRect()
        if (frame.width() <= 0 || frame.height() <= 0) return
        val leftTop =
            facade.fromScreen(Point(frame.left, frame.top)) ?: return
        val rightTop =
            facade.fromScreen(Point(frame.right, frame.top)) ?: return
        val results = FloatArray(1)
        Location.distanceBetween(
            leftTop.latitude,
            leftTop.longitude,
            rightTop.latitude,
            rightTop.longitude,
            results,
        )
        val text = String.format("%.2f km", results[0] / 1000.0)
        binding.viewfinderView.setText1(text)
        binding.viewfinderView.setText2(text)
        viewModel.updateDistanceText(text)
    }

    private fun onInstallClicked() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            return
        }
        installFromCrop()
    }

    private fun installFromCrop() {
        val facade = mapFacade
        if (facade == null) {
            Toast.makeText(requireContext(), R.string.offline_map_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val frame = binding.viewfinderView.getFrameRect()
        val nw = facade.fromScreen(Point(frame.left, frame.top))
        val se = facade.fromScreen(Point(frame.right, frame.bottom))
        if (nw == null || se == null) {
            Toast.makeText(requireContext(), R.string.offline_map_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val box =
            LatLngBox(
                minLat = minOf(nw.latitude, se.latitude),
                minLon = minOf(nw.longitude, se.longitude),
                maxLat = maxOf(nw.latitude, se.latitude),
                maxLon = maxOf(nw.longitude, se.longitude),
            )
        viewModel.install(box)
    }

    private fun bindState(state: OfflineMapUiState) {
        bindInstallLoading(state.busy)

        state.errorMessage?.let { msg ->
            if (msg != lastErrorShown) {
                lastErrorShown = msg
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        if (state.showProgressDialog) {
            showProgressDialog()
            updateProgressDialog(state)
        } else {
            progressDialog?.dismiss()
            progressDialog = null
            progressBinding = null
        }

        if (state.showThemeDialog) {
            showThemeDialog()
        } else {
            themeDialog?.dismiss()
            themeDialog = null
        }

        val warning = state.spaceWarning
        if (warning != null && !spaceDialogShown) {
            spaceDialogShown = true
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.offline_map_space_title)
                .setMessage(
                    getString(
                        R.string.offline_map_space_message,
                        warning.neededKb.toInt(),
                        warning.availableKb,
                    ),
                )
                .setPositiveButton(R.string.offline_map_ok) { _, _ ->
                    spaceDialogShown = false
                    viewModel.clearSpaceWarning()
                }
                .setOnDismissListener {
                    spaceDialogShown = false
                    viewModel.clearSpaceWarning()
                }
                .show()
        } else if (warning == null) {
            spaceDialogShown = false
        }
    }

    private fun bindInstallLoading(busy: Boolean) {
        binding.btnInstallToWatch.isEnabled = !busy
        binding.btnInstallToWatch.alpha = if (busy) 0.7f else 1f
        binding.btnInstallLoading.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnInstallToWatch.text =
            if (busy) "" else getString(R.string.offline_map_install)
    }

    private fun showProgressDialog() {
        if (progressDialog?.isShowing == true) return
        val dialog = BottomSheetDialog(requireContext())
        val pb = DialogOfflineMapProgressBinding.inflate(layoutInflater)
        progressBinding = pb
        dialog.setContentView(pb.root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)
        pb.ivCancel.setOnClickListener {
            viewModel.cancelInstall()
            dialog.dismiss()
            progressDialog = null
            progressBinding = null
        }
        progressDialog = dialog
        dialog.show()
    }

    private fun updateProgressDialog(state: OfflineMapUiState) {
        val pb = progressBinding ?: return
        when (state.progressPhase) {
            OfflineMapProgressPhase.Downloading -> {
                pb.tvPhase.setText(R.string.offline_map_phase_download)
                val pct = (state.downloadProgress * 100).toInt().coerceIn(0, 100)
                pb.progressBar.progress = pct
                pb.tvPercent.text = "$pct%"
            }
            OfflineMapProgressPhase.Installing -> {
                pb.tvPhase.setText(R.string.offline_map_phase_install)
                pb.progressBar.progress = state.installPercent.coerceIn(0, 100)
                pb.tvPercent.text = "${state.installPercent}%"
            }
        }
    }

    private fun showThemeDialog() {
        if (themeDialog?.isShowing == true) return
        themeLightSelected = true
        val dialog = BottomSheetDialog(requireContext())
        val tb = DialogOfflineMapThemeBinding.inflate(layoutInflater)
        dialog.setContentView(tb.root)
        dialog.setCanceledOnTouchOutside(false)
        fun refreshSelection() {
            tb.btnLight.alpha = if (themeLightSelected) 1f else 0.5f
            tb.btnDark.alpha = if (themeLightSelected) 0.5f else 1f
        }
        refreshSelection()
        tb.btnLight.setOnClickListener {
            themeLightSelected = true
            refreshSelection()
        }
        tb.btnDark.setOnClickListener {
            themeLightSelected = false
            refreshSelection()
        }
        tb.btnConfirm.setOnClickListener {
            viewModel.setTheme(themeLightSelected)
            dialog.dismiss()
            themeDialog = null
        }
        dialog.setOnCancelListener {
            viewModel.clearThemeDialog()
            themeDialog = null
        }
        themeDialog = dialog
        dialog.show()
    }

    private fun hasLocationPermission(): Boolean {
        val fine =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        val coarse =
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    override fun onDestroyView() {
        progressDialog?.dismiss()
        progressDialog = null
        progressBinding = null
        themeDialog?.dismiss()
        themeDialog = null
        mapFacade = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG_MAP = "offline_map_child"
        private const val DEFAULT_LAT = 39.9042
        private const val DEFAULT_LON = 116.4074
        private const val DEFAULT_ZOOM = 13f
    }
}
