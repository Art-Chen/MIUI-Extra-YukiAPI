package moe.chenxy.miuiextra.hooker.entity.home

import android.graphics.Color
import android.widget.TextView
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import moe.chenxy.miuiextra.hooker.entity.MiuiHomeHook.mainPrefs
import kotlin.math.abs

object TitleShadowHooker : YukiBaseHooker() {
    override fun onHook() {
        val enableShadow = mainPrefs.getBoolean("disable_wallpaper_auto_darken", false) && mainPrefs.getBoolean("enable_home_text_shadow", false)
        "com.miui.home.launcher.DeviceConfig".toClass().apply {
            method {
                name = "checkDarkenWallpaperSupport"
                param(ContextClass)
            }.hook {
                replaceTo(!enableShadow)
            }
        }

        "com.miui.home.launcher.common.Utilities".toClass().apply {
            method {
                name = "setTitleShadow"
                paramCount = 3
            }.hook {
                replaceUnit {
                    if (!enableShadow) return@replaceUnit

                    val textView = this.args[1] as TextView
//                    val color = this.args[2] as Int
                    val textColor = textView.currentTextColor
                    val reversedColor = Color.rgb(abs(245 - textColor.red),abs(245 - textColor.green),abs(245 - textColor.blue))
                    textView.setShadowLayer(30.0f, 0f, 0f, reversedColor)
                }
            }
        }

    }
}