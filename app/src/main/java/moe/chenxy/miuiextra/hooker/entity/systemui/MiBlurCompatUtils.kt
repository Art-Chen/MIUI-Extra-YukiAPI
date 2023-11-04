package moe.chenxy.miuiextra.hooker.entity.systemui

import android.view.View
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.systemui.StatusBarBlurUtilsHooker.toClass
import moe.chenxy.miuiextra.utils.ChenUtils

object MiBlurCompatUtils {
    private const val blurCompatCls = "com.miui.systemui.util.MiBlurCompat"

    @JvmStatic
    fun View.setPassWindowBlurEnabledCompat(enable: Boolean) =
        if (ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)) {
            XposedHelpers.callStaticMethod(
                blurCompatCls.toClass(), "setPassWindowBlurEnabledCompat", this, enable
            )
        } else {

        }

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
        if (ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)) {
            XposedHelpers.callStaticMethod(
                blurCompatCls.toClass(),
                "getBackgroundBlurOpened",
                this.context
            ) as Boolean
        } else {
            false
        }
}