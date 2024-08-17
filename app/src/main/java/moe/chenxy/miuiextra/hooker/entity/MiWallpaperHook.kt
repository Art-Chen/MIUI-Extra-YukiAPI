package moe.chenxy.miuiextra.hooker.entity

import android.animation.ValueAnimator
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.core.animation.doOnEnd
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.hooker.entity.MiWallpaperHook.ChenAnimationNew.hook
import moe.chenxy.miuiextra.hooker.entity.MiWallpaperHook.ChenAnimationNew.onScreenOff
import moe.chenxy.miuiextra.hooker.entity.MiWallpaperHook.ChenAnimationNew.onUserPresent
import moe.chenxy.miuiextra.hooker.entity.MiWallpaperHook.ChenAnimationNew.startScaleAnim
import java.lang.reflect.Method


object MiWallpaperHook : YukiBaseHooker() {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    private val mUseChenScreenOnAnim = mainPrefs.getBoolean("use_chen_screen_on_anim", false)
    private var currentRevealValue = -1f
    private var scaleAnimator : ValueAnimator? = null
    private var mWSC: Any? = null

    @Volatile
    private var mScaleValue = 0f

    override fun onHook() {
        var mIsShowingRevealBlack = false
        var mClassName = ""
        var lastToAod = false

        fun onAodWallpaperAnimTriggered(toAod: Boolean) {
            if (mWSC == null) return

//            mToAod = toAod
//            triggeredByAod = true
//            XposedHelpers.callMethod(mWSC, "showWallpaperScreenOnAnim", !mToAod)
            if (toAod == lastToAod) return
            startScaleAnim(!toAod)
            Log.i("Art_Chen", "onAodWallpaperAnimTriggered toAod $toAod")
            lastToAod = toAod
        }

        if (mUseChenScreenOnAnim) {
            loadHooker(ChenAnimationNew)
            "com.miui.miwallpaper.manager.WallpaperServiceController".toClass().apply {
                constructor {
                    paramCount = 0
                }.hook {
                    after {
                        val mContext =
                            XposedHelpers.getObjectField(this.instance, "mContext") as Context
                        mWSC = this.instance
                        mContext.registerReceiver(
                            object : BroadcastReceiver() {
                                override fun onReceive(p0: Context?, p1: Intent?) {
                                    p1?.let {
                                        val toAod = it.getBooleanExtra("toAod", false)
                                        onAodWallpaperAnimTriggered(toAod)
                                    }
                                }
                            },
                            IntentFilter("chen.action.show_wallpaper_anim"),
                            Context.RECEIVER_EXPORTED
                        )
                    }
                }

                method {
                    name = "hideKeyguardWallpaper"
                    paramCount = 2
                }.hook {
                    after {
                        onUserPresent()
                    }
                }

                method {
                    name = "showKeyguardWallpaper"
                    paramCount = 2
                }.hook {
                    after {
                        onScreenOff()
                    }
                }
            }

//            "com.miui.miwallpaper.utils.SystemSettingUtils".toClass().method {
//                name = "isLinkAgeAOD"
//            }.hook {
//                replaceAny {
//                    return@replaceAny !triggeredByAod
//                }
//            }
        }

        "com.miui.miwallpaper.opengl.AnimatorProgram".toClass().apply {
            method {
                name = "startRevealAnim"
                param(BooleanType)
            }.hook {
                replaceUnit {
                    mainPrefs.reload()
                    val awake = this.args[0] as Boolean
                    val mRevealAnimator = XposedHelpers.findField(
                        this.instance.javaClass,
                        "mRevealAnimator"
                    ).get(this.instance) as ValueAnimator
                    val i: Int
                    val f: Float

                    if (mRevealAnimator.isRunning) {
                        mRevealAnimator.cancel()
                    }

                    if (awake) {
                        i = mainPrefs.getInt("screen_on_color_fade_anim_val", 800)
                        f = 0.0f
                    } else {
                        i = mainPrefs.getInt("screen_off_color_fade_anim_val", 450)
                        f = 1.0f
                    }

                    mIsShowingRevealBlack = !awake
                    val mProgram = XposedHelpers.getObjectField(this.instance, "mProgram")
                    val mRevealValue = XposedHelpers.getFloatField(mProgram, "mRevealValue")

                    Log.i(
                        "Art_Chen",
                        "startRevealAnim: awake = $awake, current duration $i, from $mRevealValue to $f"
                    )
                    mRevealAnimator.duration = i.toLong()
                    mRevealAnimator.setFloatValues(mRevealValue, f)
//                    if (mUseChenScreenOnAnim) {
                    mRevealAnimator.interpolator =
                        if (awake)
                            PathInterpolator(0.54f, 0f, 0f, 1f)
                        else
                            PathInterpolator(0f, 0f, 0f, 1f)
//                    }
                    mRevealAnimator.start()
                    currentRevealValue = f
                    if (mUseChenScreenOnAnim) {
                        startScaleAnim(awake)
                    }

                    Log.i("Art_Chen", "startRevealAnim: run!")
                }
            }

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

    object ChenAnimationNew : YukiBaseHooker() {
        private var isSameImage = true
        private var handler: Handler? = null


        fun onUserPresent() {
            handler?.post {
                if (isSameImage) {
                    scaleAnimator!!.cancel()
                    scaleAnimator!!.setFloatValues(mScaleValue, 0f)
                    scaleAnimator!!.duration = 1000
                    scaleAnimator!!.start()
                } else {
                    scaleAnimator!!.end()
                }
            }
        }

        fun onScreenOff() {
            handler?.post {
                scaleAnimator!!.setFloatValues(mScaleValue, 0.5f)
                scaleAnimator!!.duration = 300
                scaleAnimator!!.start()
            }
        }

        fun startScaleAnim(zoomIn: Boolean) {
            if (scaleAnimator == null) {
                Log.e("Art_Chen", "Scale Anim is not init!")
                return
            }

            if (mScaleValue == if (zoomIn) 1f else 0f) return

            if (scaleAnimator!!.isRunning) scaleAnimator!!.cancel()

            isSameImage = XposedHelpers.callMethod(mWSC, "isSameImageWallpaper") as Boolean

            Log.i("Art_Chen", "zoomIn $zoomIn, isSameImage $isSameImage")

            scaleAnimator!!.setFloatValues(mScaleValue, if (zoomIn) 1f else 0.6f)
            scaleAnimator!!.duration = if (zoomIn) 1000 else 2000
            scaleAnimator!!.start()
        }

        override fun onHook() {
            var desktopCls: Class<*>? = "com.miui.miwallpaper.container.openGL.DesktopAnimImageWallpaperRenderer".toClass()
            "com.miui.miwallpaper.opengl.AnimImageWallpaperRenderer".toClass().apply {
                constructor {
                    param(ContextClass)
                }.hook {
                    after {
//                        cls =
//                            if (XposedHelpers.callMethod(mWSC, "isSameImageWallpaper") as Boolean)
//                                "com.miui.miwallpaper.container.openGL.DesktopAnimImageWallpaperRenderer".toClass()
//                            else
//                                "com.miui.miwallpaper.container.openGL.KeyguardAnimImageWallpaperRenderer".toClass()

                        if (scaleAnimator == null) {
                            handler = Handler(Looper.getMainLooper())
                            scaleAnimator = ValueAnimator()

                            scaleAnimator!!.addUpdateListener {
                                mScaleValue = it.animatedValue as Float
                            }

//                            scaleAnimator!!.duration = 1200
                            scaleAnimator!!.interpolator = PathInterpolator(0.23f, 0.6f, 0.38f, 1f)
                        }
                        scaleAnimator!!.addUpdateListener {
                            handler!!.post {
                                XposedHelpers.callMethod(this.instance, "refresh")
                            }
                        }
                        Log.i("Art_Chen", "scale animation init! instance ${this.instance.javaClass}")

                    }
                }
            }

            "com.miui.miwallpaper.container.openGL.DesktopAnimImageWallpaperRenderer".toClass().apply {
                method {
                    name = "startScaleAnim"
                }.hook {
                    before {
                        handler!!.post {
                            scaleAnimator!!.end()
                        }
                    }
                }
            }

            "com.miui.miwallpaper.opengl.ImageWallpaperRenderer".toClass().apply {
                method {
                    name = "setGLViewport"
                }.hook {
                    before {
                        if (!isSameImage
                            && desktopCls!!.isInstance(this.instance)) {
                            return@before
                        }

                        Log.v(
                            "Art_Chen",
                            "need animation!! override setGLViewport! this class = ${this.instance.javaClass}"
                        )
                        val mSurfaceSize =
                            XposedHelpers.getObjectField(
                                this.instance,
                                "mSurfaceSize"
                            ) as Rect
                        val f2 = ((1.0f - mScaleValue) * 1.0f) + (mScaleValue * 1.07f)
                        val f3 = (1.0f - f2) / 2.0f
                        val width = mSurfaceSize.width()
                        val height = mSurfaceSize.height()
                        Log.v(
                            "Art_Chen",
                            "setGLViewport mScaleValue $mScaleValue"
                        )
                        GLES20.glViewport(
                            ((mSurfaceSize.left + (width * f3)).toInt()),
                            ((mSurfaceSize.top + (f3 * height)).toInt()),
                            ((width * f2).toInt()),
                            ((height * f2).toInt())
                        )
                        this.result = null
                    }
                }
            }
        }
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