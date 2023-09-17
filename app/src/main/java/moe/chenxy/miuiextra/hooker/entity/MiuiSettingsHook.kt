package moe.chenxy.miuiextra.hooker.entity

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.StringClass

object MiuiSettingsHook : YukiBaseHooker() {
    override fun onHook() {
        var isChenMode = false
        lateinit var mContext: Context

        "com.xiaomi.misettings.display.RefreshRate.l".hook {
            injectMember {
                constructor {
                    param(ContextClass)
                }
                afterHook {
                    mContext = this.args[0] as Context
                    isChenMode = Settings.System.getString(
                        mContext.contentResolver,
                        "custom_mode_is_chen_policy"
                    ) == "true"
                }
            }

            injectMember {
                method {
                    // Proguarded method name
                    name = "a"
                    param(StringClass, BooleanType)
                }
                beforeHook {
                    if (isChenMode) {
                        Log.v(
                            "Art_Chen",
                            "[MiSettings][CustomMode] It's Chen Policy Mode, Do nothing for DB"
                        )
                        this.result = null
                    }
                }
            }

            injectMember {
                method {
                    name = "a"
                }
                beforeHook {
                    if (isChenMode) {
                        Log.i("Art_Chen", "It's Chen Policy Mode, Let's query chen's List!")
                        val listStr = Settings.Global.getString(
                            mContext.contentResolver,
                            PowerKeeperHook.SETTINGS_CUSTOM_ARRAY_KEY
                        )
                        val listArr = PowerKeeperHook.listStrToArr(listStr) as List<String>
                        this.result = listArr
                    }
                }
            }
        }

    }
}