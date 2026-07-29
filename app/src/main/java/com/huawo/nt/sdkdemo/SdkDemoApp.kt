package com.huawo.nt.sdkdemo

import android.app.Application
import android.content.Context
import com.huawo.nt.sdkdemo.data.repository.BleRepository
import com.huawo.nt.sdkdemo.util.LocaleHelper
import com.huawo.watchface.WatchfaceSDK

/**
 * Process-wide entry point.
 *
 * ## WatchfaceSDK init (required)
 * [WatchfaceSDK.init] must run before any custom-watchface push:
 * [com.huawo.watchface.custom.SifliCustomWatchface.syncToWatch] →
 * `WatchfaceSDK.setCustomWatchface(...)`.
 *
 * If skipped, the AAR asserts:
 * `Please call WatchfaceSDK.getIntance().init(Application application) before using any API`.
 *
 * We init here in [onCreate] (same pattern as the reference sdk-demo Application) so the
 * Custom / Online / Album / Music Sifli paths can assume the push manager exists even before
 * the user opens Home and triggers [BleRepository.init].
 *
 * [BleRepository.init] also calls [WatchfaceSDK.init] again (idempotent flag inside the AAR
 * sets `hadInit = true`) so a code path that only warms BLE still covers WatchfaceSDK.
 */
class SdkDemoApp : Application() {
    lateinit var bleRepository: BleRepository
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Watchface file-push manager (SFWatchfaceFilePushManager) lives behind this singleton.
        WatchfaceSDK.getInstance().init(this)
        bleRepository = BleRepository(this)
    }

    companion object {
        lateinit var instance: SdkDemoApp
            private set
    }
}
