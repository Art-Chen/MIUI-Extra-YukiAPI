package moe.chenxy.miuiextra.hooker.entity.home

import android.R.attr.classLoader
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers


object AnimationEnhanceHooker : YukiBaseHooker() {
    override fun onHook() {
        // Force enable breakable anim support
        "com.miui.home.launcher.common.DeviceLevelUtils".toClass().apply {
            method {
                name = "isUseSimpleAnim"
            }.hook {
                replaceToFalse()
            }
        }

        "com.miui.home.launcher.common.BlurUtils".toClass().apply {
            method {
                name = "isUseCompleteRecentsBlurAnimation"
            }.hook {
                replaceToTrue()
            }
        }
    }
}