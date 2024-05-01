package moe.chenxy.miuiextra.hooker.entity.systemui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Insets
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.InputEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.appcompat.widget.LinearLayoutCompat.OrientationMode
import androidx.core.animation.doOnEnd
import androidx.core.os.postDelayed
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.AttributeSetClass
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.LayoutInflaterClass
import com.highcapable.yukihookapi.hook.type.android.ViewGroupClass
import com.highcapable.yukihookapi.hook.type.java.FloatType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.hooker.entity.systemui.MiBlurCompatUtils.isSupportMiBlur
import moe.chenxy.miuiextra.hooker.entity.systemui.MiBlurCompatUtils.setMiBackgroundBlurModeCompat
import moe.chenxy.miuiextra.hooker.entity.systemui.MiBlurCompatUtils.setMiBackgroundBlurRadius
import moe.chenxy.miuiextra.hooker.entity.systemui.MiBlurCompatUtils.setMiViewBlurMode
import moe.chenxy.miuiextra.hooker.entity.systemui.MiBlurCompatUtils.setPassWindowBlurEnabledCompat
import moe.chenxy.miuiextra.utils.ChenUtils
import java.lang.reflect.Proxy
import kotlin.math.abs


object HomeHandleAnimatorHooker : YukiBaseHooker() {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")

    @Volatile
    var mIsInHome = false

    enum class ZoomType {
        NORMAL,
        NORMAL_FOR_OPACITY,
        ZOOM_IN,
        ZOOM_OUT
    }

    @SuppressLint("DiscouragedApi")
    override fun onHook() {
        val isAboveU = ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)
        var mContext: Context? = null
        var mHandler: Handler? = null

        lateinit var mHomeHandle: View

        var zoomValueAnimator: ValueAnimator? = null
        var homeHandleXAnimator: ValueAnimator? = null
        var homeHandleYAnimator: ValueAnimator? = null
        val homeHandleAlphaAnimator = ValueAnimator()

        var mHomeHandleId: Int = -1
        var barHeight = 0
        var origBarHeight = 0
        val yOffset = mainPrefs.getInt("home_handle_y_val", 7)
        var mEnabledHomeAutoTransparent =
            mainPrefs.getBoolean("chen_home_handle_full_transparent_at_miuihome", false)
        var useMiBlur = mainPrefs.getBoolean("chen_home_handle_blur_effect", false)
        var mLightColor = -1
        var mDarkColor = -1
        var mNavigationHandle : Any? = null
        fun animateHomeHandleZoom(zoomType: ZoomType) {
            if (zoomValueAnimator == null) {
                zoomValueAnimator = ValueAnimator()
                zoomValueAnimator!!.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

                zoomValueAnimator!!.addUpdateListener {
                    mHomeHandle.scaleY = it.animatedValue as Float + if (zoomType == ZoomType.ZOOM_IN) 0.1f else 0f
                    mHomeHandle.scaleX = it.animatedValue as Float
                }
            }

            zoomValueAnimator!!.duration =
                when (zoomType) {
                    ZoomType.ZOOM_IN -> 600
                    ZoomType.NORMAL_FOR_OPACITY, ZoomType.ZOOM_OUT -> 800
                    else -> 400
                }
            zoomValueAnimator!!.setFloatValues(mHomeHandle.scaleX, if (zoomType == ZoomType.ZOOM_IN) 1.05f else if (zoomType == ZoomType.ZOOM_OUT) 0.9f else 1.0f)
            if (mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)) {
//                Log.i("Art_Chen", "pivotY ${mHomeHandle.pivotY} barheight $barHeight")
                mHomeHandle.pivotY = (barHeight - origBarHeight + origBarHeight / 2).toFloat()
            }

            zoomValueAnimator!!.start()
        }

        fun animateHomeHandleX(duration: Long, offset: Float) {
            if (homeHandleXAnimator == null) {
                homeHandleXAnimator = ValueAnimator()
                homeHandleXAnimator!!.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)
            }
            homeHandleXAnimator!!.duration = duration
            homeHandleXAnimator!!.setFloatValues(mHomeHandle.translationX, offset)

            homeHandleXAnimator!!.addUpdateListener {
                mHomeHandle.translationX = it.animatedValue as Float
            }
            homeHandleXAnimator!!.start()
        }

        fun animateHomeHandleY(duration: Long, offset: Float) {
            if (homeHandleYAnimator == null) {
                homeHandleYAnimator = ValueAnimator()
                homeHandleYAnimator!!.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

                homeHandleYAnimator!!.addUpdateListener {
                    mHomeHandle.translationY = it.animatedValue as Float
                }
            }

            homeHandleYAnimator!!.duration = duration
            homeHandleYAnimator!!.setFloatValues(mHomeHandle.translationY, offset)

            homeHandleYAnimator!!.start()
        }

        fun animateHomeHandleToNormal() {
            animateHomeHandleX(600, 0f)
            animateHomeHandleY(600, yOffset.toFloat())
        }

        fun setDarkIntensity(int: Int) {
            if (mNavigationHandle != null) {
                XposedHelpers.callMethod(mNavigationHandle, "setDarkIntensity", int)
            }
        }

        var mPendingOpacityStatus: Boolean
        var isTransparent = false
        val maxBlurRadius = 20
        val opacityHomeHandleRunnable = Runnable {
            if (homeHandleAlphaAnimator.isRunning) {
                homeHandleAlphaAnimator.cancel()
            }
            homeHandleAlphaAnimator.start()
        }
        homeHandleAlphaAnimator.addUpdateListener {
            if (!useMiBlur || !mHomeHandle.isSupportMiBlur() || mainPrefs.getBoolean("chen_home_handle_auto_transparent", false)) {
                mHomeHandle.alpha = it.animatedValue as Float
            } else {
                if (isTransparent) {
                    mHomeHandle.alpha = it.animatedValue as Float
                } else if (mHomeHandle.alpha == 0f) {
                    mHomeHandle.alpha = 1f
                }
                mHomeHandle.setMiBackgroundBlurRadius(((it.animatedValue as Float) * maxBlurRadius).toInt())
            }
        }

        fun opacityHomeHandle(needOpacity: Boolean, needToTransparent: Boolean) {
            mainPrefs.reload()
            val autoTransparent = mainPrefs.getBoolean("chen_home_handle_auto_transparent", false)
            val useScaleEffect = mainPrefs.getBoolean("home_handle_scale_on_full_transparent", false)
            val transDegree = mainPrefs.getInt("home_handle_auto_trans_alpha_val", 30)
            useMiBlur = mainPrefs.getBoolean("chen_home_handle_blur_effect", false)
            val isHome = mIsInHome
            if (!autoTransparent && needOpacity && !needToTransparent && !useMiBlur) {
                return
            }
            if (homeHandleAlphaAnimator.isRunning) {
                if (isHome) {
                    homeHandleAlphaAnimator.cancel()
                } else {
                    mPendingOpacityStatus = needOpacity
                    homeHandleAlphaAnimator.doOnEnd {
                        it.removeAllListeners()
                        homeHandleAlphaAnimator.addUpdateListener { it1 ->
                            if (!useMiBlur || !mHomeHandle.isSupportMiBlur()) {
                                mHomeHandle.alpha = it1.animatedValue as Float
                            } else {
                                if (isTransparent) {
                                    mHomeHandle.alpha = it1.animatedValue as Float
                                } else if (mHomeHandle.alpha == 0f) {
                                    mHomeHandle.alpha = 1f
                                }
                                mHomeHandle.setMiBackgroundBlurRadius(((it1.animatedValue as Float) * maxBlurRadius).toInt())
                            }
                        }
                        opacityHomeHandle(mPendingOpacityStatus, needToTransparent)
                    }
                    return
                }
            }
            homeHandleAlphaAnimator.duration =
                if (needToTransparent && isAboveU)
                    600
                else if (needToTransparent)
                    300
                else if (needOpacity && mHomeHandle.alpha != 0f)
                    2000
                else if (needOpacity)
                    800
                else
                    400

            if (needOpacity && !needToTransparent && useMiBlur) {
                if (mHomeHandle.isSupportMiBlur()) {
                    mHomeHandle.alpha = 1f
                    mDarkColor = 0x55191919.toInt()
                    mLightColor = 0x77ffffff.toInt()
                    mHomeHandle.setMiBackgroundBlurModeCompat(1)
                    mHomeHandle.setPassWindowBlurEnabledCompat(true)
                    mHomeHandle.setMiViewBlurMode(2)
                    mHomeHandle.setMiBackgroundBlurRadius(maxBlurRadius)
//                        setDarkIntensity(currentIntensity)
                }
            } else {
                if (mHomeHandle.isSupportMiBlur()) {
                    mDarkColor = 0xcc191919.toInt()
                    mLightColor = 0xb3ffffff.toInt()
                    mHomeHandle.setMiBackgroundBlurModeCompat(0)
                    mHomeHandle.setPassWindowBlurEnabledCompat(false)
//                    mHomeHandle.setMiViewBlurMode(1)
                    mHomeHandle.setMiBackgroundBlurRadius(0)
                }
            }

            isTransparent = needToTransparent
//                homeHandleAlphaAnimator.interpolator = PathInterpolator(0.39f, 0f, 0.11f, 1.02f)
            homeHandleAlphaAnimator.setFloatValues(
                mHomeHandle.alpha,
                if (needToTransparent)
                    0f
                else if (needOpacity)
                    1 - (transDegree.toFloat() / 100)
                else
                    1.0f
            )
            mHandler?.removeCallbacks(opacityHomeHandleRunnable)
            val mScaleOnOpacity: Boolean =
                useScaleEffect && (needToTransparent || mHomeHandle.alpha == 0f)
            if (needOpacity && !isHome) {
                mHandler?.postDelayed(opacityHomeHandleRunnable, 5000)
            } else {
                mHandler?.post(opacityHomeHandleRunnable)
            }
            if (mScaleOnOpacity) {
                if (!needToTransparent) {
                    animateHomeHandleZoom(ZoomType.ZOOM_OUT)
                    mHandler?.postDelayed(100) {
                        animateHomeHandleZoom(ZoomType.NORMAL_FOR_OPACITY)
                    }
                } else {
                    animateHomeHandleZoom(ZoomType.ZOOM_OUT)
                }
            }
        }

        var baseX = -1f
        var baseY = -1f
        var motionTriggered = false
        val isBoostMode = mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)
        var orientation = 0
        // Cache Screen Height. IPC is expensive
        var screenRealHeight = -1
        var screenHeight = -1
        fun onInputEvent(inputEvent: InputEvent) {
            if (inputEvent is MotionEvent) {
                // obtain the event to local runnable to fix the race condition
                val motionEvent = MotionEvent.obtain(inputEvent)
//                        Log.i(
//                            "Art_Chen",
//                            "motionEvent actionMasked: ${motionEvent.actionMasked} x: ${motionEvent.x}, y: ${motionEvent.y}"
//                        )
                if (mHandler != null) {
                    mHandler!!.post {
                        if (screenRealHeight == -1) {
                            screenRealHeight = getScreenRealHeight(mContext!!)
                            screenHeight = getScreenHeight(mContext!!)
                        }
                        // onInputEvent will be done if mHandler post, so the original motionEvent will be recycled, so that we use the copy of event and recycle by ourself
                        val isNavigationBarArea: Boolean =
                            if (screenRealHeight - screenHeight > 1 && !isAboveU) {
                                motionEvent.y > screenHeight
                            } else {
                                // on U, look like the real height - orig bar height is better
                                if (!isAboveU) {
                                    Log.v(
                                        "Art_Chen",
                                        "Screen Height incorrect to calculate nav bar height, using fallback, value: ${
                                            screenRealHeight - origBarHeight
                                        }"
                                    )
                                }
                                motionEvent.y > screenRealHeight - origBarHeight
                            }

                        if (isNavigationBarArea) {
                            if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                                Log.v(
                                    "Art_Chen",
                                    "current touch is in navigation area! motionTriggered!"
                                )
                                if (!mIsInHome) {
                                    opacityHomeHandle(false, false)
                                }
                                animateHomeHandleZoom(ZoomType.ZOOM_IN)
                                if (homeHandleXAnimator!!.isRunning) {
                                    homeHandleXAnimator!!.cancel()
                                    homeHandleYAnimator!!.cancel()
                                }
                                baseX = motionEvent.x
                                baseY = motionEvent.y
                                motionTriggered = true
                            }
                            if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE && motionTriggered && (!isBoostMode && orientation == 0)) {
                                val offsetNeeded =
                                    -(baseY - motionEvent.y) * 0.15f + yOffset.toFloat()
                                if (abs(offsetNeeded) < barHeight / 2 - 6) {
                                    mHomeHandle.translationY = offsetNeeded
                                }
                            }
                        }

                        if (motionEvent.actionMasked == MotionEvent.ACTION_MOVE && motionTriggered) {
                            if (isBoostMode && orientation == 0) {
                                val offsetNeeded =
                                    -(baseY - motionEvent.y) * 0.2f + yOffset.toFloat()
                                if (abs(offsetNeeded) < barHeight / 2 + 6) {
                                    mHomeHandle.translationY = offsetNeeded
                                }
                            } else if (orientation != 0) {
                                // Let the Landscape has a little move effect instead of zero y offset.
                                val offsetNeeded =
                                    -(baseY - motionEvent.y) * 0.02f + yOffset.toFloat()
                                if (abs(offsetNeeded) < barHeight / 2 + 6) {
                                    mHomeHandle.translationY = offsetNeeded
                                }
                            }
                            mHomeHandle.translationX = -(baseX - motionEvent.x) * 0.2f
                        }

                        if (motionEvent.actionMasked == MotionEvent.ACTION_UP && motionTriggered) {
                            animateHomeHandleToNormal()
                            if (!mEnabledHomeAutoTransparent || !mIsInHome) {
                                animateHomeHandleZoom(ZoomType.NORMAL)
                            }
                            if (!mIsInHome) {
                                opacityHomeHandle(true, false)
                            }
                            motionTriggered = false
                        }
                        motionEvent.recycle()
                    }
                }
            }
        }

        // Hooks
        "com.android.systemui.navigationbar.NavigationBarInflaterView".toClass().apply {
            if (!isAboveU) {
                method {
                    name = "createView"
                    param(StringClass, ViewGroupClass, LayoutInflaterClass)
                }.hook {
                    after {
                        val str = this.args[0] as String
                        if ("home_handle" == extractButton(str)) {
                            mHomeHandleId = (this.result as View).id
                        }
                    }
                }
            }

            constructor {
                param(ContextClass, AttributeSetClass)
            }.hook {
                after {
                    mContext = this.args[0] as Context
                    mHandler = Handler(Looper.getMainLooper())

                    appClassLoader?.let { ChenInputEventDispatcher.init(mContext!!, it) }

                    if (ChenInputEventDispatcher.isInited()) {
                        ChenInputEventDispatcher.registerInputEventListener(object : ChenInputEventListener {
                            override fun onInputEvent(inputEvent: InputEvent) {
                                onInputEvent(inputEvent)
                            }
                        })
                        Log.i("Art_Chen", "ChenInputEventDispatcher registered!")
                    }
                }
            }

            method {
                name = "inflateLayout"
                param(StringClass)
            }.hook {
                after {
                    // init home handle status
                    val mHorizontal =
                        XposedHelpers.getObjectField(this.instance, "mHorizontal") as FrameLayout
                    val autoTransparent = mainPrefs.getBoolean("chen_home_handle_auto_transparent", false)
                    if (isAboveU && mHomeHandleId == -1) {
                        mHomeHandleId =
                            appResources!!.getIdentifier("home_handle", "id", appContext?.packageName)
                    }
                    mHomeHandle = mHorizontal.findViewById(mHomeHandleId)
                    mHomeHandle.translationY = yOffset.toFloat()
                    if (!useMiBlur || !mHomeHandle.isSupportMiBlur()) {
                        mHomeHandle.alpha = if (mIsInHome) 0f else if (autoTransparent) 0.7f else 1f
                    } else {
                        mHomeHandle.alpha = if (mIsInHome) 0f else 1f
                        // init mi blur
                        opacityHomeHandle(true, false)
                    }
                    animateHomeHandleToNormal()
                }
            }
        }

        "com.android.systemui.navigationbar.NavigationBar".toClass().apply {
            method {
                name = "getBarLayoutParamsForRotation"
                param(IntType)
            }.hook {
                after {
                    val orientationFor = this.args[0] as Int
                    val isTurboMode =
                        mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)
                    if (XposedHelpers.getIntField(this.instance, "mNavBarMode") != 2) {
                        return@after
                    }

                    val lp = this.result as WindowManager.LayoutParams

                    if (orientationFor == 0) {
                        origBarHeight = lp.height
                    }

                    if (isTurboMode && orientationFor == 0) {
                        lp.height = lp.height * 2
                    }

                    lateinit var inset: Insets
                    lateinit var insets: Array<Insets>
                    lateinit var insetsProviderArr: Array<*>
                    lateinit var insetsProviderOverride: Any
                    if (!isAboveU) {
                        insets =
                            XposedHelpers.getObjectField(
                                lp,
                                "providedInternalInsets"
                            ) as Array<Insets>

                        inset = insets[1]
                    } else {
                        // is U
                        insetsProviderArr = XposedHelpers.getObjectField(lp, "providedInsets") as Array<*>
                        insetsProviderOverride = insetsProviderArr[0]!!
                        inset = XposedHelpers.callMethod(insetsProviderOverride, "getInsetsSize") as Insets
                    }

                    if (mainPrefs.getBoolean(
                            "chen_home_handle_no_space",
                            false
                        ) && orientationFor == 0
                    ) {
                        inset = if (isAboveU) {
                            Insets.of(inset.left, 0, inset.right, 0)
                        } else {
                            Insets.of(inset.left, lp.height, inset.right, 0)
                        }
                    } else if (isTurboMode) {
                        inset = if (isAboveU) {
                            Insets.of(inset.left, inset.top, inset.right, origBarHeight)
                        } else {
                            Insets.of(inset.left, lp.height - origBarHeight, inset.right, 0)
                        }
                    }
                    if (orientationFor == 0) {
                        barHeight = lp.height
                    }

                    // Apply Inset
                    if (!isAboveU) {
                        insets[1] = inset
                        XposedHelpers.setObjectField(lp, "providedInternalInsets", insets)
                    } else {
                        XposedHelpers.callMethod(insetsProviderOverride, "setInsetsSize", inset)
                        XposedHelpers.setObjectField(lp, "providedInsets", insetsProviderArr)
                    }
                    Log.i(
                        "Art_Chen",
                        "screenHeight is ${getScreenHeight(mContext!!)}, realScreenHeight is ${getScreenRealHeight(mContext!!)}, origBarHeight $origBarHeight, currentBarHeight $barHeight"
                    )
                    screenHeight = getScreenHeight(mContext!!)
                    screenRealHeight = getScreenRealHeight(mContext!!)
                }
            }

            method {
                name = "repositionNavigationBar"
                param(IntType)
            }.hook {
                before {
                    orientation = this.args[0] as Int
                }
            }
        }

        val activityChangedNotify =
            if (!ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)) {
                "com.miui.systemui.util.MiuiActivityUtil".toClass().method {
                    name = "notifyListeners"
                }
            } else {
                // on U
                "com.miui.systemui.functions.MiuiTopActivityObserver".toClass().method {
                    name = "notifyListeners"
                }
            }

        activityChangedNotify.hook {
            after {
                mainPrefs.reload()
                val currentTopActivity = XposedHelpers.getObjectField(
                    this.instance,
                    "mTopActivity"
                ) as ComponentName
                val intent = Intent("chen.action.top_activity.switched")
                val mUtilsContext =
                    XposedHelpers.getObjectField(this.instance, "mContext") as Context
                mEnabledHomeAutoTransparent = mainPrefs.getBoolean("chen_home_handle_full_transparent_at_miuihome", false)

                if (currentTopActivity.packageName == "com.miui.home") {
//                        Log.i("Art_Chen", "Current Top Activity is MiuiHome, do sth")
                    intent.putExtra("isEnteredHome", true)
                    if (mEnabledHomeAutoTransparent) {
                        mIsInHome = true
                        opacityHomeHandle(needOpacity = true, needToTransparent = true)
                    }
                } else {
//                        Log.i("Art_Chen", "Current Top Activity is Not MiuiHome!")
                    intent.putExtra("isEnteredHome", false)
                    if (mEnabledHomeAutoTransparent) {
                        opacityHomeHandle(needOpacity = false, needToTransparent = false)
                        opacityHomeHandle(needOpacity = true, needToTransparent = false)
                    }
                    mIsInHome = false
                }
                mUtilsContext.sendBroadcast(intent)
            }
        }

        "com.android.systemui.navigationbar.gestural.NavigationHandle".toClass().method {
            name = "setDarkIntensity"
            param(FloatType)
        }.hook {
            before {
                if (mDarkColor == -1) {
                    mDarkColor = XposedHelpers.getIntField(this.instance, "mDarkColor")
                    mLightColor = XposedHelpers.getIntField(this.instance, "mLightColor")
                    mNavigationHandle = this.instance
                } else {
                    XposedHelpers.setIntField(this.instance, "mDarkColor", mDarkColor)
                    XposedHelpers.setIntField(this.instance, "mLightColor", mLightColor)
                }
            }
        }
    }

    private fun getScreenHeight(context: Context): Int {
        return context.resources?.displayMetrics?.heightPixels ?: 0
    }

    private var sRealSizes = arrayOfNulls<Point>(2)
    private fun getScreenRealHeight(context: Context): Int {
        var orientation = context.resources?.configuration?.orientation
        orientation = if (orientation == 1) 0 else 1
        if (sRealSizes[orientation] == null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getRealSize(point)
            sRealSizes[orientation] = point
        }
        return sRealSizes[orientation]?.y ?: getScreenRealHeight(context)
    }

    private fun getScreenRealWidth(context: Context): Int {
        var orientation = context.resources?.configuration?.orientation
        orientation = if (orientation == 1) 0 else 1
        if (sRealSizes[orientation] == null) {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val point = Point()
            display.getRealSize(point)
            sRealSizes[orientation] = point
        }
        return sRealSizes[orientation]?.x ?: getScreenRealWidth(context)
    }


    fun extractButton(str: String): String {
        return if (!str.contains("[")) str else str.substring(0, str.indexOf("["))
    }

}