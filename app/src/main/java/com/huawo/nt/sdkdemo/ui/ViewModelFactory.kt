package com.huawo.nt.sdkdemo.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.huawo.nt.sdkdemo.SdkDemoApp
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.ui.bind.BindFlowViewModel
import com.huawo.nt.sdkdemo.ui.features.AlarmsViewModel
import com.huawo.nt.sdkdemo.ui.features.AlbumTransferViewModel
import com.huawo.nt.sdkdemo.ui.features.AgpsUpdateViewModel
import com.huawo.nt.sdkdemo.ui.features.GoalsViewModel
import com.huawo.nt.sdkdemo.ui.features.MusicTransferViewModel
import com.huawo.nt.sdkdemo.ui.features.NotifyViewModel
import com.huawo.nt.sdkdemo.ui.features.OtaUpgradeViewModel
import com.huawo.nt.sdkdemo.ui.main.HomeViewModel
import com.huawo.nt.sdkdemo.ui.scan.ScanViewModel
import com.huawo.nt.sdkdemo.ui.unbind.UnbindFlowViewModel

class ViewModelFactory(
    private val application: Application = SdkDemoApp.instance,
    private val repository: BleRepository = SdkDemoApp.instance.bleRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(application, repository) as T
            modelClass.isAssignableFrom(ScanViewModel::class.java) ->
                ScanViewModel(application, repository) as T
            modelClass.isAssignableFrom(BindFlowViewModel::class.java) ->
                BindFlowViewModel(application, repository) as T
            modelClass.isAssignableFrom(UnbindFlowViewModel::class.java) ->
                UnbindFlowViewModel(application, repository) as T
            modelClass.isAssignableFrom(GoalsViewModel::class.java) ->
                GoalsViewModel(application, repository) as T
            modelClass.isAssignableFrom(AlarmsViewModel::class.java) ->
                AlarmsViewModel(application, repository) as T
            modelClass.isAssignableFrom(NotifyViewModel::class.java) ->
                NotifyViewModel(application, repository) as T
            modelClass.isAssignableFrom(MusicTransferViewModel::class.java) ->
                MusicTransferViewModel(application, repository) as T
            modelClass.isAssignableFrom(AlbumTransferViewModel::class.java) ->
                AlbumTransferViewModel(application, repository) as T
            modelClass.isAssignableFrom(AgpsUpdateViewModel::class.java) ->
                AgpsUpdateViewModel(application, repository) as T
            modelClass.isAssignableFrom(OtaUpgradeViewModel::class.java) ->
                OtaUpgradeViewModel(application, repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
