package moe.chenxy.miuiextra.hooker.entity.systemui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Insets
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
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
import kotlin.math.abs


object HomeHandleAnimatorHooker : YukiBaseHooker() {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")

    @Volatile
    var mIsInHome = false

    enum class EventType {
        NORMAL,
        HOME,
        PRESSED
    }

    data class HomeHandleSettings(
        val transDegree: Int,
        val scaleX: Float,
        val scaleY: Float,
        val alphaAnimDuration: Long,
        val scaleAnimDuration: Long,
        val xyAnimDuration: Long,
    )

    lateinit var zoomXAnimator: ValueAnimator
    lateinit var zoomYAnimator: ValueAnimator
    lateinit var xAnimator: ValueAnimator
    lateinit var yAnimator: ValueAnimator
    lateinit var alphaAnimator: ValueAnimator
    
    lateinit var normalSettings: HomeHandleSettings
    lateinit var pressedSettings: HomeHandleSettings
    lateinit var onHomeSettings: HomeHandleSettings

    @SuppressLint("DiscouragedApi")
    override fun onHook() {
        val isAboveU = ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)
        var mContext: Context? = null
        var mHandler: Handler? = null

        lateinit var mHomeHandle: View

        var mHomeHandleId: Int = -1
        var barHeight = 0
        var origBarHeight = 0
        val yOffset = mainPrefs.getInt("home_handle_y_val", 7)
        var useMiBlur = mainPrefs.getBoolean("chen_home_handle_blur_effect", false)
        var mLightColor = -1
        var mDarkColor = -1
        var mNavigationHandle : Any? = null

        fun getSetting(type: EventType): HomeHandleSettings {
            return HomeHandleSettings(
                mainPrefs.getInt("home_handle_setting_transdegree_${type.name.lowercase()}", 30),
                mainPrefs.getInt("home_handle_setting_scalex_${type.name.lowercase()}", 100)
                    .toFloat() / 100,
                mainPrefs.getInt("home_handle_setting_scaley_${type.name.lowercase()}", 100)
                    .toFloat() / 100,
                mainPrefs.getLong("home_handle_setting_duration_alpha_${type.name.lowercase()}", 600),
                mainPrefs.getLong("home_handle_setting_duration_scale_${type.name.lowercase()}", 600),
                mainPrefs.getLong("home_handle_setting_duration_xy_${type.name.lowercase()}", 600),
            )
        }

        fun updateSettings() {
            if (mainPrefs.hasFileChanged()) {
                mainPrefs.reload()
                normalSettings = getSetting(EventType.NORMAL)
                pressedSettings = getSetting(EventType.PRESSED)
                onHomeSettings = getSetting(EventType.HOME)
            }

        }

        fun animateZoomTo(scaleX: Float, scaleY: Float, duration: Long) {
            if (!this::zoomXAnimator.isInitialized) {
                zoomXAnimator = ValueAnimator()
                zoomYAnimator = ValueAnimator()
                zoomXAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)
                zoomYAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

                zoomXAnimator.addUpdateListener {
                    mHomeHandle.scaleX = it.animatedValue as Float
                }
                zoomYAnimator.addUpdateListener {
                    mHomeHandle.scaleY = it.animatedValue as Float
                }
            }
            if (zoomXAnimator.isRunning) zoomXAnimator.cancel()
            if (zoomYAnimator.isRunning) zoomYAnimator.cancel()

            zoomXAnimator.setFloatValues(mHomeHandle.scaleX, scaleX)
            zoomYAnimator.setFloatValues(mHomeHandle.scaleY, scaleY)
            zoomXAnimator.duration = duration
            zoomYAnimator.duration = duration

            if (mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)) {
                mHomeHandle.pivotY = (barHeight - origBarHeight + origBarHeight / 2).toFloat()
            }

            zoomXAnimator.start()
            zoomYAnimator.start()
        }

//        fun animateHomeHandleZoom(zoomType: ZoomType) {
//            val duration =
//                when (zoomType) {
//                    ZoomType.ZOOM_IN -> 600
//                    ZoomType.NORMAL_FOR_OPACITY, ZoomType.ZOOM_OUT -> 800
//                    else -> 400
//                }
//            zoomValueAnimator!!.setFloatValues(mHomeHandle.scaleX, if (zoomType == ZoomType.ZOOM_IN) 1.05f else if (zoomType == ZoomType.ZOOM_OUT) 0.9f else 1.0f)
//
//
//            zoomValueAnimator!!.start()
//            animateZoomTo()
//        }


        fun animateHomeHandleX(duration: Long, offset: Float) {
            if (!this::xAnimator.isInitialized) {
                xAnimator = ValueAnimator()
                xAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)
            }
            xAnimator.duration = duration
            xAnimator.setFloatValues(mHomeHandle.translationX, offset)

            xAnimator.addUpdateListener {
                mHomeHandle.translationX = it.animatedValue as Float
            }
            xAnimator.start()
        }

        fun animateHomeHandleY(duration: Long, offset: Float) {
            if (!this::yAnimator.isInitialized) {
                yAnimator = ValueAnimator()
                yAnimator.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

                yAnimator.addUpdateListener {
                    mHomeHandle.translationY = it.animatedValue as Float
                }
            }

            yAnimator.duration = duration
            yAnimator.setFloatValues(mHomeHandle.translationY, offset)

            yAnimator.start()
        }

        fun animateHomeHandleXYToNormal() {
            animateHomeHandleX(normalSettings.xyAnimDuration, 0f)
            animateHomeHandleY(normalSettings.xyAnimDuration, yOffset.toFloat())
        }

        fun setDarkIntensity(int: Int) {
            if (mNavigationHandle != null) {
                XposedHelpers.callMethod(mNavigationHandle, "setDarkIntensity", int)
            }
        }

        var isAppliedMiBlur = true
        var currBlurRadius = 0
        val opacityHomeHandleRunnable = Runnable {
            if (alphaAnimator.isRunning) {
                alphaAnimator.cancel()
            }
            alphaAnimator.start()
        }

        val doOnOpacityUpdate = ValueAnimator.AnimatorUpdateListener {
            if (!useMiBlur || !mHomeHandle.isSupportMiBlur()) {
                // Non-Blur - Adjust alpha when doing anim
                mHomeHandle.alpha = it.animatedValue as Float
            } else {
                // Adjust Blur Radius
                currBlurRadius = (1 - (it.animatedValue as Float) * 10).toInt()
                mHomeHandle.setMiBackgroundBlurRadius(currBlurRadius)
            }
        }

        fun opacityTo(to: Float, duration: Long) {
            if (!this::alphaAnimator.isInitialized) {
                alphaAnimator = ValueAnimator()
            }
            alphaAnimator.duration = duration
            alphaAnimator.addUpdateListener(doOnOpacityUpdate)
            alphaAnimator.start()
        }

        fun opacityHomeHandle(type: EventType) {
            updateSettings()

            useMiBlur = mainPrefs.getBoolean("chen_home_handle_blur_effect", false)
            val isHome = mIsInHome

            if (!this::alphaAnimator.isInitialized) {
                alphaAnimator = ValueAnimator()
            }

            if (alphaAnimator.isRunning) {
                if (isHome) {
                    alphaAnimator.cancel()
                } else {
                    alphaAnimator.doOnEnd {
                        it.removeAllListeners()
                        alphaAnimator.addUpdateListener(doOnOpacityUpdate)
                        opacityHomeHandle(type)
                    }
                    return
                }
            }
            val duration =
                when {
                    type == EventType.HOME
                        // Normal or Press to Home
                        -> onHomeSettings.alphaAnimDuration
                    type == EventType.NORMAL && mHomeHandle.alpha != 0f
                        // Pressed to Normal
                        -> normalSettings.alphaAnimDuration * 2
                    type == EventType.NORMAL
                        // Home to Normal
                        -> normalSettings.alphaAnimDuration
                    else
                        // Normal to Press
                        -> pressedSettings.alphaAnimDuration
                }

            if (type != EventType.HOME && useMiBlur && !isAppliedMiBlur) {
                if (mHomeHandle.isSupportMiBlur()) {
                    mHomeHandle.alpha = 1f
                    mDarkColor = 0x55191919
                    mLightColor = 0x77ffffff
                    mHomeHandle.setMiBackgroundBlurModeCompat(1)
                    mHomeHandle.setPassWindowBlurEnabledCompat(true)
                    mHomeHandle.setMiViewBlurMode(2)
                    mHomeHandle.setMiBackgroundBlurRadius(normalSettings.transDegree)
//                        setDarkIntensity(currentIntensity)
                    isAppliedMiBlur = true
                }
            } else if (!useMiBlur && isAppliedMiBlur) {
                isAppliedMiBlur = false
                mDarkColor = 0xff191919.toInt()
                mLightColor = 0xffffffff.toInt()
//                mHomeHandle.setMiBackgroundBlurModeCompat(0)
//                mHomeHandle.setMiViewBlurMode(2)
                mHomeHandle.setMiBackgroundBlurRadius(0)
                mHomeHandle.setPassWindowBlurEnabledCompat(false)
            }

            if (useMiBlur) {
                alphaAnimator.setFloatValues(
                    currBlurRadius.toFloat(),
                    when (type) {
                        EventType.HOME -> onHomeSettings.transDegree.toFloat()
                        EventType.NORMAL -> normalSettings.transDegree.toFloat()
                        else -> pressedSettings.transDegree.toFloat()
                    }
                )
            } else {
                // Normal alpha
                alphaAnimator.setFloatValues(
                    mHomeHandle.alpha,
                    when (type) {
                        EventType.HOME -> 1 - (onHomeSettings.transDegree.toFloat() / 100)
                        EventType.NORMAL -> 1 - (normalSettings.transDegree.toFloat() / 100)
                        else -> 1 - (pressedSettings.transDegree.toFloat() / 100)
                    }
                )
            }
            alphaAnimator.duration = duration

            mHandler?.removeCallbacks(opacityHomeHandleRunnable)
            if (type == EventType.NORMAL && !mIsInHome) {
                mHandler?.postDelayed(opacityHomeHandleRunnable, 5000)
            } else {
                mHandler?.post(opacityHomeHandleRunnable)
            }

            when (type) {
                EventType.NORMAL -> animateZoomTo(normalSettings.scaleX,
                    normalSettings.scaleY,
                    normalSettings.scaleAnimDuration)
                EventType.HOME -> animateZoomTo(onHomeSettings.scaleX, onHomeSettings.scaleY, onHomeSettings.scaleAnimDuration)
                else -> animateZoomTo(pressedSettings.scaleX, pressedSettings.scaleY, pressedSettings.scaleAnimDuration)
            }
        }

        fun leaveHome() {
            opacityHomeHandle(EventType.NORMAL)
        }
        fun enterHome() {
            opacityHomeHandle(EventType.HOME)
        }

        var baseX = -1f
        var baseY = -1f
        var motionTriggered = false
        val isBoostMode = mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)
        var orientation = 0
        // Cache Screen Height. IPC is expensive
        var screenRealHeight: Int
        var screenHeight: Int
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
                        if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN && mHomeHandle.alpha == 0f) {
                            // drop if home handle is transparent.
                            return@post
                        }

                        screenRealHeight = getScreenRealHeight(mContext!!)
                        screenHeight = getScreenHeight(mContext!!)
                        // onInputEvent will be done if mHandler post, so the original motionEvent will be recycled, so that we use the copy of event and recycle by ourself
                        val isNavigationBarArea: Boolean =
                            if (!isAboveU && screenRealHeight - screenHeight > 1) {
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
                                if (!mIsInHome || mHomeHandle.alpha != 0f) {
                                    opacityHomeHandle(EventType.PRESSED)
                                }
//                                animateZoomTo()

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
                            animateHomeHandleXYToNormal()
                            if (!mIsInHome) {
                                // Pressed to Normal
                                opacityHomeHandle(EventType.NORMAL)
                            } else if (onHomeSettings.transDegree == 100 && mHomeHandle.alpha != 0f) {
                                opacityHomeHandle(EventType.HOME)
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
                    if (isAboveU && mHomeHandleId == -1) {
                        mHomeHandleId =
                            appResources!!.getIdentifier("home_handle", "id", appContext?.packageName)
                    }
                    mHomeHandle = mHorizontal.findViewById(mHomeHandleId)
//                    mHomeHandle.translationY = yOffset.toFloat()
                    // Let's Animate to our preset value
                    if (useMiBlur && mHomeHandle.isSupportMiBlur()){
                        // Blur Mode
                        mHomeHandle.alpha = if (mIsInHome) 0f else 1f
                    }

                    mainPrefs.reload()
                    normalSettings = getSetting(EventType.NORMAL)
                    pressedSettings = getSetting(EventType.PRESSED)
                    onHomeSettings = getSetting(EventType.HOME)

                    opacityHomeHandle(EventType.NORMAL)
                    animateHomeHandleXYToNormal()
                    animateZoomTo(normalSettings.scaleX, normalSettings.scaleY, normalSettings.scaleAnimDuration)
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
                intent.`package`= "com.miui.home"
                val mUtilsContext =
                    XposedHelpers.getObjectField(this.instance, "mContext") as Context

                if (currentTopActivity.packageName == "com.miui.home") {
//                        Log.i("Art_Chen", "Current Top Activity is MiuiHome, do sth")
                    intent.putExtra("isEnteredHome", true)
                    mIsInHome = true
                    enterHome()
                } else {
//                        Log.i("Art_Chen", "Current Top Activity is Not MiuiHome!")
                    intent.putExtra("isEnteredHome", false)
                    mIsInHome = false
                    leaveHome()
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