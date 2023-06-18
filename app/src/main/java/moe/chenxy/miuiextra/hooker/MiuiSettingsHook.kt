package moe.chenxy.miuiextra.hooker

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MiuiSettingsHook {
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        var isChenMode = false
        lateinit var mContext: Context
        val mPowerKeeperHooker = PowerKeeperHook()

        XposedHelpers.findAndHookConstructor(
            "com.xiaomi.misettings.display.RefreshRate.l",
            lpparam!!.classLoader,
            Context::class.java,
            object : XC_MethodHook() {
                @SuppressLint("PrivateApi")
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    mContext = param.args[0] as Context
                    isChenMode = Settings.System.getString(mContext.contentResolver, "custom_mode_is_chen_policy")  == "true"
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "com.xiaomi.misettings.display.RefreshRate.l",
            lpparam!!.classLoader,
            "a",
//            "updateBlackTable",
            String::class.java,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                @SuppressLint("PrivateApi")
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isChenMode) {
                        Log.v("Art_Chen", "[MiSettings][CustomMode] It's Chen Policy Mode, Do nothing for DB")
                        param.result = 0
                    }
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "com.xiaomi.misettings.display.RefreshRate.l",
            lpparam!!.classLoader,
            "a",
            object : XC_MethodHook() {
                @SuppressLint("PrivateApi")
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isChenMode) {
                        Log.i("Art_Chen", "It's Chen Policy Mode, Let's query chen's List!")
                        val listStr = Settings.Global.getString(mContext.contentResolver, mPowerKeeperHooker.SETTINGS_CUSTOM_ARRAY_KEY)
                        val listArr = PowerKeeperHook().listStrToArr(listStr) as List<String>
                        param.result = listArr
                    }
                }
            }
        )

    }
}