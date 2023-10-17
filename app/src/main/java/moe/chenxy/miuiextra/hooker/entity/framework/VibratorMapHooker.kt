package moe.chenxy.miuiextra.hooker.entity.framework

import android.os.VibrationEffect
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.java.FloatType
import com.highcapable.yukihookapi.hook.type.java.LongType
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.hooker.entity.SystemHooker

object VibratorMapHooker : YukiBaseHooker(){
    private val vibratorPrefs =
        XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_vibrator_settings")
    private val vibratorEffectPrefs =
        XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_vibrator_effect_settings")
    private var mLastPerformEffect: Long = 0
    private var mEffectMap = HashMap<Int, Int>()
    private var mFirstInit = true

    @Volatile
    var needRemap = false
    
    override fun onHook() {
        "com.android.server.vibrator.VibratorController".hook {
            injectMember {
                // Hook for Normal Vibrator Remap
                method {
                    name = "on"
                    param(LongType, LongType)
                }
                beforeHook {
                    val millis = this.args[0] as Long
                    val vibrationId = this.args[1] as Long
                    vibratorPrefs.reload()
                    needRemap =
                        vibratorPrefs.getBoolean("enable_vibrator_remap", false) && millis < 500

                    if (needRemap) {
                        val id = mapMillisRangeToID(millis)
                        if (id != -1L) {
                            vibratorPerformEffect(this.instance, id, 2, vibrationId)
                            Log.i("Art_Chen", "vibratorOn Remap, millis is $millis, mappedId: $id")
                            this.result = 0L
                        }
                    }
                }
            }

            // Remap Prebaked Vibrate
            injectMember {
                method {
                    name = "on"
                    param("android.os.vibrator.PrebakedSegment", LongType)
                }
                beforeHook {
                    initEffectMap()
                    if (vibratorEffectPrefs.getBoolean("enable_vibrator_effect_remap", false)) {
                        val mPrebakedSegment = this.args[0]
//                        val vibrationId = this.args[1] as Long
                        val effectId =
                            XposedHelpers.callMethod(mPrebakedSegment, "getEffectId") as Int

                        if (mEffectMap.containsKey(effectId)) {
                            Log.i(
                                "Art_Chen",
                                "[Effect Vibrate Mapper] mapped $effectId to ${mEffectMap[effectId]}"
                            )
                            XposedHelpers.setObjectField(
                                mPrebakedSegment,
                                "mEffectId",
                                mEffectMap[effectId]
                            )
                            this.args[0] = mPrebakedSegment
                        }
                    }
                }
            }

            injectMember {
                method {
                    name = "setAmplitude"
                    param(FloatType)
                }
                beforeHook {
                    if (needRemap) {
                        Log.i(
                            "Art_Chen",
                            "should not set amplitude if we need map to effect vibrator, ignored this set"
                        )
                        this.result = null
                    }
                }
            }
        }

        "com.android.server.vibrator.SetAmplitudeVibratorStep".hook {
            injectMember {
                method {
                    name = "startVibrating"
                    param(LongType)
                }
                beforeHook {
                    val millis = this.args[0] as Long
                    vibratorPrefs.reload()
                    needRemap =
                        vibratorPrefs.getBoolean("enable_vibrator_remap", false) && millis < 500
                }
            }
        }
    }


    private fun mapMillisRangeToID(millis: Long): Long {
        if (millis in 1..10) return vibratorPrefs.getString("map_range_1_10", "5")!!.toLong()
        if (millis in 11..20) return vibratorPrefs.getString("map_range_11_20", "7")!!.toLong()
        if (millis in 21..40) return vibratorPrefs.getString("map_range_21_40", "8")!!.toLong()
        if (millis in 41..70) return vibratorPrefs.getString("map_range_41_70", "3")!!.toLong()
        if (millis in 71..500) return vibratorPrefs.getString("map_range_71_500", "1")!!.toLong()

        // Default to VibrationEffect.CLICK
        return VibrationEffect.EFFECT_CLICK.toLong()
    }

    private fun initEffectMap() {
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

    private fun vibratorPerformEffect(
        thiz: Any,
        effectId: Long,
        effectStrength: Long,
        vibrationId: Long
    ) {
        val fastLimit = if (effectId > 5) 40 else 30
        if (System.currentTimeMillis() - mLastPerformEffect < fastLimit) {
            Log.v("Art_Chen", "Too fast for Vibrator Effect!! drop it!")
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
}