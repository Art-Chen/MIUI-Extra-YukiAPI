package moe.chenxy.miuiextra

import android.animation.ValueAnimator
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage


class SystemUIPluginHook {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        var pluginLoaderClassLoader: ClassLoader? = null

        fun initPluginHook() {
            var mDialogView: LinearLayout? = null
            val mDialogAnimator = ValueAnimator()
            mDialogAnimator.addUpdateListener {
                mDialogView?.translationY = it.animatedValue as Float
            }
            mDialogAnimator.duration = 800
            mDialogAnimator.interpolator = PathInterpolator(0.39f, 1.48f, 0.44f, 1.07f)
            fun animateVolumeView(isTop: Boolean) {
                if (mDialogAnimator.isRunning)
                    mDialogAnimator.cancel()

                mDialogAnimator.setFloatValues(mDialogView!!.translationY, if (isTop) -25f else 25f, 0f)
                mDialogAnimator.start()
            }
            var lastIsTopHaptic = false
            var lastIsBottomHaptic = false
            var lastIsContinueHaptic = false
            XposedHelpers.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl$1",
                pluginLoaderClassLoader,
                "onPerformHapticFeedback",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val i = param.args[0] as Int
                        val thiz = XposedHelpers.getObjectField(param.thisObject, "this\$0")
                        mDialogView =
                            XposedHelpers.getObjectField(thiz, "mDialogView") as LinearLayout
                        val isContinueHaptic: Boolean = i and 1 > 0
                        val isTopHaptic: Boolean = i and 2 > 0
                        val isBottomHaptic: Boolean = i and 4 > 0
//                        Log.i("Art_Chen", "onPerformHapticFeedback isContinueHaptic:$isContinueHaptic, isTopHaptic: $isTopHaptic, isBottomHaptic: $isBottomHaptic")
                        if ((isTopHaptic || isBottomHaptic) && (lastIsBottomHaptic != isBottomHaptic || lastIsTopHaptic != isTopHaptic || lastIsContinueHaptic != isContinueHaptic)) {
                            animateVolumeView(isTopHaptic && !isBottomHaptic)
                        }
                        if ((isTopHaptic || isBottomHaptic) && !isContinueHaptic) {
                            animateVolumeView(isTopHaptic && !isBottomHaptic)
                        }
                        lastIsTopHaptic = isTopHaptic
                        lastIsBottomHaptic = isBottomHaptic
                    }

                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        super.afterHookedMethod(param)
                    }
                })
        }
        if (mainPrefs.getBoolean("use_chen_volume_animation",false)) {
            XposedHelpers.findAndHookMethod("com.android.systemui.shared.plugins.PluginInstance\$Factory",
                lpparam.classLoader,
                "getClassLoader",
                ApplicationInfo::class.java,
                ClassLoader::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val applicationInfo = param.args[0] as ApplicationInfo
                        if (applicationInfo.packageName == "miui.systemui.plugin") {
                            if (pluginLoaderClassLoader != param.result as ClassLoader) {
                                Log.i("Art_Chen", "ClassLoader Changed! re-init hook for SystemUIPlugin")
                                pluginLoaderClassLoader = param.result as ClassLoader
                                initPluginHook()
                            }
                        }
                    }
                })
        }


    }
}