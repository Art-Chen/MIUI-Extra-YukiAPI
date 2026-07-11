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
import moe.chenxy.miuiextra.utils.ChenUtils
import java.lang.reflect.Method
import kotlin.random.Random


object MiWallpaperHook : YukiBaseHooker() {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    private val mUseChenScreenOnAnim = mainPrefs.getBoolean("use_chen_screen_on_anim", false)
    private var currentRevealValue = -1f
    private var scaleAnimator : ValueAnimator? = null
    private var mWSC: Any? = null

    @Volatile
    private var mScaleValue = 0f

    private val isAboveV = ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.V)

    override fun onHook() {
        var mIsShowingRevealBlack = false

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
                                        ChenAnimationNew.onAodWallpaperAnimTriggered(toAod)
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
                        if (awake) {
                            if (isAboveV) {
                                PathInterpolator(0.4f, 0f, 0.2f, 1f)
                            } else {
                                PathInterpolator(0.54f, 0f, 0f, 1f)
                            }
                        } else {
                            if (isAboveV) {
                                PathInterpolator(0.16f, 1f, 0.3f, 1f)
                            } else {
                                PathInterpolator(0f, 0f, 0f, 1f)
                            }
                        }
//                    }
                    mRevealAnimator.start()
                    currentRevealValue = f
                    if (mUseChenScreenOnAnim) {
                        startScaleAnim(awake)
                    }

                    Log.i("Art_Chen", "startRevealAnim: run! isAboveV $isAboveV")
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
    }

    object ChenAnimationNew : YukiBaseHooker() {
        private var isSameImage = true
        private var handler: Handler? = null
        private var lastToAod = false
        private var scalePerMin: Runnable = Runnable {
            val random = Random.nextFloat() / 10
            val target = if (mScaleValue > 0.7f) mScaleValue - random else mScaleValue + random
            Log.d("Art_Chen", "scalePerMin trigger. $target")
            startScaleAnim(target, 2000)
            handler?.postDelayed(scalePerMin, 1 * 60000)
        }

        fun onAodWallpaperAnimTriggered(toAod: Boolean) {
            if (mWSC == null) return

            if (toAod == lastToAod) return

            if (toAod) {
                handler?.postDelayed(scalePerMin, 1 * 6000)
            } else {
                handler?.removeCallbacks(scalePerMin)
            }

            startScaleAnim(!toAod)
            Log.i("Art_Chen", "onAodWallpaperAnimTriggered toAod $toAod")
            lastToAod = toAod
        }


        fun onUserPresent() {
            handler?.removeCallbacks(scalePerMin)
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
            handler?.removeCallbacks(scalePerMin)
            handler?.post {
                scaleAnimator!!.setFloatValues(mScaleValue, 0.5f)
                scaleAnimator!!.duration = 1000
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

        fun startScaleAnim(to: Float, duration: Long) {
            if (scaleAnimator == null) {
                Log.e("Art_Chen", "Scale Anim is not init!")
                return
            }

            if (scaleAnimator!!.isRunning) scaleAnimator!!.cancel()


            scaleAnimator!!.setFloatValues(mScaleValue, to)
            scaleAnimator!!.duration = duration
            scaleAnimator!!.start()
        }

        override fun onHook() {
            var desktopCls: Class<*>? = "com.miui.miwallpaper.container.openGL.DesktopAnimImageWallpaperRenderer".toClass()
            "com.miui.miwallpaper.opengl.AnimImageWallpaperRenderer".toClass().apply {
                constructor {
                    param(ContextClass)
                }.hook {
                    after {
                        if (scaleAnimator == null) {
                            handler = Handler(Looper.getMainLooper())
                            scaleAnimator = ValueAnimator()

                            scaleAnimator!!.addUpdateListener {
                                mScaleValue = it.animatedValue as Float
                            }

                            scaleAnimator!!.interpolator = if (isAboveV) {
                                PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
                            } else {
                                PathInterpolator(0.23f, 0.6f, 0.38f, 1f)
                            }
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
}