package moe.chenxy.miuiextra.hooker.entity.home

import android.util.Log
import android.view.MotionEvent
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.MotionEventClass
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.MiuiHomeHook

object IconLaunchAnimHooker : YukiBaseHooker() {
    override fun onHook() {
        val mRunnable = Runnable {}
        // Don't cancel the AppToHome Anim if Action Down
        "com.miui.home.recents.NavStubView"
            .hook {
                injectMember {
                    method {
                        name = "onInputConsumerEvent"
                        param(MotionEventClass)
                    }
                    beforeHook {
                        MiuiHomeHook.mAppToHomeAnim2Bak =
                            XposedHelpers.getObjectField(this.instance, "mAppToHomeAnim2")
                        if (MiuiHomeHook.mAppToHomeAnim2Bak != null) {
                            XposedHelpers.setObjectField(
                                this.instance,
                                "mAppToHomeAnim2",
                                null
                            )
                        }
                    }
                    afterHook {
                        val motionEvent = this.args[0] as MotionEvent
                        Log.v(
                            "Art_Chen",
                            "onInputConsumerEvent: Action: ${motionEvent.action}, return ${this.result}. x: ${motionEvent.x} y: ${motionEvent.y}"
                        )
                        val mAppToHomeAnim2 =
                            XposedHelpers.getObjectField(this.instance, "mAppToHomeAnim2")
                        if (mAppToHomeAnim2 == null && MiuiHomeHook.mAppToHomeAnim2Bak != null) {
                            XposedHelpers.setObjectField(
                                this.instance,
                                "mAppToHomeAnim2",
                                MiuiHomeHook.mAppToHomeAnim2Bak
                            )
                        }
                    }
                }.ignoredNoSuchMemberFailure()
            }.ignoredHookClassNotFoundFailure()

        // Hook GestureModeApp for Pad
        "com.miui.home.recents.GestureModeApp".hook {
            injectMember {
                method {
                    name = "onInputConsumerEvent"
                    param(MotionEventClass)
                }
                beforeHook {
                    MiuiHomeHook.mAppToHomeAnim2Bak =
                        XposedHelpers.getObjectField(this.instance, "mAppToHomeAnim2")
                    if (MiuiHomeHook.mAppToHomeAnim2Bak != null) {
                        XposedHelpers.setObjectField(
                            this.instance,
                            "mAppToHomeAnim2",
                            null
                        )
                    }
                }
                afterHook {
                    val motionEvent = this.args[0] as MotionEvent
                    Log.v(
                        "Art_Chen",
                        "onInputConsumerEvent: Action: ${motionEvent.action}, return ${this.result}. x: ${motionEvent.x} y: ${motionEvent.y}"
                    )
                    val mAppToHomeAnim2 =
                        XposedHelpers.getObjectField(this.instance, "mAppToHomeAnim2")
                    if (mAppToHomeAnim2 == null && MiuiHomeHook.mAppToHomeAnim2Bak != null) {
                        XposedHelpers.setObjectField(
                            this.instance,
                            "mAppToHomeAnim2",
                            MiuiHomeHook.mAppToHomeAnim2Bak
                        )
                    }
                }
            }.ignoredNoSuchMemberFailure()
        }.ignoredHookClassNotFoundFailure()

        // Don't run PerformClickRunnable early
        "com.miui.home.launcher.ItemIcon".hook {
            injectMember {
                method {
                    name = "initPerformClickRunnable"
                }
                replaceUnit {
                    XposedHelpers.setObjectField(this.instance, "mPerformClickRunnable", mRunnable)
                }
            }
        }
    }
}