package moe.chenxy.miuiextra.hooker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.opengl.GLES20
import android.os.Handler
import android.util.Log
import android.view.WindowManager
import android.view.animation.PathInterpolator
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.data.AnimFragmentShaderChen
import java.lang.reflect.Method


class MiWallpaperHook {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    val mUseChenScreenOnAnim = mainPrefs.getBoolean("use_chen_screen_on_anim", false)
    var currentRevealValue = -1f
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val classLoader = lpparam!!.classLoader
        var mIsShowingRevealBlack = false
        var mClassName = ""
        XposedHelpers.findAndHookMethod("com.miui.miwallpaper.container.openGL.AnimImageWallpaperRenderer",
            classLoader,
            "startRevealAnim",
            Boolean::class.javaPrimitiveType,
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam?) {
                    mainPrefs.reload()
                    val awake = param!!.args[0] as Boolean
                    val mRevealAnimator = XposedHelpers.findField(
                        param.thisObject.javaClass.superclass,
                        "mRevealAnimator"
                    ).get(param.thisObject) as ValueAnimator
                    val i: Int
                    val f: Float

                    if (!mIsShowingRevealBlack == awake && mClassName == param.thisObject.javaClass.name) {
                        Log.i("Art_Chen", "awake animation already started! ignored this start")
                        XposedHelpers.callMethod(param.thisObject, "refresh")
                        return
                    }
                    mClassName = param.thisObject.javaClass.name

                    if (mRevealAnimator.isRunning) {
                        mRevealAnimator.cancel()
                    }

                    if (awake) {
                        i = mainPrefs.getInt("screen_on_color_fade_anim_val", 800)
                        f = 0.0f
                    } else {
                        i = if (mUseChenScreenOnAnim) {
                            80
                        } else {
                            400
                        }
                        f = 1.0f
                    }
                    mIsShowingRevealBlack = !awake
                    val mRevealValue = XposedHelpers.getFloatField(param.thisObject, "mRevealValue")
                    if (mRevealValue == f) {
                        Log.i(
                            "Art_Chen",
                            "startRevealAnim: mCurrentValue == targetValue, no anim, mCurrentValue $mRevealValue, mClass: ${param.thisObject.javaClass}"
                        )
//                        XposedHelpers.setFloatField(
//                            param.thisObject,
//                            "mRevealValue",
//                            if (f == 1f) 0f else 1f
//                        )
//                        mRevealValue = if (f == 1f) 0f else 1f
                        XposedHelpers.callMethod(param.thisObject, "refresh")
                        return
                    }
                    Log.i(
                        "Art_Chen",
                        "startRevealAnim: awake = $awake, current duration $i, from $mRevealValue to $f"
                    )
                    mRevealAnimator.duration = i.toLong()
                    mRevealAnimator.setFloatValues(mRevealValue, f)
                    if (mUseChenScreenOnAnim) {
                        mRevealAnimator.interpolator =
                            if (awake)
                                PathInterpolator(0.54f, 0f, 0f, 1f)
                            else
                                PathInterpolator(0f, 0f, 0.11f, 1f)
                    }
                    mRevealAnimator.start()
                    currentRevealValue = f
                    Log.i("Art_Chen", "startRevealAnim: run!")
                }
            })

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

        XposedHelpers.findAndHookMethod(
            "com.miui.miwallpaper.container.openGL.DesktopAnimImageWallpaperRenderer",
            classLoader,
            "startScaleAnim",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (mainPrefs.getBoolean("miui_unlock_wallpaper_anim_fade", false)) {
                        if (mLayoutParams?.alpha == 1f && !mIsShowingRevealBlack) {
                            param.result = 0
                        }
                    }
                }
            })

        if (mainPrefs.getBoolean("miui_unlock_wallpaper_anim_fade", false)) {
            XposedHelpers.findAndHookMethod(
                "com.miui.miwallpaper.wallpaperservice.impl.keyguard.KeyguardImageEngineImpl",
                classLoader,
                "hideKeyguardWallpaper",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val mWorkerHandler = XposedHelpers.getObjectField(
                            param.thisObject,
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
                                val animatorListenerAdapter = object : AnimatorListenerAdapter() {
                                    override fun onAnimationStart(animator: Animator) {
                                        Log.i("Art_Chen", "Chen Keyguard Wallpaper fade out start!")
                                    }

                                    override fun onAnimationEnd(animator: Animator) {
                                        Log.i("Art_Chen", "Chen Keyguard Wallpaper fade out end!")
                                    }
                                }
                                valueAnimation.start()
                            }
                            param.result = 0
                        }
                    }
                })

            XposedHelpers.findAndHookConstructor("com.miui.miwallpaper.wallpaperservice.MiuiKeyguardPictorialWallpaper\$KeyguardEngine",
                classLoader,
                "com.miui.miwallpaper.wallpaperservice.MiuiKeyguardPictorialWallpaper",
                object : XC_MethodHook() {

                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        mLayoutParams = XposedHelpers.getObjectField(
                            param.thisObject,
                            "mLayoutParams"
                        ) as WindowManager.LayoutParams
                        updateSurface = XposedHelpers.getObjectField(
                            param.thisObject,
                            "updateSurface"
                        ) as Method
                        mMiuiKeyguardPictorialWallpaper = param.thisObject
                    }
                })
        }

        XposedHelpers.findAndHookMethod(
            "com.miui.miwallpaper.wallpaperservice.impl.desktop.DesktopImageEngineImpl",
            classLoader,
            "lambda\$onScreenTurningOn$1\$DesktopImageEngineImpl",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam) {
                    val mKeyguardWallpaperRenderer = XposedHelpers.getObjectField(param.thisObject, "mDesktopWallpaperRenderer")
                    XposedHelpers.callMethod(mKeyguardWallpaperRenderer, "startRevealAnim", true)
                }
            })

        XposedHelpers.findAndHookMethod(
            "com.miui.miwallpaper.wallpaperservice.impl.desktop.DesktopImageEngineImpl",
            classLoader,
            "lambda\$hideKeyguardWallpaper$3\$DesktopImageEngineImpl",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = 0
                }
            })

        XposedHelpers.findAndHookMethod("com.miui.miwallpaper.container.openGL.AnimImageWallpaperRenderer",
            classLoader,
            "updateMaskLayerStatus",
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    mainPrefs.reload()
                    if (mainPrefs.getBoolean("disable_wallpaper_auto_darken", false)) {
                        param.args[1] = false
                    }
                }
            })

        XposedHelpers.findAndHookMethod(
            "com.miui.miwallpaper.wallpaperservice.impl.desktop.DesktopImageEngineImpl",
            classLoader,
            "onScreenTurningOff",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val mDesktopWallpaperRenderer = XposedHelpers.getObjectField(
                        param.thisObject,
                        "mDesktopWallpaperRenderer"
                    )
                    XposedHelpers.callMethod(
                        mDesktopWallpaperRenderer,
                        "startRevealAnim",
                        false
                    )
                }
            })

        hookForChenAnim(lpparam)
    }

    private fun hookForChenAnim(lpparam: XC_LoadPackage.LoadPackageParam?) {
        var uBlurRadius = -1
        var uBlurOffset = -1
        var uSumWeight = -1
        var uAlpha = -1
        var mScaleValue = 0f
        val mUseChenScreenOnAnim = mainPrefs.getBoolean("use_chen_screen_on_anim", false)
        val classLoader = lpparam?.classLoader
        if (mUseChenScreenOnAnim) {
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.glwallpaper.ImageWallpaperRenderer",
                classLoader,
                "setGLViewport",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val mRevealValue = XposedHelpers.getFloatField(
                            param.thisObject,
                            "mRevealValue")
                        if (mScaleValue != 0f && mRevealValue != 0f) {
                            Log.v(
                                "Art_Chen",
                                "need animation!! override setGLViewport! this class = ${param.thisObject.javaClass}"
                            )
                            val mSurfaceSize =
                                XposedHelpers.getObjectField(
                                    param.thisObject,
                                    "mSurfaceSize"
                                ) as Rect
                            val f2 = ((1.0f - mRevealValue) * 1.0f) + (mRevealValue * 1.2f);
                            val f3 = (1.0f - f2) / 2.0f;
                            val width = mSurfaceSize.width();
                            val height = mSurfaceSize.height();
                            Log.v("Art_Chen", "mRevealValue $mRevealValue, mScaleValue $mScaleValue")
                            GLES20.glViewport(
                                ((mSurfaceSize.left + (width * f3)).toInt()),
                                ((mSurfaceSize.top + (f3 * height)).toInt()),
                                ((width * f2).toInt()),
                                ((height * f2).toInt())
                            );
                            param.result = 0
                        }
                    }

                })

            XposedHelpers.findAndHookMethod("com.miui.miwallpaper.container.openGL.AnimImageWallpaperRenderer",
                classLoader,
                "lambda\$new$0\$AnimImageWallpaperRenderer",
                ValueAnimator::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val valueAnimator = param.args[0] as ValueAnimator
                        mScaleValue = (valueAnimator.animatedValue as Float)
                    }
                })

            var mFragmentShaderId: Int = -1
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.glwallpaper.ImageWallpaperRenderer",
                classLoader,
                "onSurfaceCreated",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        mFragmentShaderId = XposedHelpers.callMethod(param.thisObject, "getFragmentShader") as Int
                        Log.d("Art_Chen", "got fragment shader res id!!")
                    }
                })

            XposedHelpers.findAndHookMethod("com.android.systemui.glwallpaper.ImageGLProgram",
                classLoader,
                "getShaderResource",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] as Int == mFragmentShaderId) {
                            Log.i("Art_Chen", "mFragmentShaderId detected, return chen Fragment Shader")
                            param.result = AnimFragmentShaderChen.glsl
                        }
                    }
                })

            XposedHelpers.findAndHookMethod(
                "com.miui.miwallpaper.container.openGL.DesktopAnimImageWallpaperRenderer",
                classLoader,
                "checkIsNeedCancelAnim",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val mRevealValue = XposedHelpers.getFloatField(param.thisObject, "mRevealValue")
                        if (mUseChenScreenOnAnim && mRevealValue != 0f) {
                            param.result = true
                        }
                    }
                })
            XposedHelpers.findAndHookMethod(
                "com.miui.miwallpaper.wallpaperservice.impl.desktop.DesktopImageEngineImpl",
                classLoader,
                "onScreenTurningOn",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val mDesktopWallpaperRenderer = XposedHelpers.getObjectField(
                            param.thisObject,
                            "mDesktopWallpaperRenderer"
                        )
                        XposedHelpers.callMethod(
                            mDesktopWallpaperRenderer,
                            "resetRevealAnim",
                            true
                        )
                    }

                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        super.afterHookedMethod(param)
                    }
                })
        }


//        XposedHelpers.findAndHookMethod(
//            "com.miui.miwallpaper.container.openGL.AnimImageWallpaperRenderer",
//            classLoader,
//            "onDrawFrame",
//            object : XC_MethodHook() {
//                @Throws(Throwable::class)
//                override fun beforeHookedMethod(param: MethodHookParam) {
//                    val mRevealValue = XposedHelpers.getObjectField(param.thisObject, "mRevealValue") as Float

//                    GLES20.glUniform1i(uBlurRadius, (10 * mRevealValue).toInt())
//                    GLES20.glUniform2f(uBlurOffset, 0.001f, 0.001f)
//                    GLES20.glUniform1f(uSumWeight, 1f)

//                    Log.i("Art_Chen", "mRevealValue $mRevealValue , mScaleValue $mScaleValue")
//                    val f2 = ((1.0f - mScaleValue) * 1.0f) + (mScaleValue * 1.2f);
//                    val f3 = (1.0f - f2) / 2.0f;
//                    val width = mSurfaceSize.width();
//                    val height = mSurfaceSize.height();
//                    GLES20.glUniform1f(uAlpha, currentAlpha)
////                    GLES20.glUniform2f(uBlurOffset, 0.001f, 0.001f)
////                    GLES20.glUniform1f(uSumWeight, 1f)
//                    GLES20.glViewport(((mSurfaceSize.left + (width * f3)).toInt()), ((mSurfaceSize.top + (f3 * height)).toInt()), ((width * f2).toInt()), ((height * f2).toInt()));
//                    param.result = 0
//                }
//            })



//        XposedHelpers.findAndHookMethod(
//            "com.miui.miwallpaper.container.openGL.AnimImageGLWallpaper",
//            classLoader,
//            "setupUniforms",
//            object : XC_MethodHook() {
//                @Throws(Throwable::class)
//                override fun beforeHookedMethod(param: MethodHookParam) {
//                    val mProgram = XposedHelpers.getObjectField(param.thisObject, "mProgram")
//                    uBlurRadius = XposedHelpers.callMethod(mProgram, "getUniformHandle", "uBlurRadius") as Int
//                    uBlurOffset = XposedHelpers.callMethod(mProgram, "getUniformHandle", "uBlurOffset") as Int
//                    uSumWeight = XposedHelpers.callMethod(mProgram, "getUniformHandle", "uSumWeight") as Int
//                    uAlpha = XposedHelpers.callMethod(mProgram, "getUniformHandle", "uAlpha") as Int
//                }
//            })
    }

    fun callSuperMethod(obj: Any, methodName: String?, vararg args: Any?): Any? {
        return try {
            XposedHelpers.findMethodBestMatch(obj.javaClass.superclass, methodName, *args).invoke(obj, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun callSuperSuperMethod(obj: Any, methodName: String?, vararg args: Any?): Any? {
        return try {
            XposedHelpers.findMethodBestMatch(obj.javaClass.superclass.superclass, methodName, *args).invoke(obj, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}