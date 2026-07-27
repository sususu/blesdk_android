package com.huawo.nt.sdkdemo

import android.app.Application
import android.content.Context
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper

class SdkDemoApp : Application() {
    lateinit var bleRepository: BleRepository
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        bleRepository = BleRepository(this)
    }

    companion object {
        lateinit var instance: SdkDemoApp
            private set
    }
}
