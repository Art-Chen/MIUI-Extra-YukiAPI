package moe.chenxy.miuiextra.hooker.entity.systemui

import android.animation.ValueAnimator
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.JavaClassLoader
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig


object SystemUIPluginHook : YukiBaseHooker() {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    override fun onHook() {
        var pluginLoaderClassLoader: ClassLoader? = null

        fun loadPluginHooker(hooker: YukiBaseHooker) {
            hooker.appClassLoader = pluginLoaderClassLoader
            loadHooker(hooker)
        }

        fun initPluginHook() {
            if (mainPrefs.getBoolean("use_chen_volume_animation", false)) {
                loadPluginHooker(ChenVolumePanelAnimator)
            }
        }

        // Load plugin hooker
        "com.android.systemui.shared.plugins.PluginInstance\$Factory".toClass().method {
            name = "getClassLoader"
            param(ApplicationInfo::class.java, JavaClassLoader)
        }.hook {
            after {
                val applicationInfo = this.args[0] as ApplicationInfo
                if (applicationInfo.packageName == "miui.systemui.plugin") {
                    if (pluginLoaderClassLoader != this.result as ClassLoader) {
                        Log.i(
                            "Art_Chen",
                            "ClassLoader Changed! re-init hook for SystemUIPlugin"
                        )
                        pluginLoaderClassLoader = this.result as ClassLoader
                        initPluginHook()
                    }
                }
            }
        }

    }
}