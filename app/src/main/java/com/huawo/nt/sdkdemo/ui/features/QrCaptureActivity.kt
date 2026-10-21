package com.huawo.nt.sdkdemo.ui.features

import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.huawo.nt.sdkdemo.R
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/** Portrait scanner. The prompt must sit above the navigation bar, not on it. */
class QrCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
    }

    override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_qr_capture)
        val scanner = findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)
        liftStatus(scanner.statusView)
        return scanner
    }

    private fun liftStatus(status: TextView?) {
        if (status == null) return
        val gap = (24 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(status) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            lp.bottomMargin = gap + bars.bottom
            view.layoutParams = lp
            windowInsets
        }
        status.post { ViewCompat.requestApplyInsets(status) }
    }
}
