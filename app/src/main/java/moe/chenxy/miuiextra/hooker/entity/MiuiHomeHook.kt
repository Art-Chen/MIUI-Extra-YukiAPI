package moe.chenxy.miuiextra.hooker.entity

import android.view.View
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ViewClass
import com.highcapable.yukihookapi.hook.type.java.FloatType
import com.highcapable.yukihookapi.hook.type.java.IntType
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.hooker.entity.home.AnimationEnhanceHooker
import moe.chenxy.miuiextra.hooker.entity.home.IconLaunchAnimHooker
import moe.chenxy.miuiextra.hooker.entity.home.WallpaperZoomOptimizeHooker
import moe.chenxy.miuiextra.utils.ChenUtils


object MiuiHomeHook : YukiBaseHooker() {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    val zoomPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_wallpaper_zoom_settings")

    enum class AnimType {
        BREAK_OPEN, OPEN_FROM_HOME, OPEN_FROM_RECENTS, CLOSE_TO_RECENTS, CLOSE_TO_HOME, CLOSE_FROM_FEED, APP_TO_APP, START_FIRST_TASK
    }


    override fun onHook() {
        mainPrefs.reload()
        if (mainPrefs.getBoolean("miui_home_anim_enhance", false)) {
            // MiuiHome is no needed hack this on U
            if (!ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)) {
                loadHooker(IconLaunchAnimHooker)
            } else {
                // Above U use new Enhancer
                loadHooker(AnimationEnhanceHooker)
            }
        }


        if (zoomPrefs.getBoolean("enable_wallpaper_zoom_optimize", false)) {
            loadHooker(WallpaperZoomOptimizeHooker)
        }


        "com.miui.home.launcher.compat.UserPresentAnimationCompatV12Phone".toClass().apply {
            method {
                name = "getSpringAnimator"
                param(ViewClass, IntType, FloatType, FloatType, FloatType, FloatType)
            }.hook {
                after {
                    mainPrefs.reload()
                    val view = this.args[0] as View
                    val id = this.args[1] as Int
                    val mode = mainPrefs.getString("miui_unlock_anim_enhance_menu", "0")?.toInt()
                    if (mode == 0) return@after

                    val springAnimation = this.result
                    if (this.args[2] == -1500.0f) {
                        var dumping = this.args[4]
                        var response = this.args[5]
                        when (mode) {
                            1 -> {
                                dumping = 0.68f
                                response = 0.6f
                            }
                            2 -> {
                                dumping = 1.5f
                                response = 0.3f
                            }
                        }
                        XposedHelpers.callMethod(
                            springAnimation,
                            "setDampingResponse",
                            dumping,
                            response
                        )
                    }
                    view.setTag(id, springAnimation)
                    this.result = springAnimation
                }
            }
        }


    }
}