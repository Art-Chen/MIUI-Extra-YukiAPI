package moe.chenxy.miuiextra.hooker.entity.systemui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.SurfaceControl
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.FloatType
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
        var mWallpaperService: Service? = null
        "com.android.keyguard.wallpaper.MiuiKeyguardWallPaperManager".toClass().apply {
            method {
                name = "initKeyguardWallpaperManager"
            }.hook {
                after {
                    mWallpaperService = XposedHelpers.getObjectField(XposedHelpers.getObjectField(this.instance, "mMiuiWallpaperManager"), "mService") as Service
                }
            }
        }

        "com.android.keyguard.clock.animation.ClockBaseAnimation".toClass().apply {
            var listener: Any? = null
            var toAodListener: Any? = null
            var animConfig: Any? = null
            var toAodAnimConfig: Any? = null
            var showEase: Any? = null
            var mContext: Context? = null
            fun initAnim() {
                if (listener != null) return

                listener = "com.android.keyguard.clock.animation.ClockBaseAnimation\$1".toClass().getDeclaredConstructor(
                    SurfaceControl.Transaction::class.java, IntType).newInstance(SurfaceControl.Transaction(), 1 /* toLock */)
                toAodListener = "com.android.keyguard.clock.animation.ClockBaseAnimation\$1".toClass().getDeclaredConstructor(
                    SurfaceControl.Transaction::class.java, IntType).newInstance(SurfaceControl.Transaction(), 0 /* toAod */)
                showEase = "miuix.animation.utils.EaseManager\$InterpolateEaseStyle".toClass().declaredConstructors[0].newInstance(20, floatArrayOf(1.0f))

                animConfig = "miuix.animation.base.AnimConfig".toClass().declaredConstructors[0].newInstance()
                toAodAnimConfig = "miuix.animation.base.AnimConfig".toClass().declaredConstructors[0].newInstance()
                val listeners = XposedHelpers.getObjectField(animConfig, "listeners") as HashSet<Any>
                val aodListeners = XposedHelpers.getObjectField(toAodAnimConfig, "listeners") as HashSet<Any>
                listeners.add(listener!!)
                aodListeners.add(toAodListener!!)
            }

            method {
                name = "doAnimationToAod"
                paramCount = 3
            }.hook {
                replaceUnit {
                    val fromKeyguard = this.args[2] as Boolean
                    if (!fromKeyguard) return@replaceUnit

                    val toAod = this.args[0] as Boolean
                    val hasNotification = this.args[1] as Boolean

                    if (mContext == null)
                        mContext = XposedHelpers.getObjectField(XposedHelpers.getObjectField(this.instance, "mMiuiClockController"), "mContext") as Context
                    val intent = Intent("chen.action.show_wallpaper_anim")
                    intent.`package` = "com.miui.miwallpaper"
                    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    intent.putExtra("toAod", toAod)
                    mContext!!.sendBroadcast(intent)

                    MiWallpaperHook.mainPrefs.reload()
                    val on = MiWallpaperHook.mainPrefs.getInt("screen_on_color_fade_anim_val", 800)
                    val off =
                        MiWallpaperHook.mainPrefs.getInt("screen_off_color_fade_anim_val", 450)
                    val chenAnimLinkage = MiWallpaperHook.mainPrefs.getBoolean("lineage_aod_chen_wallpaper_anim", false)

                    initAnim()
                    Log.d("Art_Chen", "ClockBaseAnimation-LinkageAnim: start! toAod $toAod hasNotification $hasNotification")
                    XposedHelpers.setBooleanField(this.instance, "mToAod", toAod)
                    XposedHelpers.setBooleanField(this.instance, "mHasNotification", hasNotification)

                    if (toAod) {
                        val mWallpaperHideEase =
                            XposedHelpers.getObjectField(this.instance, "mWallpaperHideEase")
                        XposedHelpers.callMethod(mWallpaperHideEase, "setDuration", if (chenAnimLinkage) off.toLong() * 2 else off.toLong())

                        val stateStyle = XposedHelpers.callStaticMethod("miuix.animation.Folme".toClass(), "useValue", arrayOf("WallpaperParam"))
                        XposedHelpers.callMethod(toAodAnimConfig, "setEase", mWallpaperHideEase)
                        XposedHelpers.callMethod(stateStyle, "to", arrayOf("wallpaperBlack", if (chenAnimLinkage) 0.8f else 1f, toAodAnimConfig))
                    } else {
                        XposedHelpers.callMethod(showEase, "setDuration", on.toLong())
                        val stateStyle = XposedHelpers.callStaticMethod("miuix.animation.Folme".toClass(), "useValue", arrayOf("WallpaperParam"))
                        XposedHelpers.callMethod(animConfig, "setEase", showEase)
                        XposedHelpers.callMethod(stateStyle, "to", arrayOf("wallpaperBlack", 0f, animConfig))
                    }
                    XposedHelpers.callMethod(this.instance, "doAnimationToAod", toAod, hasNotification)
                }
                // hide when time alpha 0
//                method {
//                    name = "updateTimeViewAlpha"
//                    param(FloatType)
//                }.hook {
//                    after {
//                        val chenAnimLinkage = MiWallpaperHook.mainPrefs.getBoolean("lineage_aod_chen_wallpaper_anim", false)
//                        if (chenAnimLinkage && this.args[0] as Float == 0f && listener != null) {
//                            val mWallpaperHideEase =
//                                XposedHelpers.getObjectField(this.instance, "mWallpaperHideEase")
//                            XposedHelpers.callMethod(mWallpaperHideEase, "setDuration", 1000L)
//
//                            val stateStyle = XposedHelpers.callStaticMethod("miuix.animation.Folme".toClass(), "useValue", arrayOf("WallpaperParam"))
//                            XposedHelpers.callMethod(toAodAnimConfig, "setEase", mWallpaperHideEase)
//
//                            XposedHelpers.callMethod(stateStyle, "to", arrayOf("wallpaperBlack", 1f, toAodAnimConfig))
//                        }
//                    }
//                }
            }
        }
    }
}