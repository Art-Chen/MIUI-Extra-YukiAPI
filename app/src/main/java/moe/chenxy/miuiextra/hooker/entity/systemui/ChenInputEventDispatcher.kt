package moe.chenxy.miuiextra.hooker.entity.systemui

import android.content.Context
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.InputEvent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.systemui.HomeHandleAnimatorHooker.appContext
import moe.chenxy.miuiextra.hooker.entity.systemui.HomeHandleAnimatorHooker.loadHooker
import moe.chenxy.miuiextra.hooker.entity.systemui.HomeHandleAnimatorHooker.toClass
import moe.chenxy.miuiextra.utils.ChenUtils
import java.lang.reflect.Proxy

object ChenInputEventDispatcher {
    private val listeners = ArrayList<ChenInputEventListener>()
    private val isAboveU = ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)
    private var inited = false
    private var mInputEventReceiver: Any? = null
    private var mInputMonitor: Any? = null

    fun isInited(): Boolean {
        return inited
    }

    fun init(context: Context, appClassLoader: ClassLoader) {
        if (inited) return

        if (isAboveU) {
            // Android U register the listener directly because of the fod listener dropped some interfaces
            val inputEventReceiverCls = "com.android.systemui.shared.system.InputChannelCompat\$InputEventReceiver".toClass(appClassLoader)
            val inputEventListenerCls = "com.android.systemui.shared.system.InputChannelCompat\$InputEventListener".toClass(appClassLoader)
            val inputManagerGlobalCls = "android.hardware.input.InputManagerGlobal".toClass(appClassLoader)
            val inputManager = XposedHelpers.callStaticMethod(inputManagerGlobalCls, "getInstance")
            mInputMonitor = XposedHelpers.callMethod(inputManager, "monitorGestureInput", "Chen-HomeHandle-Touch", appContext?.display?.displayId)
            val inputChannel = XposedHelpers.callMethod(mInputMonitor, "getInputChannel")
            val looper = appContext?.mainLooper
            val choreographer = Choreographer.getInstance()
            val chenListener = Proxy.newProxyInstance(
                appClassLoader,
                arrayOf(inputEventListenerCls)
            ) { _, _, args ->
                notifyListeners(args?.get(0) as InputEvent)
            }

            mInputEventReceiver = inputEventReceiverCls.declaredConstructors[0].newInstance(
                inputChannel,
                looper,
                choreographer,
                chenListener
            )
            Log.i(
                "Art_Chen",
                "[ChenInputEventDispatcher] init done on Android U!"
            )
            inited = true
            return
        }

        // Android T uses fod touch listener
        val miuiGestureListenerClass =
            "com.android.keyguard.fod.MiuiGestureListener".toClass()
        val miuiGestureMonitorClass =
            "com.android.keyguard.fod.MiuiGestureMonitor".toClass()
        val chenListener = Proxy.newProxyInstance(
            appClassLoader,
            arrayOf(miuiGestureListenerClass)
        ) { _, _, args ->
            notifyListeners(args?.get(0) as InputEvent)
        }
        val miuiGestureMonitorInstance = XposedHelpers.callStaticMethod(
            miuiGestureMonitorClass,
            "getInstance",
            context
        )
        Log.i(
            "Art_Chen",
            "[ChenInputEventDispatcher] init on Android T (Legacy Fod Listener)"
        )
        XposedHelpers.callMethod(
            miuiGestureMonitorInstance,
            "registerPointerEventListener",
            chenListener
        )

        inited = true
    }

    fun unregister() {
        if (isAboveU) {
            if (mInputMonitor != null) {
                XposedHelpers.callMethod(mInputMonitor, "dispose")
            }

            if (mInputEventReceiver != null) {
                val mReceiver = XposedHelpers.getObjectField(mInputEventReceiver, "mReceiver")
                XposedHelpers.callMethod(mReceiver, "dispose")
            }
        }

        inited = false
    }

    private fun notifyListeners(inputEvent: InputEvent) {
        for (listener in listeners) {
            listener.onInputEvent(inputEvent)
        }
    }

    fun registerInputEventListener(chenInputEventListener: ChenInputEventListener) {
        listeners.add(chenInputEventListener)
    }

    fun unregisterInputEventListener(chenInputEventListener: ChenInputEventListener) {
        listeners.remove(chenInputEventListener)
    }
}

interface ChenInputEventListener {
    fun onInputEvent(inputEvent: InputEvent)
}