package moe.chenxy.miuiextra.hooker.entity.systemui

import android.util.Log
import android.view.SurfaceControl
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.ObjectsClass
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.MiWallpaperHook
import java.util.Objects


object LinkageAnimCustomer : YukiBaseHooker() {

    override fun onHook() {
//        "com.android.systemui.shade.MiuiNotificationPanelViewController".toClass().apply {
//            method {
//                name = "linkageViewAnim"
//                param(BooleanType)
//            }.hook {
//                before {
//                    MiWallpaperHook.mainPrefs.reload()
//                    val on = MiWallpaperHook.mainPrefs.getInt("screen_on_color_fade_anim_val", 800)
//                    val off = MiWallpaperHook.mainPrefs.getInt("screen_off_color_fade_anim_val", 450)
//                    val blackHideEase = XposedHelpers.getObjectField(this.instance, "blackHideEase")
//
//
//                    XposedHelpers.callMethod(blackHideEase, "setDuration", off.toLong())
//                }
//            }
//        }

        "com.android.keyguard.clock.animation.ClockBaseAnimation".toClass().apply {
            var listener: Any? = null
            var animConfig: Any? = null
            var showEase: Any? = null
            fun initAnim() {
                if (listener != null) return

                listener = "com.android.keyguard.clock.animation.ClockBaseAnimation\$1".toClass().getDeclaredConstructor(
                    SurfaceControl.Transaction::class.java, IntType).newInstance(SurfaceControl.Transaction(), 1 /* toLock */)
                showEase = "miuix.animation.utils.EaseManager\$InterpolateEaseStyle".toClass().declaredConstructors[0].newInstance(20, floatArrayOf(1.0f))

                animConfig = "miuix.animation.base.AnimConfig".toClass().declaredConstructors[0].newInstance()
                val listeners = XposedHelpers.getObjectField(animConfig, "listeners") as HashSet<Any>
                listeners.add(listener!!)
            }

            method {
                name = "doAnimationToAod"
                paramCount = 3
            }.hook {
                before {
                    val fromKeyguard = this.args[2] as Boolean
                    if (!fromKeyguard) return@before

                    val toAod = this.args[0] as Boolean
                    val hasNotification = this.args[1] as Boolean


                    MiWallpaperHook.mainPrefs.reload()
                    val on = MiWallpaperHook.mainPrefs.getInt("screen_on_color_fade_anim_val", 800)
                    val off =
                        MiWallpaperHook.mainPrefs.getInt("screen_off_color_fade_anim_val", 450)

                    if (toAod) {
                        val mWallpaperHideEase =
                            XposedHelpers.getObjectField(this.instance, "mWallpaperHideEase")
                        XposedHelpers.callMethod(mWallpaperHideEase, "setDuration", off.toLong())
                    } else {
                        initAnim()
                        XposedHelpers.setBooleanField(this.instance, "mToAod", toAod)
                        XposedHelpers.setBooleanField(this.instance, "mHasNotification", hasNotification)

                        Log.d("Art_Chen", "ClockBaseAnimation-LinkageAnim: toLock start! hasNotification $hasNotification")

                        XposedHelpers.callMethod(showEase, "setDuration", on.toLong())
                        val stateStyle = XposedHelpers.callStaticMethod("miuix.animation.Folme".toClass(), "useValue", arrayOf("WallpaperParam"))
                        XposedHelpers.callMethod(animConfig, "setEase", showEase)
                        XposedHelpers.callMethod(stateStyle, "to", arrayOf("wallpaperBlack", 0f, animConfig))

                        XposedHelpers.callMethod(this.instance, "doAnimationToAod", false, hasNotification)
                        this.result = null
                    }
                }

            }
        }
    }
}