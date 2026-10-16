package com.huawo.nt.sdkdemo.util

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import com.huawo.nt.sdkdemo.R

/**
 * targetSdk 35+ 在 Android 15/16 上强制 edge-to-edge，需自行处理系统栏 insets，
 * 否则 Toolbar 会顶进状态栏被遮挡。
 *
 * 注意：Android 16 已移除 windowOptOutEdgeToEdgeEnforcement，只能做 insets 适配。
 */
object EdgeToEdgeHelper {

    /** 在 [android.app.Activity.onCreate] 里、[super.onCreate][FragmentActivity.onCreate] 之前调用。 */
    fun enable(activity: ComponentActivity) {
        activity.enableEdgeToEdge(
            // 启动时沿用蓝色标题栏的浅色图标；页面恢复显示时再按实际顶部背景更新。
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
    }

    /** 在 setContentView 之后安装 root / Toolbar 的 insets 处理。 */
    fun install(activity: FragmentActivity, root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            v.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom),
            )
            // 顶部留给各页 Toolbar / 无 Toolbar 的顶层页；左右/底部已在 root 消化
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(0, bars.top, 0, 0),
                )
                .build()
        }

        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: android.os.Bundle?,
                ) {
                    val toolbar = v.findViewById<View>(R.id.toolbar)
                    when {
                        toolbar != null -> applyToolbarStatusBarInsets(toolbar)
                        // 顶层无 Toolbar 页（如从 AI 指南打开的实测页）需自行避让状态栏；
                        // 嵌套在 Watchface Tab 内的子页由宿主 Toolbar 处理，不再加 top padding。
                        f.parentFragment == null -> applyContentStatusBarInsets(v)
                    }
                }

                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    // 只跟随主容器页面（含返回）；Tab 子页和弹窗不能覆盖宿主状态栏。
                    if (f.parentFragment != null || f.id != R.id.fragment_container) return
                    val view = f.view ?: return
                    updateStatusBarAppearance(activity, view.findViewById<View>(R.id.toolbar) ?: view)
                }
            },
            true,
        )
    }

    private fun updateStatusBarAppearance(activity: FragmentActivity, topView: View) {
        val windowBackground = MaterialColors.getColor(topView, android.R.attr.colorBackground)
        val color = when (val background = topView.background) {
            is ColorDrawable -> background.color
            // MaterialToolbar 会把纯色背景转换为 MaterialShapeDrawable。
            is MaterialShapeDrawable -> background.fillColor?.let {
                it.getColorForState(topView.drawableState, it.defaultColor)
            }
            else -> null
        } ?: windowBackground
        val opaqueColor = ColorUtils.compositeColors(color, windowBackground)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = ColorUtils.calculateLuminance(opaqueColor) > 0.5
    }

    fun applyToolbarStatusBarInsets(toolbar: View) {
        val lp = toolbar.layoutParams
        if (lp != null && lp.height > 0 && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            if (toolbar.minimumHeight <= 0) {
                toolbar.minimumHeight = lp.height
            }
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            toolbar.layoutParams = lp
        }

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, windowInsets ->
            val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = top)
            // 消费顶部 inset，避免嵌套子页重复加 padding
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(toolbar)
    }

    fun applyContentStatusBarInsets(content: View) {
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, windowInsets ->
            val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.updatePadding(top = top)
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .build()
        }
        ViewCompat.requestApplyInsets(content)
    }
}
