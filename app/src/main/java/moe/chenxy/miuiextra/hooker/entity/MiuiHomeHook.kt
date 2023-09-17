package moe.chenxy.miuiextra.hooker.entity

import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.highcapable.yukihookapi.hook.type.java.FloatType
import com.highcapable.yukihookapi.hook.type.java.IntType
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.hooker.entity.home.IconLaunchAnimHooker
import moe.chenxy.miuiextra.hooker.entity.home.WallpaperZoomOptimizeHooker


object MiuiHomeHook : YukiBaseHooker() {
    var mAppToHomeAnim2Bak: Any? = null
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    val zoomPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_wallpaper_zoom_settings")

    enum class AnimType {
        BREAK_OPEN, OPEN_FROM_HOME, OPEN_FROM_RECENTS, CLOSE_TO_RECENTS, CLOSE_TO_HOME, CLOSE_FROM_FEED, APP_TO_APP, START_FIRST_TASK
    }


    override fun onHook() {
        mainPrefs.reload()
        if (mainPrefs.getBoolean("miui_home_anim_enhance", false)) {
            loadHooker(IconLaunchAnimHooker)
        }


        if (zoomPrefs.getBoolean("enable_wallpaper_zoom_optimize", false)) {
            loadHooker(WallpaperZoomOptimizeHooker)
        }

        if (mainPrefs.getBoolean("miui_unlock_anim_enhance", false)) {
            "com.miui.home.launcher.compat.UserPresentAnimationCompatV12Phone".hook {
                injectMember {
                    method {
                        name = "getSpringAnimator"
                        param(ViewClass, IntType, FloatType, FloatType, FloatType, FloatType)
                    }
                    afterHook {
                        val springAnimation = this.result
                        if (this.args[2] == -1500.0f) {
                            XposedHelpers.callMethod(
                                springAnimation,
                                "setDampingResponse",
                                0.68f,
                                0.55f
                            )
                        }
                        this.result = springAnimation
                    }
                }
            }
        }

//        // RectFSpringAnim Optimizer
//        "com.miui.home.recents.util.RectFSpringAnim".hook {
//            injectMember {
//                method {
//                    name = "setAnimParamByType"
//                    param("com.miui.home.recents.util.RectFSpringAnim\$AnimType")
//                }
//                afterHook {
//                    val animTypesEnumCls = "com.miui.home.recents.util.RectFSpringAnim\$AnimType".toClass()
//                    val animTypesEnum = animTypesEnumCls.enumConstants
//                    val animType = this.args[0]
//
//                    if (animType == animTypesEnum[AnimType.BREAK_OPEN.ordinal]) return@afterHook
//
//                    val mCenterXStiffness =
//                        XposedHelpers.getObjectField(this.instance, "mCenterXStiffness") as Float
//                    val mCenterYStiffness =
//                        XposedHelpers.getObjectField(this.instance, "mCenterYStiffness") as Float
//                    val mAlphaStiffness =
//                        XposedHelpers.getObjectField(this.instance, "mAlphaStiffness") as Float
//                    val mRatioVelocity =
//                        XposedHelpers.getObjectField(this.instance, "mRatioVelocity") as Float
//                    val mRatioStiffness =
//                        XposedHelpers.getObjectField(this.instance, "mRatioStiffness") as Float
//                    val mWidthStiffness =
//                        XposedHelpers.getObjectField(this.instance, "mWidthStiffness") as Float
//                    val mRatioMinVisibleChange =
//                        XposedHelpers.getObjectField(this.instance, "mRatioMinVisibleChange") as Float
//                    val mWidthMinVisibleChange =
//                        XposedHelpers.getObjectField(this.instance, "mWidthMinVisibleChange") as Float
//                    val mCenterYMinVisibleChange =
//                        XposedHelpers.getObjectField(this.instance, "mCenterYMinVisibleChange") as Float
//                    val mCenterXMinVisibleChange =
//                        XposedHelpers.getObjectField(this.instance, "mCenterXMinVisibleChange") as Float
//                    val mLeftVelocity =
//                        XposedHelpers.getObjectField(this.instance, "mLeftVelocity") as Float
//                    val mTopVelocity =
//                        XposedHelpers.getObjectField(this.instance, "mTopVelocity") as Float
//                    val mWidthVelocity =
//                        XposedHelpers.getObjectField(this.instance, "mWidthVelocity") as Float
//
//                    Log.i("Art_Chen", "setAnimParamByType: " +
//                            "mCenterXStiffness: $mCenterXStiffness, " +
//                            "mCenterYStiffness $mCenterYStiffness " +
//                            "mAlphaStiffness $mAlphaStiffness " +
//                            "mRatioVelocity $mRatioVelocity " +
//                            "mRatioStiffness $mRatioStiffness " +
//                            "mRatioMinVisibleChange $mRatioMinVisibleChange " +
//                            "mWidthMinVisibleChange $mWidthMinVisibleChange " +
//                            "mCenterYMinVisibleChange $mCenterYMinVisibleChange " +
//                            "mCenterXMinVisibleChange $mCenterXMinVisibleChange " +
//                            "mLeftVelocity $mLeftVelocity " +
//                            "mTopVelocity $mTopVelocity " +
//                            "mWidthVelocity $mWidthVelocity " +
//                            "mWidthStiffness $mWidthStiffness "
//                    )
//                    this.field {
//                        name("mWidthMinVisibleChange")
//                    }.get(this.instance).set(0.0001f)
//                    this.field {
//                        name("mRatioMinVisibleChange")
//                    }.get(this.instance).set(0.0001f)
//                    this.field {
//                        name("mCenterYMinVisibleChange")
//                    }.get(this.instance).set(0.0001f)
//                    this.field {
//                        name("mCenterXMinVisibleChange")
//                    }.get(this.instance).set(0.0001f)
//
//                    if (animType == animTypesEnum[AnimType.OPEN_FROM_HOME.ordinal]) {
//                        XposedHelpers.setObjectField(
//                            this.instance,
//                            "mCenterXStiffness",
//                            mCenterXStiffness * 1.05f
//                        )
//                        XposedHelpers.setObjectField(
//                            this.instance,
//                            "mCenterYStiffness",
//                            mCenterYStiffness * 1.25f
//                        )
////                        this.field {
////                            name("mLeftVelocity")
////                        }.get(this.instance).set(500f)
////                        this.field {
////                            name("mTopVelocity")
////                        }.get(this.instance).set(500f)
//                        this.field {
//                            name("mWidthVelocity")
//                        }.get(this.instance).set(11000f)
//                        this.field {
//                            name("mWidthStiffness")
//                        }.get(this.instance).set(mWidthStiffness * 0.4273504273504274)
//                    } else if (animType == animTypesEnum[AnimType.CLOSE_TO_HOME.ordinal]) {
////                        XposedHelpers.setObjectField(
////                            this.instance,
////                            "mCenterXStiffness",
////                            mCenterXStiffness * 1.2f
////                        )
////                        XposedHelpers.setObjectField(
////                            this.instance,
////                            "mCenterYStiffness",
////                            mCenterYStiffness * 2f
////                        )
//                        this.field {
//                            name("mWidthStiffness")
//                        }.get(this.instance).set(mWidthStiffness * 2f)
//                        this.field {
//                            name("mLeftVelocity")
//                        }.get(this.instance).set(0f)
//                        this.field {
//                            name("mTopVelocity")
//                        }.get(this.instance).set(
//                            mTopVelocity * 1.3f
//                        )
//                        this.field {
//                            name("mWidthVelocity")
//                        }.get(this.instance).set(mWidthVelocity / 1.4f)
//                    }
//                }
//            }
//        }

    }
}