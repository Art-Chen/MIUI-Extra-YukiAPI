package moe.chenxy.miuiextra.hooker.entity.systemui

import android.view.View
import com.highcapable.yukihookapi.hook.factory.toClassOrNull
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.systemui.MiBlurCompatUtils.isSupportMiBlur
import moe.chenxy.miuiextra.hooker.entity.systemui.StatusBarBlurUtilsHooker.toClass
import moe.chenxy.miuiextra.utils.ChenUtils

object MiBlurCompatUtils {
    var classLoader: ClassLoader? = null
    @JvmStatic
    fun View.setPassWindowBlurEnabledCompat(enable: Boolean) =
        "com.miui.systemui.util.MiBlurCompat".toClassOrNull(classLoader)?.let {
            XposedHelpers.callStaticMethod(
                it, "setPassWindowBlurEnabledCompat", this, enable
            )
        }

    @JvmStatic
    fun View.getPassWindowBlurEnabled() =
        XposedHelpers.callMethod(this, "getPassWindowBlurEnabled") as Boolean


    @JvmStatic
    fun View.setMiBackgroundBlurModeCompat(mode: Int) = XposedHelpers.callMethod(
        this, "setMiBackgroundBlurMode", mode)

    @JvmStatic
    fun View.getMiBackgroundBlurModeCompat() : Int = XposedHelpers.callMethod(
        this, "getMiBackgroundBlurMode") as Int

    @JvmStatic
    fun View.setMiBackgroundBlurRadius(radius: Int) =
        XposedHelpers.callMethod(this, "setMiBackgroundBlurRadius", radius)

    @JvmStatic
    fun View.setMiViewBlurMode(mode: Int) =
        XposedHelpers.callMethod(this, "setMiViewBlurMode", mode)

    @JvmStatic
    fun View.isSupportMiBlur() =
        "com.miui.systemui.util.MiBlurCompat".toClassOrNull(classLoader)?.let {
            XposedHelpers.callStaticMethod(
                it,
                "getBackgroundBlurOpened",
                this.context
            ) as Boolean
        } ?: false
}