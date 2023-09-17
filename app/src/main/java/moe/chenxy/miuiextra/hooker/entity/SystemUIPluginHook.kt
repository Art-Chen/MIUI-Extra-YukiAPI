package moe.chenxy.miuiextra.hooker.entity

import android.animation.ValueAnimator
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.JavaClassLoader
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig


object SystemUIPluginHook : YukiBaseHooker() {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    override fun onHook() {
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

                mDialogAnimator.setFloatValues(
                    mDialogView!!.translationY,
                    if (isTop) -25f else 25f,
                    0f
                )
                mDialogAnimator.start()
            }

            var lastIsTopHaptic = false
            var lastIsBottomHaptic = false
            var lastIsContinueHaptic = false
            findClass("com.android.systemui.miui.volume.MiuiVolumeDialogImpl$1", pluginLoaderClassLoader).hook {
                injectMember {
                    method {
                        name = "onPerformHapticFeedback"
                        param(IntType)
                    }
                    beforeHook {
                        val i = this.args[0] as Int
                        val thiz = XposedHelpers.getObjectField(this.instance, "this\$0")
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
                }
            }
        }

        if (mainPrefs.getBoolean("use_chen_volume_animation", false)) {
            "com.android.systemui.shared.plugins.PluginInstance\$Factory".hook {
                injectMember {
                    method {
                        name = "getClassLoader"
                        param(ApplicationInfo::class.java, JavaClassLoader)
                    }
                    afterHook {
                        val applicationInfo = this.args[0] as ApplicationInfo
                        if (applicationInfo.packageName == "miui.systemui.plugin") {
                            if (pluginLoaderClassLoader != this.result as ClassLoader) {
                                Log.i(
                                    "Art_Chen",
                                    "ClassLoader Changed! re-init hook for SystemUIPlugin"
                                )
                                pluginLoaderClassLoader = this.result as ClassLoader
                                initPluginHook()
                            }
                        }
                    }
                }
            }
        }
    }
}