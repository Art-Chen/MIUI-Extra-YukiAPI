package moe.chenxy.miuiextra

import android.R.attr.classLoader
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.AbstractExecutorService


class MiuiHomeHook {
    var mAppToHomeAnim2Bak: Any? = null
    val mRunnable = Runnable {}
    val mDebug = false
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    val zoomPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_wallpaper_zoom_settings")
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        mainPrefs.reload()
        if (mainPrefs.getBoolean("miui_home_anim_enhance", false)) {
            // Don't cancel the AppToHome Anim if Action Down
            try {
                XposedHelpers.findAndHookMethod(
                    "com.miui.home.recents.NavStubView",
                    lpparam!!.classLoader,
                    "onInputConsumerEvent",
                    MotionEvent::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            mAppToHomeAnim2Bak =
                                XposedHelpers.getObjectField(param.thisObject, "mAppToHomeAnim2")
                            if (mAppToHomeAnim2Bak != null) {
                                XposedHelpers.setObjectField(
                                    param.thisObject,
                                    "mAppToHomeAnim2",
                                    null
                                )
                            }
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            val motionEvent = param.args[0] as MotionEvent
                            Log.v(
                                "Art_Chen",
                                "onInputConsumerEvent: Action: ${motionEvent.action}, return ${param.result}. x: ${motionEvent.x} y: ${motionEvent.y}"
                            )
                            val mAppToHomeAnim2 =
                                XposedHelpers.getObjectField(param.thisObject, "mAppToHomeAnim2")
                            if (mAppToHomeAnim2 == null && mAppToHomeAnim2Bak != null) {
                                XposedHelpers.setObjectField(
                                    param.thisObject,
                                    "mAppToHomeAnim2",
                                    mAppToHomeAnim2Bak
                                )
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.e("Art_Chen", "Phone Home optimize may not supported, ignored! err: ${e.message}")
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "com.miui.home.recents.GestureModeApp",
                    lpparam!!.classLoader,
                    "onInputConsumerEvent",
                    MotionEvent::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            mAppToHomeAnim2Bak =
                                XposedHelpers.getObjectField(param.thisObject, "mAppToHomeAnim2")
                            if (mAppToHomeAnim2Bak != null) {
                                XposedHelpers.setObjectField(param.thisObject, "mAppToHomeAnim2", null)
                            }
                        }

                        override fun afterHookedMethod(param: MethodHookParam) {
                            val motionEvent = param.args[0] as MotionEvent
                            Log.v(
                                "Art_Chen",
                                "onInputConsumerEvent: Action: ${motionEvent.action}, return ${param.result}. x: ${motionEvent.x} y: ${motionEvent.y}"
                            )
                            val mAppToHomeAnim2 =
                                XposedHelpers.getObjectField(param.thisObject, "mAppToHomeAnim2")
                            if (mAppToHomeAnim2 == null && mAppToHomeAnim2Bak != null) {
                                XposedHelpers.setObjectField(
                                    param.thisObject,
                                    "mAppToHomeAnim2",
                                    mAppToHomeAnim2Bak
                                )
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                Log.e("Art_Chen", "Pad optimize may not supported, ignored! err: ${e.message}")
            }

            // Don't run PerformClickRunnable early
            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.ItemIcon",
                lpparam!!.classLoader,
                "initPerformClickRunnable",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = mRunnable
                    }
                }
            )
        }
        var mSpringAnimation: Any? = null
        var zoomInStiffness = zoomPrefs.getInt("wallpaper_zoomIn_stiffness_val", 3263).toFloat() / 100
        var zoomOutStiffness = zoomPrefs.getInt("wallpaper_zoomOut_stiffness_val", 3263).toFloat() / 100
        var zoomOutStartVelocity =
            zoomPrefs.getInt("wallpaper_zoomOut_start_velocity_val", 662).toFloat() / 1000
        var zoomInStartVelocity =
            zoomPrefs.getInt("wallpaper_zoomIn_start_velocity_val", 0).toFloat() / 1000
        var zoomOut =
            zoomPrefs.getInt("wallpaper_zoomOut_val", 4).toFloat() / 10
        var wallpaperZoomThiz: Any? = null
        var mZoomedIn = false
        if (zoomPrefs.getBoolean("enable_wallpaper_zoom_optimize", false)) {
            XposedHelpers.findAndHookMethod("com.miui.home.launcher.wallpaper.WallpaperZoomManager",
                lpparam!!.classLoader,
                "animateZoomOutTo",
                Float::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodReplacement() {
                    @Throws(Throwable::class)
                    override fun replaceHookedMethod(param: MethodHookParam?) {
//                        Log.i("Art_Chen", "animateZoomOutTo")
                        wallpaperZoomThiz = param?.thisObject
                        var f = param!!.args[0] as Float
                        val zoomIn = param.args[1] as Boolean

                        mZoomedIn = zoomIn
                        val mZoomOut = XposedHelpers.getFloatField(param.thisObject, "mZoomOut")
                        if (mZoomOut == f) {
                            return
                        }

                        if (zoomPrefs.hasFileChanged()) {
                            zoomPrefs.reload()
                            zoomInStiffness = zoomPrefs.getInt("wallpaper_zoomIn_stiffness_val", 3263).toFloat() / 100
                            zoomOutStiffness = zoomPrefs.getInt("wallpaper_zoomOut_stiffness_val", 3263).toFloat() / 100
                            zoomOutStartVelocity =
                                zoomPrefs.getInt("wallpaper_zoomOut_start_velocity_val", 662).toFloat() / 1000
                            zoomOut = zoomPrefs.getInt("wallpaper_zoomOut_val", 4).toFloat() / 10
                            zoomInStartVelocity =
                                zoomPrefs.getInt("wallpaper_zoomIn_start_velocity_val", 0).toFloat() / 1000
                        }

                        if (f == 0.6f) {
                            f = zoomOut
                        }

                        mSpringAnimation =
                            XposedHelpers.getObjectField(param.thisObject, "mSpringAnimation")
                        val mCurrentVelocity = XposedHelpers.getObjectField(
                            param.thisObject,
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
                                XposedHelpers.getObjectField(param.thisObject, "mZoomInSpringForce")
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
                                param.thisObject,
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
                            Class.forName(
                                "com.miui.home.recents.TouchInteractionService",
                                false,
                                lpparam.classLoader
                            ),
                            "MAIN_THREAD_EXECUTOR"
                        ) as AbstractExecutorService
                        val runnable = Class.forName(
                            "com.miui.home.launcher.wallpaper.WallpaperZoomManager\$animateZoomOutTo\$1",
                            true,
                            lpparam.classLoader
                        ).declaredConstructors[0]
                        runnable.isAccessible = true

                        MAIN_THREAD_EXECUTOR.execute(
                            runnable.newInstance(
                                param.thisObject,
                                f
                            ) as Runnable
                        )
//                        Log.i("Art_Chen", "animateZoomOutTo End!")

                    }
                }
            )

            var mZoomedOut = 1.0f
            XposedHelpers.findAndHookMethod("com.miui.home.launcher.wallpaper.WallpaperZoomManager",
                lpparam.classLoader,
                "access\$getMZoomOut\$p",
                "com.miui.home.launcher.wallpaper.WallpaperZoomManager",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = mZoomedOut
                    }

//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        Log.i("Art_Chen", "getMZoomOut ${param.result}")
//                    }
                })

            XposedHelpers.findAndHookMethod(
                "androidx.dynamicanimation.animation.SpringAnimation",
                lpparam.classLoader,
                "cancel",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.thisObject == mSpringAnimation) {
//                            Log.i("Art_Chen", "Don't cancel Wallpaper scale anim")
                            param.result = 0
                        }
                    }

                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        super.afterHookedMethod(param)
                    }
                })
            var isInHome = true
            var lastScreenState = Display.STATE_ON
            XposedHelpers.findAndHookConstructor("com.miui.home.launcher.wallpaper.WallpaperZoomManager",
                lpparam.classLoader,
                Context::class.java,
                IBinder::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        wallpaperZoomThiz = param.thisObject
                        val mContext = param.args[0] as Context
                        val chenActivitySwitchFilter = IntentFilter("chen.action.top_activity.switched")
                        val chenActivitySwitchBroadcastReceiver = object : BroadcastReceiver() {
                            override fun onReceive(p0: Context?, p1: Intent?) {
                                if (p1?.action == "chen.action.top_activity.switched") {
                                    isInHome = p1.getBooleanExtra("isEnteredHome", true)
//                                    if (!isInHome && lastScreenState == Display.STATE_ON) {
//                                        XposedHelpers.callMethod(wallpaperZoomThiz, "animateWallpaperZoom", true)
//                                    }
                                }
                            }
                        }
                        mContext.registerReceiver(chenActivitySwitchBroadcastReceiver, chenActivitySwitchFilter)
                    }
                })
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
            XposedHelpers.findAndHookMethod("com.miui.home.launcher.wallpaper.WallpaperZoomManager",
                lpparam.classLoader,
                "setWallpaperZoomOut",
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {

                    }

                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
//                        Log.i("Art_Chen", "setWallpaperZoomOut ${param.args[0]}")
                        mZoomedOut = param.args[0] as Float
                    }
                })

            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.common.UnlockAnimationStateMachine",
                lpparam.classLoader,
                "onDisplayChange",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        zoomPrefs.reload()
                        val mLauncher = XposedHelpers.getObjectField(param.thisObject, "mLauncher")
                        val screenState = XposedHelpers.callStaticMethod(Class.forName("com.miui.home.launcher.common.Utilities", true, lpparam.classLoader), "getDisplayState", mLauncher)
                        Log.i("Art_Chen", "onDisplayChanged!! current Status: $screenState")
                        if (lastScreenState == screenState) {
                            Log.i("Art_Chen", "screenState not changed, ignore wallpaper auto zoom")
                            return
                        }
                        if (screenState == Display.STATE_DOZE || screenState == Display.STATE_DOZE_SUSPEND || screenState == Display.STATE_OFF) {
                            if (mZoomedIn) {
                                XposedHelpers.callMethod(wallpaperZoomThiz, "animateWallpaperZoom", false)
                            }
                        }
                        lastScreenState = screenState as Int
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.common.UnlockAnimationStateMachine",
                lpparam.classLoader,
                "onUserPresent",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedHelpers.callMethod(
                            wallpaperZoomThiz,
                            "animateWallpaperZoom",
                            !isInHome
                        )
                    }
                })

            var launcherThiz: Any? = null
            if (zoomPrefs.getBoolean("sync_wallpaper_and_app_anim", true)) {
                var isLaunching = false
                XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher",
                    lpparam.classLoader,
                    "launch",
                    "com.miui.home.launcher.ShortcutInfo",
                    View::class.java,
                    object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            isLaunching = true
                            launcherThiz = param.thisObject
                        }

                        @Throws(Throwable::class)
                        override fun afterHookedMethod(param: MethodHookParam) {
                            isLaunching = false
                        }
                    })

                XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher",
                    lpparam.classLoader,
                    "animateWallpaperZoom",
                    Boolean::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            launcherThiz = param.thisObject
                            if (isLaunching) {
                                Log.i("Art_Chen", "sync_wallpaper_and_app_anim enabled, ignore wallpaper zoom when launch")
                                param.result = 0
                            }
                        }

                        @Throws(Throwable::class)
                        override fun afterHookedMethod(param: MethodHookParam) {
                            super.afterHookedMethod(param)
                        }
                    })

                XposedHelpers.findAndHookMethod("com.miui.home.recents.LauncherAnimationRunner",
                    lpparam.classLoader,
                    "onAnimationStart",
                    Int::class.javaPrimitiveType,
                    "com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat[]",
                    "com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat[]",
                    "com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat[]",
                    Runnable::class.java,
                    object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            Log.i("Art_Chen", "onAnimationStart")
//                            val overviewState = XposedHelpers.getStaticObjectField(Class.forName("com.miui.home.launcher.LauncherState", true, lpparam.classLoader), "OVERVIEW")
//                            if (launcherThiz != null) {
//                                XposedHelpers.callMethod(launcherThiz, "animateWallpaperZoom", true)
//                            }
                        }

                    })
            }
        }

        if (mainPrefs.getBoolean("miui_unlock_anim_enhance", false)) {
            XposedHelpers.findAndHookMethod("com.miui.home.launcher.compat.UserPresentAnimationCompatV12Phone",
                lpparam?.classLoader,
                "getSpringAnimator",
                View::class.java,
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val springAnimation = param.result
                        if (param.args[2] == -1500.0f) {
                            XposedHelpers.callMethod(
                                springAnimation,
                                "setDampingResponse",
                                0.68f,
                                0.55f
                            )
                        }
                        param.result = springAnimation
                    }
                })
        }

        XposedHelpers.findAndHookMethod("com.miui.home.recents.util.RectFSpringAnim",
            lpparam!!.classLoader,
            "setAnimParamByType",
            "com.miui.home.recents.util.RectFSpringAnim\$AnimType",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val animTypesEnumCls = XposedHelpers.findClass("com.miui.home.recents.util.RectFSpringAnim\$AnimType", lpparam.classLoader)
                    val animTypesEnum = animTypesEnumCls.enumConstants
                    val animType = param.args[0]
                    val origCenterXStiffness =
                        XposedHelpers.getObjectField(param.thisObject, "mCenterXStiffness") as Float
                    val origCenterYStiffness =
                        XposedHelpers.getObjectField(param.thisObject, "mCenterYStiffness") as Float

                    if (animType == animTypesEnum[1]) {
                        XposedHelpers.setObjectField(
                            param.thisObject,
                            "mCenterXStiffness",
                            origCenterXStiffness / 1.7f
                        )
                        XposedHelpers.setObjectField(
                            param.thisObject,
                            "mCenterYStiffness",
                            origCenterYStiffness / 1.4f
                        )
                    } else if (animType == animTypesEnum[4]) {
                        XposedHelpers.setObjectField(
                            param.thisObject,
                            "mCenterXStiffness",
                            origCenterXStiffness * 1.2f
                        )
                        XposedHelpers.setObjectField(
                            param.thisObject,
                            "mCenterYStiffness",
                            origCenterYStiffness
                        )
                    }
                }
            })

        XposedHelpers.findAndHookMethod(
            "com.miui.home.recents.util.RectFSpringAnim",
            lpparam!!.classLoader,
            "cancel",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Exception().printStackTrace()
//                    param.result = 0
                }

                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    super.afterHookedMethod(param)
                }
            })

//        if (mDebug) {
//            XposedHelpers.findAndHookMethod("com.miui.maml.elements.AnimatedScreenElement",
//                lpparam.classLoader,
//                "onTouch",
//                MotionEvent::class.java,
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        val motionEvent = param.args[0] as MotionEvent
//                        Log.i(
//                            "Art_Chen",
//                            "AnimatedScreenElement: onTouch return ${param.result}, motionEvent action: ${motionEvent.actionMasked}. x: ${motionEvent.x} y: ${motionEvent.y}"
//                        )
//                        Exception().printStackTrace()
//                    }
//                })
//
//            XposedHelpers.findAndHookMethod(
//                "com.miui.maml.ActionCommand\$IntentCommand",
//                lpparam.classLoader,
//                "doPerform",
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        Log.i("Art_Chen", "maml Action doPerform!! next -> startActivity!")
//                        Exception().printStackTrace()
//                    }
//                })
//
//            XposedHelpers.findAndHookMethod("com.miui.home.recents.QuickstepAppTransitionManagerImpl",
//                lpparam.classLoader,
//                "getActivityLaunchOptions",
//                "com.miui.home.launcher.Launcher",
//                View::class.java,
//                Rect::class.java,
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        Exception().printStackTrace()
//                    }
//                })
//
//
//            XposedHelpers.findAndHookMethod("com.miui.maml.component.MamlView",
//                lpparam.classLoader,
//                "onTouchEvent",
//                MotionEvent::class.java,
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        val motionEvent = param.args[0] as MotionEvent
//                        Log.i(
//                            "Art_Chen",
//                            "MamlView: onTouchEvent return ${param.result}, motionEvent action: ${motionEvent.actionMasked}"
//                        )
//                    }
//                })
//
//            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher",
//                lpparam.classLoader,
//                "dispatchTouchEvent",
//                MotionEvent::class.java,
//                object : XC_MethodHook() {
//
//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        val motionEvent = param.args[0] as MotionEvent
//                        Log.i(
//                            "Art_Chen",
//                            "Launcher: dispatchTouchEvent return ${param.result}, motionEvent action: ${motionEvent.actionMasked}"
//                        )
//                        Exception().printStackTrace()
//                    }
//                })
//
////        XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher",
////            lpparam.classLoader,
////            "startActivity",
////            Intent::class.java,
////            Any::class.java,
////            View::class.java,
////            object : XC_MethodHook() {
////                @Throws(Throwable::class)
////                override fun afterHookedMethod(param: MethodHookParam) {
////                    Exception().printStackTrace()
////                }
////            })
//
//            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher",
//                lpparam.classLoader,
//                "onClick",
//                View::class.java,
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun afterHookedMethod(param: MethodHookParam) {
//                        Log.i("Art_Chen", "Launcher: onClick")
//                        Exception().printStackTrace()
//                    }
//                })
//
//            XposedHelpers.findAndHookMethod("com.miui.maml.elements.WindowScreenElement",
//                lpparam.classLoader,
//                "onVisibilityChange",
//                Boolean::class.javaPrimitiveType,
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun beforeHookedMethod(param: MethodHookParam) {
//                        val z = param.args[0]
//                        Log.i("Art_Chen", "onVisibilityChange ${z}")
//                        param.result = 0
//                    }
////
////                @Throws(Throwable::class)
////                override fun afterHookedMethod(param: MethodHookParam) {
////                    super.afterHookedMethod(param)
////                }
//                })
//        }
    }
}