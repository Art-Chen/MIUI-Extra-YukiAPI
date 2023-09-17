package moe.chenxy.miuiextra.hooker.entity.home

import android.R.attr.classLoader
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.Display
import androidx.dynamicanimation.animation.DynamicAnimation
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.IBinderClass
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.FloatType
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.MiuiHomeHook
import java.util.concurrent.AbstractExecutorService


object WallpaperZoomOptimizeHooker : YukiBaseHooker() {
    var mSpringAnimation: Any? = null
    var zoomInStiffness =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_stiffness_val", 3263).toFloat() / 100
    var zoomOutStiffness =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_stiffness_val", 3263).toFloat() / 100
    var zoomOutStartVelocity =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_start_velocity_val", 662).toFloat() / 1000
    var zoomInStartVelocity =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_start_velocity_val", 0).toFloat() / 1000
    private var zoomOut =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_val", 4).toFloat() / 10
    var wallpaperZoomThiz: Any? = null
    var mZoomedIn = false
    var mZoomedOut = 1.0f

    var isInHome = true
    var lastScreenState = Display.STATE_ON

    override fun onHook() {
        "com.miui.home.launcher.wallpaper.WallpaperZoomManager".hook {
            injectMember {
                method {
                    name = "animateZoomOutTo"
                    param(FloatType, BooleanType)
                }
                replaceUnit {
                    wallpaperZoomThiz = this.instance
                    var f = this.args[0] as Float
                    val zoomIn = this.args[1] as Boolean

                    mZoomedIn = zoomIn
                    val mZoomOut = XposedHelpers.getFloatField(this.instance, "mZoomOut")
                    if (mZoomOut == f) {
                        return@replaceUnit
                    }

                    if (MiuiHomeHook.zoomPrefs.hasFileChanged()) {
                        MiuiHomeHook.zoomPrefs.reload()
                        zoomInStiffness =
                            MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_stiffness_val", 3263)
                                .toFloat() / 100
                        zoomOutStiffness =
                            MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_stiffness_val", 3263)
                                .toFloat() / 100
                        zoomOutStartVelocity =
                            MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_start_velocity_val", 662)
                                .toFloat() / 1000
                        zoomOut = MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_val", 4).toFloat() / 10
                        zoomInStartVelocity =
                            MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_start_velocity_val", 0)
                                .toFloat() / 1000
                    }

                    if (f == 0.6f) {
                        f = zoomOut
                    }

                    mSpringAnimation =
                        XposedHelpers.getObjectField(this.instance, "mSpringAnimation")
                    val mCurrentVelocity = XposedHelpers.getObjectField(
                        this.instance,
                        "mCurrentVelocity"
                    ) as Float
                    XposedHelpers.callMethod(
                        mSpringAnimation,
                        "setMinimumVisibleChange",
                        DynamicAnimation.MIN_VISIBLE_CHANGE_ALPHA
                    )
                    if (zoomIn) {
                        XposedHelpers.callMethod(
                            mSpringAnimation,
                            "setStartVelocity",
                            if (zoomInStartVelocity == 0f) mCurrentVelocity else zoomInStartVelocity
                        )
                        val mZoomInSpringForce =
                            XposedHelpers.getObjectField(this.instance, "mZoomInSpringForce")
                        XposedHelpers.callMethod(
                            mZoomInSpringForce,
                            "setStiffness",
                            zoomInStiffness
                        )
                        XposedHelpers.callMethod(mZoomInSpringForce, "setDampingRatio", 0.99f)

                        XposedHelpers.callMethod(mZoomInSpringForce, "setFinalPosition", f)
                        XposedHelpers.callMethod(
                            mSpringAnimation,
                            "setSpring",
                            mZoomInSpringForce
                        )
                    } else {
                        XposedHelpers.callMethod(
                            mSpringAnimation,
                            "setStartVelocity",
                            zoomOutStartVelocity
                        )
                        val mZoomOutSpringForce = XposedHelpers.getObjectField(
                            this.instance,
                            "mZoomOutSpringForce"
                        )
                        XposedHelpers.callMethod(
                            mZoomOutSpringForce,
                            "setStiffness",
                            zoomOutStiffness
                        )
                        XposedHelpers.callMethod(mZoomOutSpringForce, "setDampingRatio", 0.99f)

                        XposedHelpers.callMethod(mZoomOutSpringForce, "setFinalPosition", f)
                        XposedHelpers.callMethod(
                            mSpringAnimation,
                            "setSpring",
                            mZoomOutSpringForce
                        )
                    }
                    val MAIN_THREAD_EXECUTOR = XposedHelpers.getStaticObjectField(
                        "com.miui.home.recents.TouchInteractionService".toClass(),
                        "MAIN_THREAD_EXECUTOR"
                    ) as AbstractExecutorService
                    val runnable = "com.miui.home.launcher.wallpaper.WallpaperZoomManager\$animateZoomOutTo\$1".toClass().declaredConstructors[0]
                    runnable.isAccessible = true

                    MAIN_THREAD_EXECUTOR.execute(
                        runnable.newInstance(
                            this.instance,
                            f
                        ) as Runnable
                    )
                }
            }

            injectMember {
                method {
                    name = "access\$getMZoomOut\$p"
                    param("com.miui.home.launcher.wallpaper.WallpaperZoomManager")
                }
                replaceTo(mZoomedOut)
            }

            injectMember {
                constructor {
                    param(ContextClass, IBinderClass)
                }
                afterHook {
                    wallpaperZoomThiz = this.instance
                    val mContext = this.args[0] as Context
                    val chenActivitySwitchFilter =
                        IntentFilter("chen.action.top_activity.switched")
                    val chenActivitySwitchBroadcastReceiver = object : BroadcastReceiver() {
                        override fun onReceive(p0: Context?, p1: Intent?) {
                            if (p1?.action == "chen.action.top_activity.switched") {
                                isInHome = p1.getBooleanExtra("isEnteredHome", true)
                            }
                        }
                    }
                    mContext.registerReceiver(
                        chenActivitySwitchBroadcastReceiver,
                        chenActivitySwitchFilter
                    )
                }
            }

            injectMember {
                method {
                    name = "setWallpaperZoomOut"
                    param(FloatType)
                }
                afterHook {
                    mZoomedOut = this.args[0] as Float
                }
            }
        }

        "androidx.dynamicanimation.animation.SpringAnimation".hook {
            injectMember {
                method {
                    name = "cancel"
                }
                beforeHook {
                    if (this.instance == mSpringAnimation) this.result = null
                }
            }
        }

        "com.miui.home.launcher.common.UnlockAnimationStateMachine".hook {
            injectMember {
                method {
                    name = "onDisplayChange"
                }
                beforeHook {
                    MiuiHomeHook.zoomPrefs.reload()
                    val mLauncher = XposedHelpers.getObjectField(this.instance, "mLauncher")
                    val screenState = XposedHelpers.callStaticMethod(
                        "com.miui.home.launcher.common.Utilities".toClass(),
                        "getDisplayState",
                        mLauncher
                    )
                    Log.i("Art_Chen", "onDisplayChanged!! current Status: $screenState")
                    if (lastScreenState == screenState) {
                        Log.i("Art_Chen", "screenState not changed, ignore wallpaper auto zoom")
                        return@beforeHook
                    }
                    if (screenState == Display.STATE_DOZE || screenState == Display.STATE_DOZE_SUSPEND || screenState == Display.STATE_OFF) {
                        if (mZoomedIn) {
                            XposedHelpers.callMethod(
                                wallpaperZoomThiz,
                                "animateWallpaperZoom",
                                false
                            )
                        }
                    }
                    lastScreenState = screenState as Int
                }
            }

            injectMember {
                method {
                    name = "onUserPresent"
                }
                afterHook {
                    // Reset Zoom State after unlock
                    XposedHelpers.callMethod(
                        wallpaperZoomThiz,
                        "animateWallpaperZoom",
                        !isInHome
                    )
                }
            }
        }

//
//            XposedHelpers.findAndHookMethod("com.miui.home.launcher.wallpaper.WallpaperZoomManager",
//                lpparam!!.classLoader,
//                "access\$setMCurrentVelocity\$p",
//                "com.miui.home.launcher.wallpaper.WallpaperZoomManager",
//                Float::class.javaPrimitiveType,
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun beforeHookedMethod(param: MethodHookParam) {
//                        Log.i("Art_Chen", "currentVelocity ${param.args[1]}")
//                    }
//                })
////


//        var launcherThiz: Any? = null
        if (MiuiHomeHook.zoomPrefs.getBoolean("sync_wallpaper_and_app_anim", true)) {
            var isLaunching = false
            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher",
                appClassLoader,
                "animateWallpaperZoom",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isLaunching) {
                            Log.i(
                                "Art_Chen",
                                "sync_wallpaper_and_app_anim enabled, ignore wallpaper zoom when launch"
                            )
                            param.result = null
                        }
                    }

                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        super.afterHookedMethod(param)
                    }
                })

            "com.miui.home.launcher.Launcher".hook {
                injectMember {
                    method {
                        name = "launch"
                        param("com.miui.home.launcher.ShortcutInfo", ViewClass)
                    }
                    beforeHook {
                        isLaunching = true
//                        launcherThiz = this.instance
                    }
                    afterHook {
                        isLaunching = false
                    }
                }
//
//                injectMember {
//                    method {
//                        name = "animateWallpaperZoom"
//                        param(BooleanType)
//                    }
//                    beforeHook {
////                        launcherThiz = this.instance
//
//                    }
//                }
//            }

//                "com.miui.home.recents.LauncherAnimationRunner".hook {
//                    injectMember {
//                        method {
//                            name = "onAnimationStart"
//                            param(IntType, VagueType, VagueType, VagueType, Runnable::class.java)
//                        }
//                        beforeHook {
//                            Log.i("Art_Chen", "onAnimationStart")
//                        }
//                    }
//                }
            }
        }
    }
}