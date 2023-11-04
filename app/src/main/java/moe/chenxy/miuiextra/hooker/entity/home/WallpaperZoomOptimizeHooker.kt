package moe.chenxy.miuiextra.hooker.entity.home

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.Display
import androidx.dynamicanimation.animation.DynamicAnimation
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.IBinderClass
import com.highcapable.yukihookapi.hook.type.android.IntentClass
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.FloatType
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.MiuiHomeHook
import moe.chenxy.miuiextra.hooker.entity.systemui.SystemUIPluginHook.hook
import moe.chenxy.miuiextra.utils.ChenUtils
import java.util.concurrent.AbstractExecutorService


object WallpaperZoomOptimizeHooker : YukiBaseHooker() {
    private var mSpringAnimation: Any? = null
    private var zoomInStiffness =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_stiffness_val", 3263).toFloat() / 100
    private var zoomOutStiffness =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_stiffness_val", 3263).toFloat() / 100
    private var zoomOutStartVelocity =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_start_velocity_val", 662).toFloat() / 1000
    private var zoomInStartVelocity =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_start_velocity_val", 0).toFloat() / 1000
    private var zoomOut =
        MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_val", 4).toFloat() / 10
    private var wallpaperZoomThiz: Any? = null
    private var mZoomedIn = false
    private var mZoomedOut = 1.0f

    private var isInHome = true
    private var lastScreenState = Display.STATE_ON
    private var disableCancelWallpaperAnim = false

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onHook() {
        "com.miui.home.launcher.wallpaper.WallpaperZoomManager".toClass().apply {
            method {
                name = "animateZoomOutTo"
                param(FloatType, BooleanType)
            }.hook {
                replaceUnit {
                    wallpaperZoomThiz = this.instance
                    var f = this.args[0] as Float
                    val zoomIn = this.args[1] as Boolean

                    mZoomedIn = zoomIn
                    if (mZoomedOut == f) {
                        return@replaceUnit
                    }
                    XposedHelpers.setObjectField(this.instance, "mZoomOut", mZoomedOut)

                    if (MiuiHomeHook.zoomPrefs.hasFileChanged()) {
                        MiuiHomeHook.zoomPrefs.reload()
                        zoomInStiffness =
                            MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_stiffness_val", 3263)
                                .toFloat() / 100
                        zoomOutStiffness =
                            MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_stiffness_val", 3263)
                                .toFloat() / 100
                        zoomOutStartVelocity =
                            MiuiHomeHook.zoomPrefs.getInt(
                                "wallpaper_zoomOut_start_velocity_val",
                                662
                            )
                                .toFloat() / 1000
                        zoomOut =
                            MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_val", 1).toFloat() / 10
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
                        DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE
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
                        XposedHelpers.callMethod(mZoomInSpringForce, "setDampingRatio", 1.5f)

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
                        XposedHelpers.callMethod(mZoomOutSpringForce, "setDampingRatio", 1.5f)

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
                    val runnable =
                        "com.miui.home.launcher.wallpaper.WallpaperZoomManager\$animateZoomOutTo\$1".toClass().declaredConstructors[0]
                    runnable.isAccessible = true

                    val mSpringAnimation = XposedHelpers.getObjectField(this.instance, "mSpringAnimation")
                    val canStop = XposedHelpers.callMethod(mSpringAnimation, "isRunning") as Boolean
                            && XposedHelpers.callMethod(mSpringAnimation, "canSkipToEnd") as Boolean
                    if (canStop) {
                        disableCancelWallpaperAnim = false
                        XposedHelpers.callStaticMethod("com.miui.home.launcher.animate.SpringAnimationReflectUtils".toClass(), "cancel", mSpringAnimation)
                    }

                    MAIN_THREAD_EXECUTOR.execute(
                        runnable.newInstance(
                            this.instance,
                            f
                        ) as Runnable
                    )
                }
            }

            constructor {
                param(ContextClass, IBinderClass)
            }.hook {
                after {
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


            method {
                name = "setWallpaperZoomOut"
                param(FloatType)
            }.hook {
                replaceUnit {
                    val mSpringAnimation = XposedHelpers.getObjectField(this.instance, "mSpringAnimation")
                    disableCancelWallpaperAnim = true
                    val f = this.args[0] as Float
                    mZoomedOut = f.coerceIn(0f, 1f)
                    val mWallpaperManager = XposedHelpers.getObjectField(this.instance, "mWallpaperManager")
                    val mWindowToken = XposedHelpers.getObjectField(this.instance, "mWindowToken")
                    XposedHelpers.callMethod(mWallpaperManager, "setWallpaperZoomOut", arrayOf(IBinderClass, FloatType), mWindowToken, mZoomedOut)
                    disableCancelWallpaperAnim = false

                    val canStop = XposedHelpers.callMethod(mSpringAnimation, "isRunning") as Boolean
                            && XposedHelpers.callMethod(mSpringAnimation, "canSkipToEnd") as Boolean
                    if (((mZoomedIn && mZoomedOut < zoomOut + 0.0015051) || (!mZoomedIn && mZoomedOut >= 0.9999999f)) && canStop) {
                        mZoomedOut = if (mZoomedIn) zoomOut else 1f
                        XposedHelpers.callStaticMethod("com.miui.home.launcher.animate.SpringAnimationReflectUtils".toClass(), "cancel", mSpringAnimation)
                    }
                }
            }
        }

//        "androidx.dynamicanimation.animation.SpringAnimation".hook {
//            injectMember {
//                method {
//                    name = "cancel"
//                }
//                beforeHook {
//                    if (this.instance == mSpringAnimation) this.result = null
//                }
//            }
//        }

        "com.miui.home.launcher.common.UnlockAnimationStateMachine".toClass().apply {
            method {
                name = "onDisplayChange"
            }.hook {
                before {
                    MiuiHomeHook.zoomPrefs.reload()
                    val mLauncher = XposedHelpers.getObjectField(this.instance, "mLauncher")
                    val screenState = XposedHelpers.callStaticMethod(
                        "com.miui.home.launcher.common.Utilities".toClass(),
                        "getDisplayState",
                        mLauncher
                    )
                    Log.i("Art_Chen", "onDisplayChanged!! current Status: $screenState")
                    if (lastScreenState == screenState) {
                        Log.v("Art_Chen", "screenState not changed, ignore wallpaper auto zoom")
                        return@before
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

            method {
                name = "onUserPresent"
            }.hook {
                after {
                    // Reset Zoom State after unlock
                    XposedHelpers.callMethod(
                        wallpaperZoomThiz,
                        "animateWallpaperZoom",
                        !isInHome
                    )
                }
            }
        }

        "com.miui.home.launcher.animate.SpringAnimationReflectUtils".toClass().method {
            name = "cancel"
        }.hook {
            before {
                if (disableCancelWallpaperAnim) {
                    this.result = null
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
            XposedHelpers.findMethodExact("com.miui.home.launcher.Launcher", appClassLoader, "animateWallpaperZoom",
                Boolean::class.javaPrimitiveType).hook {
                before {
                    if (isLaunching) {
                        Log.i(
                            "Art_Chen",
                            "sync_wallpaper_and_app_anim enabled, ignore wallpaper zoom when launch"
                        )
                        this.result = null
                    }
                }
            }

            if (!ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)) {
                XposedHelpers.findMethodExact(
                    "com.miui.home.launcher.Launcher", appClassLoader, "launch",
                    "com.miui.home.launcher.ShortcutInfo".toClass(), ViewClass
                ).hook {
                    before {
                        isLaunching = true
                    }
                    after {
                        isLaunching = false
                    }
                }
            } else {
                // on U, we just skip if using startActivity
                XposedHelpers.findMethodExact(
                    "com.miui.home.launcher.Launcher", appClassLoader, "superStartActivity",
                    IntentClass, BundleClass
                ).hook {
                    before {
                        isLaunching = true
                    }
                    after {
                        isLaunching = false
                    }
                }
            }

//            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher",
//                appClassLoader,
//                "animateWallpaperZoom",
//                Boolean::class.javaPrimitiveType,
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun beforeHookedMethod(param: MethodHookParam) {
//                        if (isLaunching) {
//                            Log.i(
//                                "Art_Chen",
//                                "sync_wallpaper_and_app_anim enabled, ignore wallpaper zoom when launch"
//                            )
//                            param.result = null
//                        }
//                    }
//
//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        super.afterHookedMethod(param)
//                    }
//                })

//            "com.miui.home.launcher.Launcher".toClass().apply {
//                method {
//                    name = "launch"
//                    param("com.miui.home.launcher.ShortcutInfo".toClass(), ViewClass)
//                }.hook {
//                    before {
//                        isLaunching = true
//                    }
//                    after {
//                        isLaunching = false
//                    }
//                }
//            }
        }
    }
}