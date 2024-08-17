package moe.chenxy.miuiextra.hooker.entity.home

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.highcapable.yukihookapi.hook.type.java.IntType
import de.robv.android.xposed.XposedHelpers

object ViewBlurHooker : YukiBaseHooker() {
    override fun onHook() {
        var launcherBlurActivated = false

        fun View.getMiViewBlurModeCompat() = XposedHelpers.callMethod(
            this, "getMiViewBlurMode") as Int
        // Workaround NavBar zindex higher than launcher caused mi blur not draw the blur view
        ViewClass.apply {
            method {
                name = "setMiBackgroundBlurRadius"
                param(IntType)
            }.hook {
                before {
                    if ((this.instance as View).getMiViewBlurModeCompat() != 1) {
                        return@before
                    }

                    val isActive = this.args[0] as Int > 0
//                    if (isActive == launcherBlurActivated) return@before
//
//                    launcherBlurActivated = isActive

                    val mContext = XposedHelpers.getObjectField(this.instance, "mContext") as Context
                    val intent = Intent("chen.action.home.blur.state.switched")
                    intent.`package`= "com.android.systemui"
                    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    intent.putExtra("active", isActive)
                    mContext.sendBroadcast(intent)
                }
            }
        }
    }

}