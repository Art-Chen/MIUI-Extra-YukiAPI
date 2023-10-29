package moe.chenxy.miuiextra.hooker.entity.systemui

import android.content.pm.ApplicationInfo
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.JavaClassLoader
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.utils.ChenUtils


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

            loadPluginHooker(StatusBarBlurUtilsHooker.ControlCenterWindowViewHooker)
        }

        // Load plugin hooker
        if (!ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)) {
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
        } else {
            // get Classloader for plugin on Android U
            "com.android.systemui.shared.plugins.PluginInstance".toClass().method {
                name = "loadPlugin"
            }.hook {
                after {
                    val pkgName = XposedHelpers.callMethod(this.instance, "getPackage")
                    if (pkgName == "miui.systemui.plugin") {
                        val factory = XposedHelpers.getObjectField(this.instance, "mPluginFactory")
                        val clsLoader = XposedHelpers.callMethod(XposedHelpers.getObjectField(factory, "mClassLoaderFactory"), "get") as ClassLoader
                        if (pluginLoaderClassLoader != clsLoader) {
                            Log.i(
                                "Art_Chen",
                                "[loadPlugin] initPluginHook"
                            )
                            pluginLoaderClassLoader = clsLoader
                            initPluginHook()
                        }
                    }
                }
            }
        }

    }
}