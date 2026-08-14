package com.huawo.nt.sdkdemo

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.huawo.ai.AiCenter
import com.huawo.ai.utils.SpUtils
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
 *
 * ## AiCenter (HaWoFit-aligned)
 * [AiCenter.setApplication] + [SpUtils.init] once per process.
 * [ProcessLifecycleOwner] keeps [AiCenter.setAppInForeground] in sync (HaWoFit uses
 * AppForegroundEvent for the same purpose). Full AI start (init / key / startWorking /
 * setDeviceInfo) still happens on the AI demo screen after BLE is connected.
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

        // AiCenter process-level bind (required before init/startWorking on feature screens).
        SpUtils.init(this)
        AiCenter.getInstance().setApplication(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    AiCenter.getInstance().isAppInForeground = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    AiCenter.getInstance().isAppInForeground = false
                }
            },
        )
    }

    companion object {
        lateinit var instance: SdkDemoApp
            private set
    }
}
