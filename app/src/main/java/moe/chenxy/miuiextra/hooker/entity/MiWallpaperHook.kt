package moe.chenxy.miuiextra.hooker.entity

import android.R.attr.classLoader
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.WindowManager
import android.view.animation.PathInterpolator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.field
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ValueAnimatorClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import de.robv.android.xposed.*
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.data.AnimFragmentShaderChen
import java.lang.reflect.Method


object MiWallpaperHook : YukiBaseHooker() {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    private val mUseChenScreenOnAnim = mainPrefs.getBoolean("use_chen_screen_on_anim", false)
    private var currentRevealValue = -1f

    override fun onHook() {
        var mIsShowingRevealBlack = false
        var mClassName = ""

        "com.miui.miwallpaper.container.openGL.AnimImageWallpaperRenderer".toClass().apply {
//            injectMember {
//                method {
//                    name = "startRevealAnim"
//                    param(BooleanType)
//                }
//                replaceUnit {
//                    mainPrefs.reload()
//                    val awake = this.args[0] as Boolean
//                    val mRevealAnimator = XposedHelpers.findField(
//                        this.instance.javaClass.superclass,
//                        "mRevealAnimator"
//                    ).get(this.instance) as ValueAnimator
//                    val i: Int
//                    val f: Float
//
//                    if (!mIsShowingRevealBlack == awake && mClassName == this.instance.javaClass.name) {
//                        Log.i("Art_Chen", "awake animation already started! ignored this start")
//                        XposedHelpers.callMethod(this.instance, "refresh")
//                        return@replaceUnit
//                    }
//                    mClassName = this.instance.javaClass.name
//
//                    if (mRevealAnimator.isRunning) {
//                        mRevealAnimator.cancel()
//                    }
//
//                    if (awake) {
//                        i = mainPrefs.getInt("screen_on_color_fade_anim_val", 800)
//                        f = 0.0f
//                    } else {
//                        i = if (mUseChenScreenOnAnim) {
//                            80
//                        } else {
//                            400
//                        }
//                        f = 1.0f
//                    }
//                    mIsShowingRevealBlack = !awake
//                    val mRevealValue = XposedHelpers.getFloatField(this.instance, "mRevealValue")
//                    if (mRevealValue == f) {
//                        Log.i(
//                            "Art_Chen",
//                            "startRevealAnim: mCurrentValue == targetValue, no anim, mCurrentValue $mRevealValue, mClass: ${this.instance.javaClass}"
//                        )
////                        XposedHelpers.setFloatField(
////                            param.thisObject,
////                            "mRevealValue",
////                            if (f == 1f) 0f else 1f
////                        )
////                        mRevealValue = if (f == 1f) 0f else 1f
//                        XposedHelpers.callMethod(this.instance, "refresh")
//                        return@replaceUnit
//                    }
//                    Log.i(
//                        "Art_Chen",
//                        "startRevealAnim: awake = $awake, current duration $i, from $mRevealValue to $f"
//                    )
//                    mRevealAnimator.duration = i.toLong()
//                    mRevealAnimator.setFloatValues(mRevealValue, f)
//                    if (mUseChenScreenOnAnim) {
//                        mRevealAnimator.interpolator =
//                            if (awake)
//                                PathInterpolator(0.54f, 0f, 0f, 1f)
//                            else
//                                PathInterpolator(0f, 0f, 0.11f, 1f)
//                    }
//                    mRevealAnimator.start()
//                    currentRevealValue = f
//                    Log.i("Art_Chen", "startRevealAnim: run!")
//                    Exception().printStackTrace()
//                }
//            }

            method {
                name = "updateMaskLayerStatus"
                param(BooleanType, BooleanType)
            }.hook {
                before {
                    mainPrefs.reload()
                    if (mainPrefs.getBoolean("disable_wallpaper_auto_darken", false)) {
                        this.args[1] = false
                    }
                }
            }
        }

        var mLayoutParams: WindowManager.LayoutParams? = null
        var updateSurface: Method? = null
        var mMiuiKeyguardPictorialWallpaper: Any? = null
        fun updateAlpha(float: Float) {
            if (mLayoutParams!!.alpha == float) {
                return
            }
            mLayoutParams!!.alpha = float
            XposedHelpers.setObjectField(
                mMiuiKeyguardPictorialWallpaper,
                "mLayoutParams",
                mLayoutParams
            )
            try {
                updateSurface!!.invoke(mMiuiKeyguardPictorialWallpaper, true, false, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

        if (mainPrefs.getBoolean("miui_unlock_wallpaper_anim_fade", false)) {
            "com.miui.miwallpaper.wallpaperservice.impl.keyguard.KeyguardImageEngineImpl".toClass().method {
                name = "hideKeyguardWallpaper"
            }.hook {
                before {
                    val mWorkerHandler = XposedHelpers.getObjectField(
                        this.instance,
                        "mWorkerHandler"
                    ) as Handler
                    if (mLayoutParams?.alpha == 1f && !mIsShowingRevealBlack) {
                        Log.i(
                            "Art_Chen",
                            "pre start alpha animation, alpha ${mLayoutParams!!.alpha}, mIsShowingRevealBlack $mIsShowingRevealBlack"
                        )
                        mainPrefs.reload()
                        mWorkerHandler.post {
                            val valueAnimation = ValueAnimator()
                            valueAnimation.duration = mainPrefs.getInt(
                                "miui_unlock_wallpaper_anim_fade_anim_val",
                                450
                            ).toLong()
                            valueAnimation.setFloatValues(1.0f, 0.0f)
                            valueAnimation.setInterpolator {
                                val f2: Float = it - 1.0f
                                f2 * f2 * f2 + 1.0f
                            }
                            valueAnimation.addUpdateListener {
                                updateAlpha(it.animatedValue as Float)
                            }
                            valueAnimation.start()
                        }
                        this.result = null
                    }
                }
            }

            "com.miui.miwallpaper.wallpaperservice.MiuiKeyguardPictorialWallpaper\$KeyguardEngine".toClass().constructor {
                param("com.miui.miwallpaper.wallpaperservice.MiuiKeyguardPictorialWallpaper")
            }.hook {
                after {
                    mLayoutParams = XposedHelpers.getObjectField(
                        this.instance,
                        "mLayoutParams"
                    ) as WindowManager.LayoutParams
                    updateSurface = XposedHelpers.getObjectField(
                        this.instance,
                        "updateSurface"
                    ) as Method
                    mMiuiKeyguardPictorialWallpaper = this.instance
                }
            }

            "com.miui.miwallpaper.container.openGL.DesktopAnimImageWallpaperRenderer".toClass().method {
                name = "startScaleAnim"
            }.hook {
                intercept()
//                before {
////                    if (mLayoutParams?.alpha == 1f && !mIsShowingRevealBlack) {
//                        this.result = null
////                    }
//                }
            }
        }

//        "com.miui.miwallpaper.wallpaperservice.impl.desktop.DesktopImageEngineImpl".hook {
//            injectMember {
//                method {
//                    name = "lambda\$hideKeyguardWallpaper$11\$DesktopImageEngineImpl"
//                }
//                beforeHook {
//                    this.result = null
//                }
//            }.ignoredAllFailure()
//
//            injectMember {
//                method {
//                    name = "lambda\$hideKeyguardWallpaper$3\$DesktopImageEngineImpl"
//                }
//                beforeHook {
//                    this.result = null
//                }
//            }.ignoredAllFailure()
//
//            injectMember {
//                method {
//                    name = "onScreenTurningOff"
//                }
//                beforeHook {
//                    val mDesktopWallpaperRenderer = XposedHelpers.getObjectField(
//                        this.instance,
//                        "mDesktopWallpaperRenderer"
//                    )
//                    XposedHelpers.callMethod(
//                        mDesktopWallpaperRenderer,
//                        "startRevealAnim",
//                        false
//                    )
//                }
//            }
//
//            injectMember {
//                method {
//                    name = "lambda\$onScreenTurningOn$1\$DesktopImageEngineImpl"
//                }
//                beforeHook {
//                    if (mIsShowingRevealBlack && mUseChenScreenOnAnim) {
//                        val mDesktopWallpaperRenderer =
//                            XposedHelpers.getObjectField(this.instance, "mDesktopWallpaperRenderer")
//                        XposedHelpers.callMethod(mDesktopWallpaperRenderer, "startRevealAnim", true)
//                        this.result = null
//                    }
//                }
//            }
//
//        }

//        if (mainPrefs.getBoolean("use_chen_screen_on_anim", false)) {
//            loadHooker(ChenAnimation)
//        }

//        "com.miui.miwallpaper.manager.WallpaperServiceController".hook {
//            injectMember {
//                method {
//                    name = "needDesktopDoRevealAnim"
//                }
//                replaceTo(true)
//            }
//        }
    }

//    object ChenAnimation : YukiBaseHooker() {
//        var uBlurRadius = -1
//        var uBlurOffset = -1
//        var uSumWeight = -1
//        var uAlpha = -1
//        var mScaleValue = 0f
//        var mFragmentShaderId: Int = -1
//        override fun onHook() {
//            "com.android.systemui.glwallpaper.ImageWallpaperRenderer".hook {
//                injectMember {
//                    method {
//                        name = "setGLViewport"
//                    }
//                    beforeHook {
//                        val mRevealValue = XposedHelpers.getFloatField(
//                            this.instance,
//                            "mRevealValue"
//                        )
//                        if (mScaleValue != 0f && mRevealValue != 0f) {
//                            Log.v(
//                                "Art_Chen",
//                                "need animation!! override setGLViewport! this class = ${this.instance.javaClass}"
//                            )
//                            val mSurfaceSize =
//                                XposedHelpers.getObjectField(
//                                    this.instance,
//                                    "mSurfaceSize"
//                                ) as Rect
//                            val f2 = ((1.0f - mRevealValue) * 1.0f) + (mRevealValue * 1.2f)
//                            val f3 = (1.0f - f2) / 2.0f
//                            val width = mSurfaceSize.width()
//                            val height = mSurfaceSize.height()
//                            Log.v(
//                                "Art_Chen",
//                                "mRevealValue $mRevealValue, mScaleValue $mScaleValue"
//                            )
//                            GLES20.glViewport(
//                                ((mSurfaceSize.left + (width * f3)).toInt()),
//                                ((mSurfaceSize.top + (f3 * height)).toInt()),
//                                ((width * f2).toInt()),
//                                ((height * f2).toInt())
//                            )
//                            this.result = null
//                        }
//                    }
//                }
//
//                injectMember {
//                    method {
//                        name = "onSurfaceCreated"
//                    }
//                    beforeHook {
//                        mFragmentShaderId =
//                            XposedHelpers.callMethod(this.instance, "getFragmentShader") as Int
//                        Log.d("Art_Chen", "got fragment shader res id!!")
//                    }
//                }
//            }
//
//            "com.miui.miwallpaper.container.openGL.AnimImageWallpaperRenderer".hook {
//                injectMember {
//                    method {
//                        name = "lambda\$new$0\$AnimImageWallpaperRenderer"
//                        param(ValueAnimatorClass)
//                    }
//                    beforeHook {
//                        val valueAnimator = this.args[0] as ValueAnimator
//                        mScaleValue = (valueAnimator.animatedValue as Float)
//                    }
//                }
//            }
//
//            "com.android.systemui.glwallpaper.ImageGLProgram".hook {
//                injectMember {
//                    method {
//                        name = "getShaderResource"
//                        param(IntType)
//                    }
//                    beforeHook {
//                        if (this.args[0] as Int == mFragmentShaderId) {
//                            Log.i(
//                                "Art_Chen",
//                                "mFragmentShaderId detected, return chen Fragment Shader"
//                            )
//                            this.result = AnimFragmentShaderChen.glsl
//                        }
//                    }
//                }
//            }
//
//
//            "com.miui.miwallpaper.container.openGL.DesktopAnimImageWallpaperRenderer".toClass().apply {
//
//                method {
//                    name = "checkIsNeedCancelAnim"
//                }.hook {
//                    before {
//                        val mRevealValue =
//                            XposedHelpers.getFloatField(this.instance, "mRevealValue")
//                        if (mUseChenScreenOnAnim && mRevealValue != 0f) {
//                            this.result = true
//                        }
//                    }
//                }
//
//                method {
//                    name = "setGLViewport"
//                }.hook {
//                    before {
//                        val mRevealValue =
//                            field {
//                                name = "mRevealValue"
//                                superClass()
//                            }.get(this.instance).any()
//                        if (mUseChenScreenOnAnim && mRevealValue != 0f) {
//                            // v1.9.0+ need set these to false to call the super method
//                            field {
//                                name("mWallpaperScaling")
//                            }.get(this.instance).set(false)
//
//                            field {
//                                name("mIsResetScale")
//                            }.get(this.instance).set(false)
//                        }
//                    }
//                }
//            }
//        }
//    }
}