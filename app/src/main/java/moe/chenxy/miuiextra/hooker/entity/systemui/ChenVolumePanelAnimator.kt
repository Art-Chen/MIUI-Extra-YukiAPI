package moe.chenxy.miuiextra.hooker.entity.systemui

import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.IntType
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig
import kotlin.math.absoluteValue


object ChenVolumePanelAnimator : YukiBaseHooker() {
    private const val SHIFT_ONLY = 0
    private const val STRETCH = 1
    private const val SCALE = 2

    private var MAX_SHIFT_VAL = 25f

    override fun onHook() {
        val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
        mainPrefs.reload()
        var effectMode = mainPrefs.getString("chen_volume_animation_effect", "0")?.toInt()
        var mDialogView: LinearLayout? = null
        val mDialogAnimator = ValueAnimator()
        mDialogAnimator.addUpdateListener {
            val currentVal = it.animatedValue as Float
            when (effectMode) {
                STRETCH -> {
                    mDialogView?.scaleX = 1 - (currentVal.absoluteValue / (MAX_SHIFT_VAL * 8))
                    mDialogView?.scaleY = 1 + (currentVal.absoluteValue / (MAX_SHIFT_VAL * 20))
                }
                SCALE -> {
                    mDialogView?.scaleX = 1 - (currentVal / (MAX_SHIFT_VAL * 15))
                    mDialogView?.scaleY = 1 - (currentVal / (MAX_SHIFT_VAL * 15))
                }
            }
            mDialogView?.translationY = currentVal
        }

        mDialogAnimator.duration = 800
        mDialogAnimator.interpolator = PathInterpolator(0.39f, 1.48f, 0.44f, 1.07f)
        fun animateVolumeView(isTop: Boolean) {
            if (mDialogAnimator.isRunning)
                mDialogAnimator.cancel()

            MAX_SHIFT_VAL = when (effectMode) {
                STRETCH -> 50f
                else -> 25f
            }

            mDialogAnimator.setFloatValues(
                mDialogView!!.translationY,
                if (isTop) -MAX_SHIFT_VAL else MAX_SHIFT_VAL,
                0f
            )
            mainPrefs.reload()
            effectMode = mainPrefs.getString("chen_volume_animation_effect", "0")?.toInt()
            mDialogAnimator.start()
        }

        var lastIsTopHaptic = false
        var lastIsBottomHaptic = false
        var lastIsContinueHaptic = false
        var isDismissed = true
        "com.android.systemui.miui.volume.MiuiVolumeDialogImpl$1".toClass().method {
            name = "onPerformHapticFeedback"
            param(IntType)
        }.hook {
            before {
                val i = this.args[0] as Int
                val thiz = XposedHelpers.getObjectField(this.instance, "this\$0")
                mDialogView =
                    XposedHelpers.getObjectField(thiz, "mDialogView") as LinearLayout
                val isContinueHaptic: Boolean = i and 1 > 0
                val isTopHaptic: Boolean = i and 2 > 0
                val isBottomHaptic: Boolean = i and 4 > 0
                if (isDismissed) {
                    isDismissed = false
                    return@before
                }
                // Log.i("Art_Chen", "onPerformHapticFeedback isContinueHaptic:$isContinueHaptic, isTopHaptic: $isTopHaptic, isBottomHaptic: $isBottomHaptic")
                if ((isTopHaptic || isBottomHaptic) && (lastIsBottomHaptic != isBottomHaptic || lastIsTopHaptic != isTopHaptic || lastIsContinueHaptic != isContinueHaptic)) {
                    animateVolumeView(isTopHaptic && !isBottomHaptic)
                }
                if ((isTopHaptic || isBottomHaptic) && !isContinueHaptic) {
                    animateVolumeView(isTopHaptic && !isBottomHaptic)
                }
                lastIsTopHaptic = isTopHaptic
                lastIsBottomHaptic = isBottomHaptic
            }
        }

        "com.android.systemui.miui.volume.MiuiVolumeDialogImpl$6".toClass().method {
            name = "onDismiss"
        }.hook {
            after {
                isDismissed = true
            }
        }

    }
}