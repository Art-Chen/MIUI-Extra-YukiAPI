package moe.chenxy.miuiextra

import android.R.attr.classLoader
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.VibrationEffect
import android.util.Log
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage


class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit /* Optional */ {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    val vibratorPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_vibrator_settings")
    val vibratorEffectPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_vibrator_effect_settings")
    val wallpaperZoomPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_wallpaper_zoom_settings")
    private var mLastPerformEffect: Long = 0
    var mEffectMap = HashMap<Int, Int>()
    var mFirstInit = true

    companion object {
        @Volatile
        var needRemap = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        mainPrefs.reload()
        vibratorEffectPrefs.reload()

        if (lpparam?.packageName == "android") {

            // Hook for Vibrator Remap
            XposedHelpers.findAndHookMethod(
                "com.android.server.vibrator.VibratorController",
                lpparam.classLoader,
                "on",
                Long::class.java,
                Long::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val millis = param.args[0] as Long
                        val vibrationId = param.args[1] as Long
                        vibratorPrefs.reload()
                        needRemap = vibratorPrefs.getBoolean("enable_vibrator_remap", false) && millis < 500

                        if (needRemap) {
                            val id = mapMillisRangeToID(millis)
                            if (id != -1L) {
                                vibratorPerformEffect(param.thisObject, id, 2, vibrationId)
                                Log.i("Art_Chen", "vibratorOn Remap, millis is $millis, mappedId: $id")
                                param.result = 0L
                            }
                        }
                    }
                })

            XposedHelpers.findAndHookMethod(
                "com.android.server.vibrator.SetAmplitudeVibratorStep",
                lpparam.classLoader,
                "startVibrating",
                Long::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val millis = param.args[0] as Long
                        vibratorPrefs.reload()
                        needRemap = vibratorPrefs.getBoolean("enable_vibrator_remap", false) && millis < 500
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                "com.android.server.vibrator.VibratorController",
                lpparam.classLoader,
                "setAmplitude",
                Float::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (needRemap) {
                            Log.i("Art_Chen", "should not set amplitude if we need map to effect vibrator, ignored this set")
                            param.result = 0
                        }
                    }
                })

            // Hook for Prebaked Remap
            XposedHelpers.findAndHookMethod(
                "com.android.server.vibrator.VibratorController",
                lpparam.classLoader,
                "on",
                "android.os.vibrator.PrebakedSegment",
                Long::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        initEffectMap()
                        if (vibratorEffectPrefs.getBoolean("enable_vibrator_effect_remap", false)) {
                            val mPrebakedSegment = param.args[0]
                            val vibrationId = param.args[1] as Long
                            val effectId =
                                XposedHelpers.callMethod(mPrebakedSegment, "getEffectId") as Int

                            if (mEffectMap.containsKey(effectId)) {
                                Log.i("Art_Chen", "[Effect Vibrate Mapper] mapped $effectId to ${mEffectMap[effectId]}")
                                XposedHelpers.setObjectField(
                                    mPrebakedSegment,
                                    "mEffectId",
                                    mEffectMap[effectId]
                                )
                                param.args[0] = mPrebakedSegment
                            }
                        }
                    }
                })

            // Hook for Wallpaper Scale Settings
            XposedHelpers.findAndHookMethod(
                "com.android.server.wm.WallpaperController",
                lpparam.classLoader,
                "updateWallpaperOffset",
                "com.android.server.wm.WindowState",
                Boolean::class.java,
                object : XC_MethodHook() {
                    @SuppressLint("PrivateApi")
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        wallpaperZoomPrefs.reload()
                        if (wallpaperZoomPrefs.getInt("wallpaper_scale_val", -1) != -1) {
                            val scaleNew = (wallpaperZoomPrefs.getInt("wallpaper_scale_val", 120).toFloat() / 100)
                            XposedHelpers.setFloatField(param.thisObject, "mMaxWallpaperScale", scaleNew)
                        }
                    }
                }
            )

            try {
                // Refresh Rate Settings
                XposedHelpers.findAndHookMethod(
                    "com.android.server.display.DisplayManagerServiceImpl",
                    lpparam.classLoader,
                    "appRequestChangeSceneRefreshRate",
                    Parcel::class.java,
                    object : XC_MethodHook() {
                        @SuppressLint("PrivateApi")
                        @Throws(Throwable::class)
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            mainPrefs.reload()
                            if (mainPrefs.getBoolean("force_disable_mibridge_dynamic_refresh_scene", false)) {
                                val data = param.args[0] as Parcel
                                data.enforceInterface("android.view.android.hardware.display.IDisplayManager")
                                val callingUid = Binder.getCallingUid();
                                val targetPkgName = data.readString();
                                val maxRefreshRate = data.readInt();
                                if (callingUid < 10000) {
                                    val token = Binder.clearCallingIdentity()
                                    Log.i(
                                        "Art_Chen",
                                        "$targetPkgName want to apply RefreshRate to $maxRefreshRate, Deny!"
                                    )
                                    Binder.restoreCallingIdentity(token)
                                }
                                param.result = true
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            // Smooth ColorFade Animation
            if (mainPrefs.getBoolean("color_fade_anim_smoothly", false)) {
                XposedHelpers.findAndHookMethod(
                    "com.android.server.display.DisplayPowerController",
                    lpparam.classLoader,
                    "initialize",
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        @SuppressLint("PrivateApi")
                        @Throws(Throwable::class)
                        override fun afterHookedMethod(param: MethodHookParam) {
                            mainPrefs.reload()
                            val mContext = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context
//                            val mColorFadeOnAnimator = XposedHelpers.getObjectField(
//                                param.thisObject,
//                                "mColorFadeOnAnimator"
//                            ) as ObjectAnimator
                            val mColorFadeOffAnimator = XposedHelpers.getObjectField(
                                param.thisObject,
                                "mColorFadeOffAnimator"
                            ) as ObjectAnimator
//                            mColorFadeOnAnimator.duration = mainPrefs.getInt("screen_on_color_fade_anim_val", 350).toLong()
                            mColorFadeOffAnimator.duration = mainPrefs.getInt("screen_off_color_fade_anim_val", 450).toLong()

                            val broadcastReceiver = object : BroadcastReceiver() {
                                override fun onReceive(p0: Context?, p1: Intent?) {
                                    mainPrefs.reload()
//                                    mColorFadeOnAnimator.duration = mainPrefs.getInt("screen_on_color_fade_anim_val", 350).toLong()
                                    mColorFadeOffAnimator.duration = mainPrefs.getInt("screen_off_color_fade_anim_val", 450).toLong()
                                }

                            }
                            mContext.registerReceiver(broadcastReceiver, IntentFilter("chen.miui.extra.update.colorfade"))

                        }
                    }
                )
            }
            if (mainPrefs.getBoolean("do_not_override_pending_transition", false)) {
                XposedHelpers.findAndHookMethod("com.android.server.wm.ActivityClientController",
                    lpparam.classLoader,
                    "overridePendingTransition",
                    IBinder::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    object : XC_MethodHook() {
                        @Throws(Throwable::class)
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            param.result = 0
                        }
                    })
            }
        } else if (lpparam?.packageName == "com.android.systemui") {
            if (mainPrefs.getBoolean("chen_home_handle_anim", false)) {
                SystemUINavigationBarHook().handleLoadPackage(lpparam)
            }
            try {
                SystemUIMusicNotificationHook().handleLoadPackage(lpparam)
            } catch (e: Throwable) {
                Log.i("Art_Chen", "This Version didn't support the Music Notification Hook, ignored!")
                // do nothing
            }
            SystemUIPluginHook().handleLoadPackage(lpparam)
        } else if (lpparam?.packageName == "com.miui.powerkeeper") {
            PowerKeeperHook().handleLoadPackage(lpparam)
        } else if (lpparam?.packageName == "com.miui.home") {
            MiuiHomeHook().handleLoadPackage(lpparam)
        } else if (lpparam?.packageName == "com.xiaomi.misettings") {
            MiuiSettingsHook().handleLoadPackage(lpparam)
        } else if (lpparam?.packageName == "com.miui.miwallpaper") {
            MiWallpaperHook().handleLoadPackage(lpparam)
        }
    }



    fun mapMillisRangeToID(millis: Long): Long {
        if (millis in 1..10) return vibratorPrefs.getString("map_range_1_10", "5")!!.toLong()
        if (millis in 11..20) return vibratorPrefs.getString("map_range_11_20", "7")!!.toLong()
        if (millis in 21..40) return vibratorPrefs.getString("map_range_21_40", "8")!!.toLong()
        if (millis in 41..70) return vibratorPrefs.getString("map_range_41_70", "3")!!.toLong()
        if (millis in 71..500) return vibratorPrefs.getString("map_range_71_500", "1")!!.toLong()

        // Default to VibrationEffect.CLICK
        return VibrationEffect.EFFECT_CLICK.toLong()
    }

    fun initEffectMap() {
        if (vibratorEffectPrefs.hasFileChanged() || mFirstInit) {
            vibratorEffectPrefs.reload()
            mEffectMap.clear()
            val set = vibratorEffectPrefs.getStringSet("chen_vibrator_effect_map_list", null)
            if (set != null) {
                for (str in set) {
                    val arr = str.split(",")
                    mEffectMap[arr[0].toInt()] = arr[1].toInt()
                }
            }
            mFirstInit = false
        }
    }

    fun vibratorPerformEffect(thiz: Any, effectId: Long, effectStrength: Long, vibrationId: Long) {
        val fastLimit = if (effectId > 5) 40 else 30
        if (System.currentTimeMillis() - mLastPerformEffect < fastLimit) {
            Log.e("Art_Chen", "Too fast for Vibrator Effect!! drop it!")
            return
        }
        val nativeWrapper: Any = XposedHelpers.getObjectField(
            thiz,
            "mNativeWrapper"
        )
        XposedHelpers.callMethod(
            nativeWrapper, "perform", arrayOf<Class<*>?>(
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            ), effectId, effectStrength, vibrationId
        )
        mLastPerformEffect = System.currentTimeMillis()
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
//        Log.i("Art_Chen", "initZygote")
    }
}