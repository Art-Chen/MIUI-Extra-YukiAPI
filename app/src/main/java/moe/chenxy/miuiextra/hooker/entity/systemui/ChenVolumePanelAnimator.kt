package moe.chenxy.miuiextra.hooker.entity.systemui

import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import android.widget.LinearLayout
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.java.IntType
import de.robv.android.xposed.XposedHelpers

object ChenVolumePanelAnimator : YukiBaseHooker() {
    override fun onHook() {
        var mDialogView: LinearLayout? = null
        val mDialogAnimator = ValueAnimator()
        mDialogAnimator.addUpdateListener {
            mDialogView?.translationY = it.animatedValue as Float
        }
        mDialogAnimator.duration = 800
        mDialogAnimator.interpolator = PathInterpolator(0.39f, 1.48f, 0.44f, 1.07f)
        fun animateVolumeView(isTop: Boolean) {
            if (mDialogAnimator.isRunning)
                mDialogAnimator.cancel()

            mDialogAnimator.setFloatValues(
                mDialogView!!.translationY,
                if (isTop) -25f else 25f,
                0f
            )
            mDialogAnimator.start()
        }

        var lastIsTopHaptic = false
        var lastIsBottomHaptic = false
        var lastIsContinueHaptic = false
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
    }
}