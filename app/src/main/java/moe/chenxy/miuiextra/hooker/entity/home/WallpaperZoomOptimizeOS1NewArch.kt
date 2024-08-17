package moe.chenxy.miuiextra.hooker.entity.home

import android.annotation.SuppressLint
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.hooker.entity.MiuiHomeHook


object WallpaperZoomOptimizeOS1NewArch : YukiBaseHooker() {
    private lateinit var mZoomInSpringForce: Any
    private lateinit var mZoomOutSpringForce: Any
    private lateinit var mSpringAnimation: Any

    private var mZoomOut = MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_val", 1).toFloat() / 10
    private var zoomOutStartVelocity =
        MiuiHomeHook.zoomPrefs.getInt(
            "wallpaper_zoomOut_start_velocity_val",
            662
        ).toFloat() / 1000
    private var zoomInStartVelocity = MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_start_velocity_val", 0).toFloat() / 1000
    private var accuracyLevel = MiuiHomeHook.zoomPrefs.getInt("wallpaper_accuracy_val", 3)
    private val minChange: Float
        get() {
            return when (accuracyLevel) {
                1 -> 0.1f
                2 -> 0.05f
                3 -> 0.01f
                4 -> 0.005f
                5 -> 0.001f
                6 -> 0.0005f
                7 -> 0.0001f
                8 -> 0.00005f
                9 -> 0.00001f
                else -> 0.1f
            }
        }

    private fun updateSettings(thiz: Any, isZoomOut: Boolean) {
        var firstInit = false
        if (!this::mZoomInSpringForce.isInitialized) {
            mZoomInSpringForce = XposedHelpers.getObjectField(thiz, "mZoomInSpringForce")
            mZoomOutSpringForce = XposedHelpers.getObjectField(thiz, "mZoomOutSpringForce")
            mSpringAnimation = XposedHelpers.getObjectField(thiz, "mSpringAnimation")
            firstInit = true
        }

        if (MiuiHomeHook.zoomPrefs.hasFileChanged() || firstInit) {
            MiuiHomeHook.zoomPrefs.reload()
            val zoomInStiffness =
                MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_stiffness_val", 3263)
                    .toFloat() / 100
            val zoomOutStiffness =
                MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_stiffness_val", 3263)
                    .toFloat() / 100

            mZoomOut =
                MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_val", 1).toFloat() / 10
            zoomInStartVelocity =
                MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_start_velocity_val", 0).toFloat() / 1000
            accuracyLevel = MiuiHomeHook.zoomPrefs.getInt("wallpaper_accuracy_val", 2)

            val zoomInDampingRatio = MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomIn_damping_ratio_val", 100).toFloat() / 100
            val zoomOutDampingRatio = MiuiHomeHook.zoomPrefs.getInt("wallpaper_zoomOut_damping_ratio_val", 100).toFloat() / 100

            XposedHelpers.callMethod(mZoomInSpringForce, "setStiffness", zoomInStiffness)
            XposedHelpers.callMethod(mZoomInSpringForce, "setDampingRatio", zoomInDampingRatio)
            XposedHelpers.callMethod(mZoomOutSpringForce, "setStiffness", zoomOutStiffness)
            XposedHelpers.callMethod(mZoomOutSpringForce, "setDampingRatio", zoomOutDampingRatio)
//            XposedHelpers.callMethod(mZoomOutSpringForce, "setFinalPosition", mZoomOut)

            XposedHelpers.callMethod(mSpringAnimation, "setMinimumVisibleChange", minChange)
        }

        if (isZoomOut)
            XposedHelpers.callMethod(mSpringAnimation, "setStartVelocity", zoomOutStartVelocity)
        else
            XposedHelpers.callMethod(mSpringAnimation, "setStartVelocity", zoomInStartVelocity)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onHook() {
        var mWallpaperElement: Any? = null
        var currentZoom = 0f
        "com.miui.home.recents.anim.WallpaperElement".toClass().method {
            name = "animTo"
            paramCount = 1
        }.hook {
            before {
                mWallpaperElement = this.instance
                val param = this.args[0] ?: return@before

                currentZoom = XposedHelpers.callMethod(param, "getZoomOut") as Float

                updateSettings(this.instance, currentZoom == 1.0f)
            }
        }

        "com.miui.home.recents.anim.WallpaperParam".toClass().method {
            name = "getZoomOut"
        }.hook {
            after {
                if (this.result == 0.6f) this.result = mZoomOut
            }
        }

        "com.miui.home.launcher.common.UnlockAnimationStateMachine".toClass().apply {
            method {
                name = "onScreenOff"
            }.hook {
                after {
                    // reset
                    mWallpaperElement?.apply {
                        if (currentZoom < 1f) {
                            XposedHelpers.callMethod(this, "updateElementProperty", 1f)
                        }
                    }

                }
            }
        }
    }
}