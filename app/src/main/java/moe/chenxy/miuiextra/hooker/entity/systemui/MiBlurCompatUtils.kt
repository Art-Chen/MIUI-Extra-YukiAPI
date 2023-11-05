package moe.chenxy.miuiextra.hooker.entity.systemui

import android.view.View
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.systemui.MiBlurCompatUtils.isSupportMiBlur
import moe.chenxy.miuiextra.hooker.entity.systemui.StatusBarBlurUtilsHooker.toClass
import moe.chenxy.miuiextra.utils.ChenUtils

object MiBlurCompatUtils {
    private val blurCompatCls = "com.miui.systemui.util.MiBlurCompat".toClassOrNull()

    @JvmStatic
    fun View.setPassWindowBlurEnabledCompat(enable: Boolean) =
        blurCompatCls?.let {
            XposedHelpers.callStaticMethod(
                it, "setPassWindowBlurEnabledCompat", this, enable
            )
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
        blurCompatCls?.let {
            XposedHelpers.callStaticMethod(
                it,
                "getBackgroundBlurOpened",
                this.context
            ) as Boolean
        } ?: false
}