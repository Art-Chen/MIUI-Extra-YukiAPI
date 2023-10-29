package moe.chenxy.miuiextra.hooker.entity.systemui

import android.util.Log
import android.view.SurfaceControl
import android.view.View
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.highcapable.yukihookapi.hook.type.defined.VagueType
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.FloatType
import com.highcapable.yukihookapi.hook.type.java.IntType
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig

val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
val useBlurScale = mainPrefs.getBoolean("use_blur_scale_effect", false)
val disableMiBlur = mainPrefs.getBoolean("force_disable_mi_blur_effect", false)
val blurScaleVal = mainPrefs.getInt("blur_scale_val", 7)
object StatusBarBlurUtilsHooker : YukiBaseHooker() {

    override fun onHook() {
        // Do Blur Scale when apply Blur
        if (useBlurScale) {
            "com.android.systemui.statusbar.BlurUtils".toClass().apply {
                method {
                    name = "applyBlur"
                    param(VagueType, IntType, BooleanType)
                }.hook {
                    before {
                        val viewRootImpl = this.args[0] ?: return@before
                        val surfaceControl = XposedHelpers.callMethod(
                            viewRootImpl,
                            "getSurfaceControl"
                        ) as SurfaceControl

                        if (surfaceControl.isValid) {
                            val transaction =
                                method { name = "createTransaction" }.get(this.instance)
                                    .invoke<SurfaceControl.Transaction>()
                            try {
                                if (method { name = "supportsBlursOnWindows" }.get(this.instance)
                                        .invoke<Any>() as Boolean
                                ) {
                                    val ratioOfBlurRadius =
                                        method { name = "ratioOfBlurRadius" }.get(this.instance)
                                    val ratio = ratioOfBlurRadius.invoke<Float>(this.args[1])
                                    XposedHelpers.callMethod(
                                        transaction,
                                        "setBlurScaleRatio",
                                        surfaceControl,
                                        ratio!! / (10 - blurScaleVal)
                                    )
                                    transaction?.apply()
                                    transaction?.close()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }

        if (useBlurScale || disableMiBlur) {
            "com.android.systemui.statusbar.policy.BlurUtilsExt".toClass().method {
                name = "applyBlur"
                param(FloatType, ViewClass)
            }.hook {
                before {
                    val blurRatio = this.args[0] as Float
                    val view = this.args[1] as View
                    val useBlur = XposedHelpers.callMethod(this.instance, "getUseBlur") as Boolean
                    val blurCompatCls = "com.miui.systemui.util.MiBlurCompat".toClass()

                    if (useBlur) {
                        if (disableMiBlur) {
                            val dimColorRGB =
                                XposedHelpers.getIntField(this.instance, "dimColorRGB")
                            val dimColorAlpha =
                                XposedHelpers.getIntField(this.instance, "dimColorAlpha")
                            val blurUtils = XposedHelpers.getObjectField(this.instance, "blurUtils")
                            val blurRadius = (XposedHelpers.callMethod(
                                blurUtils,
                                "blurRadiusOfRatio",
                                blurRatio.coerceIn(0f, 1f)
                            ) as Float).toInt()
                            val newAlpha = (dimColorAlpha * blurRatio.coerceIn(0f, 1f)).toInt()
                            val newColor = dimColorRGB or (newAlpha shl 24)

                            XposedHelpers.callStaticMethod(
                                blurCompatCls,
                                "setPassWindowBlurEnabledCompat",
                                view,
                                false
                            )

                            view.setBackgroundColor(newColor)
                            val viewRootImpl = XposedHelpers.callMethod(view, "getViewRootImpl")
                            XposedHelpers.callMethod(
                                blurUtils,
                                "applyBlur",
                                viewRootImpl,
                                blurRadius,
                                false
                            )
                            this.result = null
                        }

                    }

                }

                after {
                    val blurRatio = this.args[0] as Float
                    val view = this.args[1] as View
                    val useBlur = XposedHelpers.callMethod(this.instance, "getUseBlur") as Boolean
                    val blurCompatCls = "com.miui.systemui.util.MiBlurCompat".toClass()

                    if (useBlur && useBlurScale) {
                        val isMiBlur = XposedHelpers.callStaticMethod(blurCompatCls, "getBackgroundBlurOpened", view.context) as Boolean
                        if (isMiBlur && view.isAttachedToWindow) {
                            try {
                                XposedHelpers.callMethod(view, "setMiBackgroundBlurScaleRatio", blurRatio / (15 - blurScaleVal))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }

    }

    object ControlCenterWindowViewHooker : YukiBaseHooker() {
        override fun onHook() {
            if (disableMiBlur) {
                "miui.systemui.controlcenter.windowview.ControlCenterWindowViewController".toClass()
                    .method {
                        name = "getThemeRes"
                    }.hook {
                    val classicCCStyleID = XposedHelpers.getStaticIntField(
                        "miui.systemui.controlcenter.R\$style".toClass(),
                        "ControlCenter"
                    )
                    replaceTo(classicCCStyleID)
                }
            }
        }

    }
}
