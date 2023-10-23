package moe.chenxy.miuiextra.hooker.entity.home

import android.util.Log
import android.view.MotionEvent
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.MotionEventClass
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.MiuiHomeHook

object IconLaunchAnimHooker : YukiBaseHooker() {
    private var mAppToHomeAnim2Bak: Any? = null
    override fun onHook() {
        val mRunnable = Runnable {}
        // Don't cancel the AppToHome Anim if Action Down
        "com.miui.home.recents.NavStubView".toClass().method {
            name = "onInputConsumerEvent"
            param(MotionEventClass)
        }.hook {
            before {
                mAppToHomeAnim2Bak =
                    XposedHelpers.getObjectField(this.instance, "mAppToHomeAnim2")
                if (mAppToHomeAnim2Bak != null) {
                    XposedHelpers.setObjectField(
                        this.instance,
                        "mAppToHomeAnim2",
                        null
                    )
                }
            }
            after {
                val motionEvent = this.args[0] as MotionEvent
                Log.v(
                    "Art_Chen",
                    "onInputConsumerEvent: Action: ${motionEvent.action}, return ${this.result}. x: ${motionEvent.x} y: ${motionEvent.y}"
                )
                val mAppToHomeAnim2 =
                    XposedHelpers.getObjectField(this.instance, "mAppToHomeAnim2")
                if (mAppToHomeAnim2 == null && mAppToHomeAnim2Bak != null) {
                    XposedHelpers.setObjectField(
                        this.instance,
                        "mAppToHomeAnim2",
                        mAppToHomeAnim2Bak
                    )
                }
            }
        }

        // Hook GestureModeApp for Pad
        "com.miui.home.recents.GestureModeApp".toClass().method {
            name = "onInputConsumerEvent"
            param(MotionEventClass)
        }.hook { 
            before {
                mAppToHomeAnim2Bak =
                    XposedHelpers.getObjectField(instance, "mAppToHomeAnim2")
                if (mAppToHomeAnim2Bak != null) {
                    XposedHelpers.setObjectField(
                        instance,
                        "mAppToHomeAnim2",
                        null
                    )
                }
            }
            after {
                val motionEvent = args[0] as MotionEvent
                Log.v(
                    "Art_Chen",
                    "onInputConsumerEvent: Action: ${motionEvent.action}, return $result. x: ${motionEvent.x} y: ${motionEvent.y}"
                )
                val mAppToHomeAnim2 =
                    XposedHelpers.getObjectField(instance, "mAppToHomeAnim2")
                if (mAppToHomeAnim2 == null && mAppToHomeAnim2Bak != null) {
                    XposedHelpers.setObjectField(
                        instance,
                        "mAppToHomeAnim2",
                        mAppToHomeAnim2Bak
                    )
                }
            }
        }

        // Don't run PerformClickRunnable early
        "com.miui.home.launcher.ItemIcon".toClass().method {
                    name = "initPerformClickRunnable"
        }.hook {
            replaceUnit {
                XposedHelpers.setObjectField(instance, "mPerformClickRunnable", mRunnable)
            }
        }
    }
}