package moe.chenxy.miuiextra.hooker.entity.systemui

import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.View
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.defined.VagueType
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.FloatType
import com.highcapable.yukihookapi.hook.type.java.IntType
import de.robv.android.xposed.XposedHelpers

object StatusBarBlurUtilsHooker : YukiBaseHooker() {
    override fun onHook() {
        // Do Blur Scale when apply Blur
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
                        val transaction = method { name = "createTransaction" }.get(this.instance)
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
                                    ratio!! / 3
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

}