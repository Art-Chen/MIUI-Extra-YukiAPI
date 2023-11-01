package moe.chenxy.miuiextra.hooker.entity.systemui

import android.view.View
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.systemui.StatusBarBlurUtilsHooker.toClass

object MiBlurCompatUtils {
    private val blurCompatCls = "com.miui.systemui.util.MiBlurCompat".toClass()

    @JvmStatic
    fun View.setPassWindowBlurEnabledCompat(enable: Boolean) = XposedHelpers.callStaticMethod(
        blurCompatCls, "setPassWindowBlurEnabledCompat", this, enable)

    @JvmStatic
    fun View.setMiBackgroundBlurModeCompat(mode: Int) = XposedHelpers.callMethod(
        this, "setMiBackgroundBlurMode", mode)

    @JvmStatic
    fun View.setMiBackgroundBlurRadius(radius: Int) =
        XposedHelpers.callMethod(this, "setMiBackgroundBlurRadius", radius)

    @JvmStatic
    fun View.setMiViewBlurMode(mode: Int) =
        XposedHelpers.callMethod(this, "setMiViewBlurMode", mode)

    @JvmStatic
    fun View.isSupportMiBlur() =
        XposedHelpers.callStaticMethod(blurCompatCls, "getBackgroundBlurOpened", this.context) as Boolean
}