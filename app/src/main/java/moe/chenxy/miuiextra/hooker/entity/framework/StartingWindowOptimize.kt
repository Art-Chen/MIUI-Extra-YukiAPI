package moe.chenxy.miuiextra.hooker.entity.framework

import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.method

object StartingWindowOptimize :YukiBaseHooker() {
    override fun onHook() {
        "com.android.server.wm.ActivityRecord".toClass().method {
            name = "getStartingWindowType"
            paramCount = 7
        }.hook {
            after {
                Log.i("Art_Chen", "Starting Window Type ${this.result}")
            }
        }
    }
}