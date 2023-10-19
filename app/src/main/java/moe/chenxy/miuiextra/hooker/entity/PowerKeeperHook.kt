package moe.chenxy.miuiextra.hooker.entity

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Message
import android.provider.Settings
import android.util.ArrayMap
import android.util.ArraySet
import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.constructor
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.LooperClass
import com.highcapable.yukihookapi.hook.type.android.MessageClass
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig

object PowerKeeperHook : YukiBaseHooker() {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    private const val COOKIE_VIDEO = 248
    const val SETTINGS_CUSTOM_ARRAY_KEY = "chen_custom_refresh_policy_blacklist"
    private val mCustomTopApps = ArrayMap<String, Int>()
    private var mThiz: Any? = null

    override fun onHook() {
        val isOldVersion = getIsNonSupportedVersion()
        mainPrefs.reload()

        "com.miui.powerkeeper.statemachine.DisplayFrameSetting".toClass().apply {
            // Init for Chen Policy
            constructor {
                param(ContextClass, LooperClass)
            }.hook {
                after {
                    mainPrefs.reload()
                    val mContext =
                        XposedHelpers.getObjectField(this.instance, "mContext") as Context

                    if (isOldVersion) {
                        Log.e("Art_Chen", "[PowerKeeper] Old Version PowerKeeper detected!!")
                        mThiz = this.instance
                        if (mainPrefs.getBoolean("force_enable_refresh_custom", false)) {
                            Log.d(
                                "Art_Chen",
                                "[PowerKeeper][CustomPolicy] Force Enabled refresh rate policy!"
                            )
                            val mAppliedChenPolicy =
                                Settings.System.getString(
                                    mContext.contentResolver,
                                    "custom_mode_is_chen_policy"
                                ) == "true"
                            if (!mAppliedChenPolicy) {
                                initCustomBlackList(mContext)

                                // Tell MiSetting that we supported CustomMode
                                Settings.System.putString(
                                    mContext.contentResolver,
                                    "custom_mode_switch",
                                    "true"
                                )
                                Settings.System.putString(
                                    mContext.contentResolver,
                                    "custom_mode_is_chen_policy",
                                    "true"
                                )
                                Settings.Secure.putString(
                                    mContext.contentResolver,
                                    "custom_mode_switch",
                                    "true"
                                )
                            }

                            setAllCustomAppToTopApp(mContext)
                            Log.d(
                                "Art_Chen",
                                "[PowerKeeper][CustomPolicy] Now TopApp Replaced to Custom App List"
                            )

                            // Watch RefreshRate List Changed
                            val contentObserver =
                                object : ContentObserver(this.instance as Handler?) {
                                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                                        Log.d(
                                            "Art_Chen",
                                            "[PowerKeeper][CustomPolicy] list changed, let's update it!"
                                        )
                                        getNewTopAppsList(mContext)
                                    }
                                }
                            mContext.contentResolver.registerContentObserver(
                                Settings.System.getUriFor("custom_mode"),
                                false,
                                contentObserver
                            )
                        } else {
                            // Restore all bool to unsupported
                            Settings.System.putString(
                                mContext.contentResolver,
                                "custom_mode_switch",
                                "false"
                            )
                            Settings.System.putString(
                                mContext.contentResolver,
                                "custom_mode_is_chen_policy",
                                "false"
                            )
                            Settings.Secure.putString(
                                mContext.contentResolver,
                                "custom_mode_switch",
                                "false"
                            )
                        }
                    } else {
                        Log.i(
                            "Art_Chen",
                            "[PowerKeeper][CustomRefreshRatePolicy] Native Supported Version PowerKeeper detected!!"
                        )
                        val mAppliedChenPolicy =
                            Settings.System.getString(
                                mContext.contentResolver,
                                "custom_mode_is_chen_policy"
                            ) == "true"
                        if (mAppliedChenPolicy) {
                            Log.i("Art_Chen", "cleanup chen policy to use official policy.")
                            Settings.System.putString(
                                mContext.contentResolver,
                                "custom_mode_is_chen_policy",
                                "false"
                            )
                            Settings.Global.putString(
                                mContext.contentResolver,
                                SETTINGS_CUSTOM_ARRAY_KEY,
                                ""
                            )
                        }
                    }
                }
            }

            // Force Enable Custom Mode if PowerKeeper supported this
            if (!isOldVersion) {
                method {
                    name = "parseCustomModeSwitchFromDb"
                    param(StringClass)
                }.hook {
                    after {
                        val mIsCustomFpsSwitch =
                            XposedHelpers.getObjectField(this.instance, "mIsCustomFpsSwitch")
                        val mIsNativeSupported =
                            mIsCustomFpsSwitch != null && (mIsCustomFpsSwitch as String) == "true"
                        val mContext = XposedHelpers.getObjectField(
                            this.instance,
                            "mContext"
                        ) as Context

                        if (mIsNativeSupported) {
                            Settings.Global.putString(mContext.contentResolver, "custom_mode_is_native_supported", "true")
                        }
                        mainPrefs.reload()

                        if (mainPrefs.getBoolean(
                                "force_enable_refresh_custom",
                                false
                            ) && !mIsNativeSupported
                        ) {
                            Settings.System.putString(
                                mContext.contentResolver,
                                "custom_mode_switch",
                                "true"
                            )
                            XposedHelpers.setObjectField(
                                this.instance,
                                "mIsCustomFpsSwitch",
                                "true"
                            )
                        }
                    }
                }
            }
            
            // Disable Miui Refresh Rate Policy
            method {
                name = "setScreenEffect"
                param(StringClass, IntType, IntType)
            }.hook {
                before {
                    val cookie = this.args[2] as Int
                    val fps = this.args[1] as Int
                    mainPrefs.reload()

                    val mContext =
                        XposedHelpers.getObjectField(this.instance, "mContext") as Context

                    if (mainPrefs.getBoolean("force_current_refresh_rate", false)) {
                        val mUserFPS = XposedHelpers.getIntField(this.instance, "mUserFps")

                        setCurrentUserFpsAsFinal(this.instance)
                        this.args[1] = mUserFPS
                        this.result = null
                    }

                    if (isOldVersion && mainPrefs.getBoolean(
                            "force_enable_refresh_custom",
                            false
                        )
                    ) {
                        if (cookie == COOKIE_VIDEO && !checkAppInBlackList(
                                mContext,
                                this.args[0] as String
                            )
                        ) {
                            Log.i(
                                "Art_Chen",
                                "[PowerKeeper][CustomPolicy] Ignored non 60 fps Video App fps setting action!"
                            )
                            this.result = null
                        }

                        if (!checkAppInBlackList(
                                mContext,
                                this.args[0] as String
                            ) && fps == 60
                        ) {
                            val mUserFPS = XposedHelpers.getIntField(this.instance, "mUserFps")
                            Log.i(
                                "Art_Chen",
                                "[PowerKeeper][CustomPolicy] App ${this.args[0] as String} not in 60fps list!!! Just set to UserFPS directly"
                            )
                            setCurrentUserFpsAsFinal(this.instance)
                            this.args[1] = mUserFPS
                        }
                    }
                }
            }

            // Get App Refresh Rate Policy from Cloud if using Chen Policy
            if (isOldVersion && mainPrefs.getBoolean("force_enable_refresh_custom", false)) {
                method {
                    name = "handleMessage"
                    param(MessageClass)
                }.hook {
                    after {
                        // 3 == Game 4 == Apps 5 == Video
                        val message = this.args[0] as Message
                        if (message.what == 4) {
                            val mContext = XposedHelpers.getObjectField(
                                this.instance,
                                "mContext"
                            ) as Context
                            Log.i(
                                "Art_Chen",
                                "App Refresh rate policy changed by Cloud Control!! re-apply mTopApps List!"
                            )
                            setAllCustomAppToTopApp(mContext)
                        }
                    }
                }
            }

            method {
                name = "onPkgExistentChanged"
                param("com.miui.powerkeeper.event.EventsAggregator\$EventResult")
            }.hook {
                after {
                    val mContext =
                        XposedHelpers.getObjectField(this.instance, "mContext") as Context
                    setAllCustomAppToTopApp(mContext)
                }
            }
        }
    }

    private fun setCurrentUserFpsAsFinal(thiz: Any) {
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
        )
        XposedHelpers.setIntField(thiz, "mCurrentFps", mUserFPS)
    }

    private fun initCustomBlackList(context: Context) {
        Log.d(
            "Art_Chen",
            "[PowerKeeper][CustomPolicy] initCustomBlackList--> Get Top Apps and Video Apps"
        )
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

    private fun getIsNonSupportedVersion(): Boolean {

        val parseCustomModeSwitchFromDbMethod: Any?
        try {
            parseCustomModeSwitchFromDbMethod = XposedHelpers.findMethodExact(
                "com.miui.powerkeeper.statemachine.DisplayFrameSetting".toClass(),
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
        val mCurrentTopApps =
            XposedHelpers.getObjectField(mThiz, "mTopApps") as ArrayMap<String, Int>

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

    private fun addNewBlackListAppsToList(context: Context, packageName: String) {
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

    private fun removeAppsFromList(context: Context, packageName: String) {
        val listStr = Settings.Global.getString(context.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY)
        val listArr = listStrToArr(listStr)
        if (listArr.contains(packageName)) {
            listArr.remove(packageName)
            Log.d(
                "Art_Chen",
                "[PowerKeeper][CustomPolicy] removeAppsFromList, pkgName: $packageName"
            )
        }
        Settings.Global.putString(
            context.contentResolver,
            SETTINGS_CUSTOM_ARRAY_KEY,
            listArrToStr(listArr)
        )
    }

    private fun listArrToStr(list: ArrayList<String>): String {
        return list.joinToString(separator = ",")
    }

    fun listStrToArr(listStr: String): ArrayList<String> {
        return ArrayList(listStr.split(","))
    }

    private fun checkAppInBlackList(context: Context, pkgName: String): Boolean {
        val listStr = Settings.Global.getString(context.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY)
        val list = listStrToArr(listStr)
        return list.contains(pkgName)
    }

    private fun setAllCustomAppToTopApp(context: Context) {
        val listStr = Settings.Global.getString(context.contentResolver, SETTINGS_CUSTOM_ARRAY_KEY)

        if (mCustomTopApps.isEmpty()) {
            for (app in listStrToArr(listStr)) {
                mCustomTopApps.put(app, 60)
            }
        }

        XposedHelpers.setObjectField(mThiz, "mTopApps", mCustomTopApps)
    }
}