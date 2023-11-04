package moe.chenxy.miuiextra.hooker.entity

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Parcel
import android.os.VibrationEffect
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.IBinderClass
import com.highcapable.yukihookapi.hook.type.defined.VagueType
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.FloatType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.LongType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.hooker.entity.SystemHooker.toClass
import moe.chenxy.miuiextra.hooker.entity.framework.VibratorMapHooker

object SystemHooker : YukiBaseHooker() {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    private val wallpaperZoomPrefs =
        XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_wallpaper_zoom_settings")

    override fun onHook() {
        // Vibrator Mapper
        loadHooker(VibratorMapHooker)

        // Hook for Wallpaper Scale Settings
        "com.android.server.wm.WallpaperController".toClass().method {
                name = "updateWallpaperOffset"
                param(VagueType, BooleanType)
        }.hook {
            before {
                wallpaperZoomPrefs.reload()
                if (wallpaperZoomPrefs.getInt("wallpaper_scale_val", -1) != -1) {
                    val scaleNew =
                        (wallpaperZoomPrefs.getInt("wallpaper_scale_val", 120).toFloat() / 100)
                    XposedHelpers.setFloatField(this.instance, "mMaxWallpaperScale", scaleNew)
                }
            }
        }

        // Hook for disable MiBridge FPS Change Interface
        "com.android.server.display.DisplayManagerServiceImpl".toClass().method {
                name = "appRequestChangeSceneRefreshRate"
                param(Parcel::class.java)
        }.hook {
            before {
                mainPrefs.reload()
                if (mainPrefs.getBoolean(
                        "force_disable_mibridge_dynamic_refresh_scene",
                        false
                    )
                ) {
                    val data = this.args[0] as Parcel
                    data.enforceInterface("android.view.android.hardware.display.IDisplayManager")
                    val callingUid = Binder.getCallingUid()
                    val targetPkgName = data.readString()
                    val maxRefreshRate = data.readInt()
                    if (callingUid < 10000) {
                        val token = Binder.clearCallingIdentity()
                        Log.i(
                            "Art_Chen",
                            "$targetPkgName want to apply RefreshRate to $maxRefreshRate, Deny!"
                        )
                        Binder.restoreCallingIdentity(token)
                    }
                    this.result = true
                }
            }
        }

        // ColorFade Animation Customize
        if (mainPrefs.getBoolean("color_fade_anim_smoothly", false)) {
            "com.android.server.display.DisplayPowerController".toClass().method {
                        name = "initialize"
                        param(IntType)
            }.hook {
                after {
                    mainPrefs.reload()
                    val mContext =
                        XposedHelpers.getObjectField(this.instance, "mContext") as Context
                    val mColorFadeOffAnimator = XposedHelpers.getObjectField(
                        this.instance,
                        "mColorFadeOffAnimator"
                    ) as ObjectAnimator
                    mColorFadeOffAnimator.duration =
                        mainPrefs.getInt("screen_off_color_fade_anim_val", 450).toLong()

                    val broadcastReceiver = object : BroadcastReceiver() {
                        override fun onReceive(p0: Context?, p1: Intent?) {
                            mainPrefs.reload()
                            mColorFadeOffAnimator.duration =
                                mainPrefs.getInt("screen_off_color_fade_anim_val", 450).toLong()
                        }

                    }
                    mContext.registerReceiver(
                        broadcastReceiver,
                        IntentFilter("chen.miui.extra.update.colorfade")
                    )
                }
            }
        }

        if (mainPrefs.getBoolean("do_not_override_pending_transition", false)) {
            "com.android.server.wm.ActivityClientController".toClass().method {
                name = "overridePendingTransition"
                param(IBinderClass, StringClass, IntType, IntType, IntType)
            }.hook {
                before {
                    Log.v("Art_Chen", "overridePendingTransition pkgName ${this.args[1]} enterAnim ${this.args[2]} exit ${this.args[3]}")
                    // Skip if exit Anim is not null.
                    if (this.args[2] != 0 && this.args[3] != 0) {
                        this.result = null
                    }
                }
            }
        }
    }
}