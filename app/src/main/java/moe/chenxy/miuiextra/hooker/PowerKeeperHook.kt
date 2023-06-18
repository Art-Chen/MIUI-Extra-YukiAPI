package moe.chenxy.miuiextra.hooker

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.util.ArrayMap
import android.util.ArraySet
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import moe.chenxy.miuiextra.BuildConfig

class PowerKeeperHook() {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    private val COOKIE_VIDEO = 248
    private val COOKIE_TOP_APP = 249
    val SETTINGS_CUSTOM_ARRAY_KEY = "chen_custom_refresh_policy_blacklist"
    private val mCustomTopApps = ArrayMap<String, Int>()
    private var mThiz: Any? = null
    var mContext: Context? = null

    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val isOldVersion = getIsNonSupportedVersion(lpparam)
        mainPrefs.reload()

        XposedHelpers.findAndHookConstructor(
            "com.miui.powerkeeper.statemachine.DisplayFrameSetting",
            lpparam!!.classLoader,
            Context::class.java,
            Looper::class.java,
            object : XC_MethodHook() {
                @SuppressLint("PrivateApi")
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    mainPrefs.reload()
                    mContext = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context

                    if (isOldVersion) {
                        Log.e("Art_Chen", "[PowerKeeper] Old Version PowerKeeper detected!!")
                        mThiz = param.thisObject
                        if (mainPrefs.getBoolean("force_enable_refresh_custom", false)) {
                            Log.d("Art_Chen", "[PowerKeeper][CustomPolicy] Force Enabled refresh rate policy!")
                            val mAppliedChenPolicy =
                                Settings.System.getString(mContext!!.contentResolver, "custom_mode_is_chen_policy") == "true"
                            if (!mAppliedChenPolicy) {
                                initCustomBlackList(mContext!!)

                                // Tell MiSetting that we supported CustomMode
                                Settings.System.putString(mContext!!.contentResolver, "custom_mode_switch", "true")
                                Settings.System.putString(mContext!!.contentResolver, "custom_mode_is_chen_policy", "true")
                                Settings.Secure.putString(mContext!!.contentResolver, "custom_mode_switch", "true")
                            }

                            setAllCustomAppToTopApp(mContext!!)
                            Log.d("Art_Chen", "[PowerKeeper][CustomPolicy] Now TopApp Replaced to Custom App List")

                            // Watch RefreshRate List Changed
                            val contentObserver = object : ContentObserver(param.thisObject as Handler?) {
                                override fun onChange(selfChange: Boolean, uri: Uri?) {
                                    Log.d("Art_Chen", "[PowerKeeper][CustomPolicy] list changed, let's update it!")
                                    getNewTopAppsList(mContext!!)
                                }
                            }
                            mContext!!.contentResolver.registerContentObserver(
                                Settings.System.getUriFor("custom_mode"),
                                false,
                                contentObserver
                            )
                        } else {
                            // Restore all bool to unsupported
                            Settings.System.putString(mContext!!.contentResolver, "custom_mode_switch", "false")
                            Settings.System.putString(mContext!!.contentResolver, "custom_mode_is_chen_policy", "false")
                            Settings.Secure.putString(mContext!!.contentResolver, "custom_mode_switch", "false")
                        }
                    } else {
                        Log.i("Art_Chen", "[PowerKeeper][CustomRefreshRatePolicy] Native Supported Version PowerKeeper detected!!")
                        val mAppliedChenPolicy =
                            Settings.System.getString(mContext!!.contentResolver, "custom_mode_is_chen_policy") == "true"
                        if (mAppliedChenPolicy) {
                            Log.i("Art_Chen", "cleanup chen policy to use official policy.")
                            Settings.System.putString(mContext!!.contentResolver, "custom_mode_is_chen_policy", "false")
                            Settings.Global.putString(mContext!!.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY, "")
                        }
                    }
                }
            }
        )

        if (!isOldVersion) {
            // Custom FPS Settings
            XposedHelpers.findAndHookMethod(
                "com.miui.powerkeeper.statemachine.DisplayFrameSetting",
                lpparam!!.classLoader,
                "parseCustomModeSwitchFromDb",
                String::class.java,
                object : XC_MethodHook() {
                    @SuppressLint("PrivateApi")
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val mIsCustomFpsSwitch =
                            XposedHelpers.getObjectField(param.thisObject, "mIsCustomFpsSwitch")
                        val mIsNativeSupported =
                            mIsCustomFpsSwitch != null && (mIsCustomFpsSwitch as String) == "true"
                        mainPrefs.reload()

                        if (mainPrefs.getBoolean(
                                "force_enable_refresh_custom",
                                false
                            ) && !mIsNativeSupported
                        ) {
                            val mContext = XposedHelpers.getObjectField(
                                param.thisObject,
                                "mContext"
                            ) as Context
                            Settings.System.putString(
                                mContext.contentResolver,
                                "custom_mode_switch",
                                "true"
                            );
                            XposedHelpers.setObjectField(
                                param.thisObject,
                                "mIsCustomFpsSwitch",
                                "true"
                            )
                        }
                    }
                }
            )
        }

        // Disable Miui Refresh Rate Policy
        XposedHelpers.findAndHookMethod(
            "com.miui.powerkeeper.statemachine.DisplayFrameSetting",
            lpparam.classLoader,
            "setScreenEffect",
            String::class.java,
            Int::class.java,
            Int::class.java,
            object : XC_MethodHook() {
                @SuppressLint("PrivateApi")
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cookie = param.args[2] as Int
                    val fps = param.args[1] as Int
                    mainPrefs.reload()

                    if (mContext == null) {
                        mContext = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context
                    }

                    if (mainPrefs.getBoolean("force_current_refresh_rate", false)) {
                        val mUserFPS = XposedHelpers.getIntField(param.thisObject, "mUserFps")

                        setCurrentUserFpsAsFinal(mContext!!, param.thisObject)
                        param.args[1] = mUserFPS
                        param.result = 0
                    }

                    if (isOldVersion && mainPrefs.getBoolean("force_enable_refresh_custom", false)) {
                        if (cookie == COOKIE_VIDEO && !checkAppInBlackList(mContext!!, param.args[0] as String)) {
                            Log.i(
                                "Art_Chen",
                                "[PowerKeeper][CustomPolicy] Ignored non 60 fps Video App fps setting action!"
                            )
                            param.result = 0
                        }

                        if (!checkAppInBlackList(mContext!!, param.args[0] as String) && fps == 60) {
                            val mUserFPS = XposedHelpers.getIntField(param.thisObject, "mUserFps")
                            Log.i("Art_Chen", "[PowerKeeper][CustomPolicy] App ${param.args[0] as String} not in 60fps list!!! Just set to UserFPS directly")
                            setCurrentUserFpsAsFinal(mContext!!, param.thisObject)
                            param.args[1] = mUserFPS
                        }
                    }

                }
            }
        )

        if (isOldVersion && mainPrefs.getBoolean("force_enable_refresh_custom", false)) {
            XposedHelpers.findAndHookMethod(
                "com.miui.powerkeeper.statemachine.DisplayFrameSetting",
                lpparam.classLoader,
                "handleMessage",
                Message::class.java,
                object : XC_MethodHook() {
                    @SuppressLint("PrivateApi")
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // 3 == Game 4 == Apps 5 == Video
                        val message = param.args[0] as Message
                        if (message.what == 4) {
                            Log.i(
                                "Art_Chen",
                                "App Refresh rate policy changed by Cloud Control!! re-apply mTopApps List!"
                            )
                            setAllCustomAppToTopApp(mContext!!)
                        }
                    }
                }
            )

            XposedHelpers.findAndHookMethod(
                "com.miui.powerkeeper.statemachine.DisplayFrameSetting",
                lpparam.classLoader,
                "onPkgExistentChanged",
                "com.miui.powerkeeper.event.EventsAggregator\$EventResult",
                object : XC_MethodHook() {
                    @SuppressLint("PrivateApi")
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (mContext == null) {
                            mContext = XposedHelpers.getObjectField(param.thisObject, "mContext") as Context
                        }
                        setAllCustomAppToTopApp(mContext!!)
                    }
                }
            )
        }
    }

    private fun setCurrentUserFpsAsFinal(context: Context, thiz: Any) {
        val mUserFPS = XposedHelpers.getIntField(thiz, "mUserFps")
        val mContext =
            XposedHelpers.getObjectField(thiz, "mContext") as Context
        Settings.System.putInt(
            mContext.contentResolver,
            "peak_refresh_rate",
            mUserFPS
        )
        Settings.Secure.putInt(
            mContext.contentResolver,
            "miui_refresh_rate",
            mUserFPS
        );
        XposedHelpers.setIntField(thiz, "mCurrentFps", mUserFPS)
    }

    private fun initCustomBlackList(context: Context) {
        Log.d("Art_Chen", "[PowerKeeper][CustomPolicy] initCustomBlackList--> Get Top Apps and Video Apps")
        val mTopApps = XposedHelpers.getObjectField(mThiz, "mTopApps") as ArrayMap<*, *>
        val mVideoApps = XposedHelpers.getObjectField(mThiz, "mVideoApps") as ArraySet<*>

        Log.d("Art_Chen", "[PowerKeeper][CustomPolicy] initCustomBlackList")

        // Clear Before set
        Settings.Global.putString(context.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY, "")
        for (app in mTopApps) {
            if (app.value == 60) {
                addNewBlackListAppsToList(context, app.key as String)
            }
        }

        for (app in mVideoApps) {
            addNewBlackListAppsToList(context, app as String)
        }
    }

    private fun getIsNonSupportedVersion(lpparam: XC_LoadPackage.LoadPackageParam?): Boolean {

        val parseCustomModeSwitchFromDbMethod: Any?
        try {
            parseCustomModeSwitchFromDbMethod = XposedHelpers.findMethodExact(
                Class.forName(
                    "com.miui.powerkeeper.statemachine.DisplayFrameSetting",
                    false,
                    lpparam!!.classLoader
                ),
                "parseCustomModeSwitchFromDb",
                String::class.java
            )
        } catch (e: NoSuchMethodError) {
            return true
        }
        return parseCustomModeSwitchFromDbMethod == null
    }

    private fun getNewTopAppsList(context: Context) {
        val pkgName = Settings.System.getString(context.contentResolver, "custom_mode")
        val pkgArr = pkgName.split(",")
        val mCurrentTopApps = XposedHelpers.getObjectField(mThiz, "mTopApps") as ArrayMap<String, Int>

        Log.d("Art_Chen", "[PowerKeeper][CustomPolicy] handleBlackListChanged pkgName: $pkgName")
        if (pkgArr[1] == "1") {
            Log.i("Art_Chen", "[PowerKeeper][CustomPolicy] blacklist removed for ${pkgArr[0]}")
            removeAppsFromList(context, pkgArr[0])
            if (mCurrentTopApps.contains(pkgArr[0])) {
                mCurrentTopApps.remove(pkgArr[0])
            }
        } else {
            Log.i("Art_Chen", "[PowerKeeper][CustomPolicy] blacklist added for ${pkgArr[0]}")
            addNewBlackListAppsToList(context, pkgArr[0])
            mCurrentTopApps[pkgArr[0]] = 60
        }
        XposedHelpers.setObjectField(mThiz, "mTopApps", mCurrentTopApps)
    }

    fun addNewBlackListAppsToList(context: Context, packageName: String) {
        val listStr = Settings.Global.getString(context.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY)
        val listArr = listStrToArr(listStr)
        if (!listArr.contains(packageName)) {
            Settings.Global.putString(
                context.contentResolver,
                SETTINGS_CUSTOM_ARRAY_KEY,
                if (listStr.isEmpty())
                    packageName
                else
                    "$listStr,$packageName"
            )
            Log.d(
                "Art_Chen",
                "[PowerKeeper][CustomPolicy] added New BlackList Apps To List, pkgName: $packageName"
            )
        }
    }

    fun removeAppsFromList(context: Context, packageName: String) {
        val listStr = Settings.Global.getString(context.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY)
        val listArr = listStrToArr(listStr)
        if (listArr.contains(packageName)) {
            listArr.remove(packageName)
            Log.d("Art_Chen", "[PowerKeeper][CustomPolicy] removeAppsFromList, pkgName: $packageName")
        }
        Settings.Global.putString(
            context.contentResolver,
            SETTINGS_CUSTOM_ARRAY_KEY,
            listArrToStr(listArr)
        )
    }

    fun listArrToStr(list: ArrayList<String>) : String {
        return list.joinToString(separator = ",")
    }

    fun listStrToArr(listStr: String) : ArrayList<String> {
        return ArrayList(listStr.split(","))
    }

    fun checkAppInBlackList(context: Context, pkgName: String) : Boolean {
        val listStr = Settings.Global.getString(context.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY)
        val list = listStrToArr(listStr)
        return list.contains(pkgName)
    }

    fun setAllCustomAppToTopApp(context: Context) {
        val listStr = Settings.Global.getString(context.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY)

        if (mCustomTopApps.isEmpty()) {
            for (app in listStrToArr(listStr)) {
                mCustomTopApps.put(app, 60)
            }
        }

        XposedHelpers.setObjectField(mThiz, "mTopApps", mCustomTopApps)
    }
}