package moe.chenxy.miuiextra

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import java.io.DataOutputStream

class ChenUtils {
    companion object {
        fun performVibrateClick(context: Context) {
            val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }

        fun performVibrateHeavyClick(context: Context) {
            val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }

        fun performVibrateAnyIndex(context: Context, id: Int) {
            val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibrator.defaultVibrator.vibrate(VibrationEffect.createPredefined(id))
        }

        fun lerp(start: Float, stop: Float, amount: Float): Float {
            return start + (stop - start) * amount
        }

        fun execShell(command: String) {
            try {
                val p = Runtime.getRuntime().exec("su")
                val outputStream = p.outputStream
                val dataOutputStream = DataOutputStream(outputStream)
                dataOutputStream.writeBytes(command)
                dataOutputStream.flush()
                dataOutputStream.close()
                outputStream.close()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }
}