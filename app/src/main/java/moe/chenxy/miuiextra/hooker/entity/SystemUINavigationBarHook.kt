package moe.chenxy.miuiextra.hooker.entity

import android.animation.ValueAnimator
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
import com.highcapable.yukihookapi.hook.type.android.AttributeSetClass
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.LayoutInflaterClass
import com.highcapable.yukihookapi.hook.type.android.ViewGroupClass
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import java.lang.reflect.Proxy
import kotlin.math.abs


object SystemUINavigationBarHook : YukiBaseHooker() {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")

    @Volatile
    var mIsInHome = false

    override fun onHook() {
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

        fun animateHomeHandleZoom(isZoom: Boolean) {
            if (zoomValueAnimator == null) {
                zoomValueAnimator = ValueAnimator()
                zoomValueAnimator!!.interpolator = PathInterpolator(0.39f, 1.58f, 0.44f, 1.07f)

                zoomValueAnimator!!.addUpdateListener {
                    mHomeHandle.scaleY = it.animatedValue as Float + if (isZoom) 0.1f else 0f
                    mHomeHandle.scaleX = it.animatedValue as Float
                }
            }

            zoomValueAnimator!!.duration = if (isZoom) 600 else 400
            zoomValueAnimator!!.setFloatValues(mHomeHandle.scaleX, if (isZoom) 1.05f else 1.0f)
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

        homeHandleAlphaAnimator.addUpdateListener {
            mHomeHandle.alpha = it.animatedValue as Float
        }
        var mPendingOpacityStatus = false
        val opacityHomeHandleRunnable = Runnable {
            if (homeHandleAlphaAnimator.isRunning) {
                homeHandleAlphaAnimator.cancel()
            }
            homeHandleAlphaAnimator.start()
        }

        fun opacityHomeHandle(needOpacity: Boolean, needToTransparent: Boolean) {
            mainPrefs.reload()
            if (mainPrefs.getBoolean("chen_home_handle_auto_transparent", false)) {
                val isHome = mIsInHome
                if (homeHandleAlphaAnimator.isRunning) {
                    if (isHome) {
                        homeHandleAlphaAnimator.cancel()
                    } else {
                        mPendingOpacityStatus = needOpacity
                        homeHandleAlphaAnimator.doOnEnd {
                            it.removeAllListeners()
                            homeHandleAlphaAnimator.addUpdateListener { it1 ->
                                mHomeHandle.alpha = it1.animatedValue as Float
                            }
                            opacityHomeHandle(mPendingOpacityStatus, needToTransparent)
                        }
                        return
                    }
                }
                homeHandleAlphaAnimator.duration =
                    if (needToTransparent) 300 else if (needOpacity) 2000 else 400
//                homeHandleAlphaAnimator.interpolator = PathInterpolator(0.39f, 0f, 0.11f, 1.02f)
                homeHandleAlphaAnimator.setFloatValues(
                    mHomeHandle.alpha,
                    if (needToTransparent)
                        0f
                    else if (needOpacity)
                        0.7f
                    else
                        1.0f
                )
                mHandler?.removeCallbacks(opacityHomeHandleRunnable)
                if (needOpacity && !isHome) {
                    mHandler?.postDelayed(opacityHomeHandleRunnable, 5000)
                } else {
                    mHandler?.post(opacityHomeHandleRunnable)
                }
            } else {
                mHomeHandle.alpha = 1.0f
            }
        }

        var baseX = -1f
        var baseY = -1f
        var motionTriggered = false
        val isBoostMode = mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)
        var orientation = 0
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
                        // onInputEvent will be done if mHandler post, so the original motionEvent will be recycled, so that we use the copy of event and recycle by ourself
                        val isNavigationBarArea: Boolean =
                            if (getScreenRealHeight(mContext!!) - getScreenHeight(mContext!!) > 1) {
                                motionEvent.y > getScreenHeight(mContext!!)
                            } else {
                                Log.v(
                                    "Art_Chen",
                                    "Screen Height incorrect to calculate nav bar height, using fallback, value: ${
                                        getScreenRealHeight(mContext!!) - origBarHeight
                                    }"
                                )
                                motionEvent.y > getScreenRealHeight(mContext!!) - origBarHeight
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
                                animateHomeHandleZoom(true)
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
                            }
                            mHomeHandle.translationX = -(baseX - motionEvent.x) * 0.2f
                        }

                        if (motionEvent.actionMasked == MotionEvent.ACTION_UP && motionTriggered) {
                            animateHomeHandleToNormal()
                            animateHomeHandleZoom(false)
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
        "com.android.systemui.navigationbar.NavigationBarInflaterView".hook {
            injectMember {
                method {
                    name = "createView"
                    param(StringClass, ViewGroupClass, LayoutInflaterClass)
                }
                afterHook {
                    val str = this.args[0] as String
                    if ("home_handle" == extractButton(str)) {
                        mHomeHandleId = (this.result as View).id
                    }
                }
            }

            injectMember {
                constructor {
                    param(ContextClass, AttributeSetClass)
                }
                afterHook {
                    mContext = this.args[0] as Context
                    mHandler = Handler(Looper.getMainLooper())

                    val miuiGestureListenerClass = "com.android.keyguard.fod.MiuiGestureListener".toClass()
                    val miuiGestureMonitorClass = "com.android.keyguard.fod.MiuiGestureMonitor".toClass()
                    val chenListener = Proxy.newProxyInstance(
                        appClassLoader,
                        arrayOf(miuiGestureListenerClass)
                    ) { _, _, args ->
                        onInputEvent(args?.get(0) as InputEvent)
                    }
                    val miuiGestureMonitorInstance = XposedHelpers.callStaticMethod(
                        miuiGestureMonitorClass,
                        "getInstance",
                        mContext
                    )
                    Log.i(
                        "Art_Chen",
                        "register chen pointer event listener when navigation bar view init"
                    )
                    XposedHelpers.callMethod(
                        miuiGestureMonitorInstance,
                        "registerPointerEventListener",
                        chenListener
                    )
                }
            }

            injectMember {
                method {
                    name = "inflateLayout"
                    param(StringClass)
                }
                afterHook {
                    // init home handle status
                    val mHorizontal =
                        XposedHelpers.getObjectField(this.instance, "mHorizontal") as FrameLayout
                    mHomeHandle = mHorizontal.findViewById(mHomeHandleId)
                    mHomeHandle.translationY = yOffset.toFloat()
                    if (mainPrefs.getBoolean("chen_home_handle_auto_transparent", false)) {
                        mHomeHandle.alpha = if (mIsInHome) 0f else 0.7f
                    }
                    animateHomeHandleToNormal()
                }
            }
        }

        "com.android.systemui.navigationbar.NavigationBar".hook {
            injectMember {
                method {
                    name = "getBarLayoutParamsForRotation"
                    param(IntType)
                }
                afterHook {
                    val orientationFor = this.args[0] as Int
                    val isTurboMode =
                        mainPrefs.getBoolean("chen_home_handle_anim_turbo_mode", false)
                    if (XposedHelpers.getIntField(this.instance, "mNavBarMode") != 2) {
                        return@afterHook
                    }

                    val lp = this.result as WindowManager.LayoutParams

                    if (orientationFor == 0) {
                        origBarHeight = lp.height
                    }

                    if (isTurboMode && orientationFor == 0) {
                        lp.height = lp.height * 2
                    }
                    val insets =
                        XposedHelpers.getObjectField(lp, "providedInternalInsets") as Array<Insets>
                    if (mainPrefs.getBoolean(
                            "chen_home_handle_no_space",
                            false
                        ) && orientationFor == 0
                    ) {
                        insets[1] = Insets.of(0, lp.height, 0, 0)
                        XposedHelpers.setObjectField(lp, "providedInternalInsets", insets)
                    } else if (isTurboMode) {
                        insets[1] = Insets.of(0, lp.height - origBarHeight, 0, 0)
                        XposedHelpers.setObjectField(lp, "providedInternalInsets", insets)
                    }
                    if (orientationFor == 0) {
                        barHeight = lp.height
                    }
                    Log.i(
                        "Art_Chen",
                        "screenHeight is ${getScreenHeight(mContext!!)}, origBarHeight $origBarHeight, currentBarHeight $barHeight"
                    )
                }
            }

            injectMember {
                method {
                    name = "repositionNavigationBar"
                    param(IntType)
                }
                beforeHook {
                    orientation = this.args[0] as Int
                }
            }

        }

        "com.miui.systemui.util.MiuiActivityUtil".hook {
            injectMember {
                method {
                    name = "notifyListeners"
                }
                afterHook {
                    mainPrefs.reload()
                    val currentTopActivity = XposedHelpers.getObjectField(
                        this.instance,
                        "mTopActivity"
                    ) as ComponentName
                    val intent = Intent("chen.action.top_activity.switched")
                    val mUtilsContext =
                        XposedHelpers.getObjectField(this.instance, "mContext") as Context
                    val mEnabledAutoTransparent =
                        mainPrefs.getBoolean("chen_home_handle_full_transparent_at_miuihome", false)

                    if (currentTopActivity.packageName == "com.miui.home") {
//                        Log.i("Art_Chen", "Current Top Activity is MiuiHome, do sth")
                        intent.putExtra("isEnteredHome", true)
                        if (mEnabledAutoTransparent) {
                            mIsInHome = true
                            opacityHomeHandle(needOpacity = true, needToTransparent = true)
                        }
                    } else {
//                        Log.i("Art_Chen", "Current Top Activity is Not MiuiHome!")
                        intent.putExtra("isEnteredHome", false)
                        if (mEnabledAutoTransparent) {
                            opacityHomeHandle(needOpacity = false, needToTransparent = false)
                            opacityHomeHandle(needOpacity = true, needToTransparent = false)
                        }
                        mIsInHome = false
                    }
                    mUtilsContext.sendBroadcast(intent)
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