package moe.chenxy.miuiextra.hooker

import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import moe.chenxy.miuiextra.BuildConfig


class SystemUIMusicNotificationHook {
    val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")
    // MediaControl Panel Hook
    fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val classLoader = lpparam!!.classLoader
        var mLastArtwork: Icon? = null
        mainPrefs.reload()

        if (mainPrefs.getBoolean("music_notification_optimize", false)) {
            XposedHelpers.findAndHookMethod("com.android.systemui.media.MediaControlPanel",
                classLoader,
                "bindArtworkAndColors",
                "com.android.systemui.media.MediaData",
                String::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        val artwork: Icon =
                            XposedHelpers.callMethod(param!!.args[0], "getArtwork") as Icon
                        if (artwork != mLastArtwork) {
                            Log.v("Art_Chen", "Force updated artwork!")
                            mLastArtwork = artwork
                            param.args[2] = true
                        }
                    }
                })

            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaControlPanel",
                classLoader,
                "setInfoText",
                "com.android.systemui.media.MediaData",
                "com.android.systemui.media.MediaViewHolder",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val appName =
                            XposedHelpers.callMethod(param.args[1], "getAppName") as TextView
                        appName.setText(
                            XposedHelpers.callMethod(
                                param.args[0],
                                "getApp"
                            ) as String
                        );
                        param.result = 0
                    }
                }
            )
        }

        if (mainPrefs.getBoolean("music_notification_optimize_foreground_color", false)) {
            var mediaViewHolder: Any? = null
            XposedHelpers.findAndHookMethod("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaControlPanel",
                classLoader,
                "setForegroundColors",
                "com.android.systemui.statusbar.notification.mediacontrol.ProcessArtworkTask\$Result",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = 0
                    }
                }
            )
            XposedHelpers.findAndHookMethod("com.android.systemui.media.ColorSchemeTransition\$accentPrimary$2",
                classLoader,
                "invoke",
                Int::class.java,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        mediaViewHolder = XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.thisObject, "this\$0"), "mediaViewHolder")
                        val action0 = XposedHelpers.callMethod(mediaViewHolder, "getAction0") as ImageView
                        val action1 = XposedHelpers.callMethod(mediaViewHolder, "getAction1") as ImageView
                        val action2 = XposedHelpers.callMethod(mediaViewHolder, "getAction2") as ImageView
                        val action3 = XposedHelpers.callMethod(mediaViewHolder, "getAction3") as ImageView
                        val action4 = XposedHelpers.callMethod(mediaViewHolder, "getAction4") as ImageView
                        action0.backgroundTintList = ColorStateList.valueOf(param.args[0] as Int)
                        action1.backgroundTintList = ColorStateList.valueOf(param.args[0] as Int)
                        action2.backgroundTintList = ColorStateList.valueOf(param.args[0] as Int)
                        action3.backgroundTintList = ColorStateList.valueOf(param.args[0] as Int)
                        action4.backgroundTintList = ColorStateList.valueOf(param.args[0] as Int)
                    }
                })

//            XposedHelpers.findAndHookMethod("com.android.systemui.media.ColorSchemeTransition\$textPrimaryInverse$2",
//                classLoader,
//                "invoke",
//                Int::class.java,
//                object : XC_MethodHook() {
//                    @Throws(Throwable::class)
//                    override fun beforeHookedMethod(param: MethodHookParam) {
//                        Log.i("Art_Chen", "textPrimaryInverse: color ${param.args[0] as Int}")
//                        val action0 = XposedHelpers.callMethod(mediaViewHolder, "getAction0") as ImageView
//                        val action1 = XposedHelpers.callMethod(mediaViewHolder, "getAction1") as ImageView
//                        val action2 = XposedHelpers.callMethod(mediaViewHolder, "getAction2") as ImageView
//                        val action3 = XposedHelpers.callMethod(mediaViewHolder, "getAction3") as ImageView
//                        val action4 = XposedHelpers.callMethod(mediaViewHolder, "getAction4") as ImageView
//                        action0.imageTintList = ColorStateList.valueOf(param.args[0] as Int)
//                        action1.imageTintList = ColorStateList.valueOf(param.args[0] as Int)
//                        action2.imageTintList = ColorStateList.valueOf(param.args[0] as Int)
//                        action3.imageTintList = ColorStateList.valueOf(param.args[0] as Int)
//                        action4.imageTintList = ColorStateList.valueOf(param.args[0] as Int)
//                    }
//
//
//                })

            XposedHelpers.findAndHookMethod("com.android.systemui.media.ColorSchemeTransition\$colorSeamless$1",
                classLoader,
                "invoke",
                "com.android.systemui.monet.ColorScheme",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        mediaViewHolder = XposedHelpers.getObjectField(XposedHelpers.getObjectField(param.thisObject, "this\$0"), "mediaViewHolder")

                    }
                })

            XposedHelpers.findAndHookMethod("com.android.systemui.media.ColorSchemeTransition\$colorSeamless$2",
                classLoader,
                "invoke",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val seamlessIcon = XposedHelpers.callMethod(mediaViewHolder, "getSeamlessIcon") as ImageView
                        seamlessIcon.imageTintList = ColorStateList.valueOf(param.args[0] as Int)
                    }
                })

            XposedHelpers.findAndHookMethod("com.android.systemui.media.ColorSchemeTransition",
                classLoader,
                "updateColorScheme",
                "com.android.systemui.monet.ColorScheme",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            mediaViewHolder = XposedHelpers.getObjectField(param.thisObject, "mediaViewHolder")
                            val colorScheme = param.args[0]

                            val colorTransitionsNotUpdated = arrayOf(
                                XposedHelpers.getObjectField(param.thisObject, "surfaceColor"),
                                XposedHelpers.getObjectField(param.thisObject, "colorSeamless"),
                                XposedHelpers.getObjectField(param.thisObject, "accentPrimary"),
                                XposedHelpers.getObjectField(param.thisObject, "accentSecondary"),
                                XposedHelpers.getObjectField(param.thisObject, "textPrimary"),
                                XposedHelpers.getObjectField(param.thisObject, "textPrimaryInverse"),
                                XposedHelpers.getObjectField(param.thisObject, "textSecondary"),
                                XposedHelpers.getObjectField(param.thisObject, "textTertiary"),
                            )
                            for (obj in colorTransitionsNotUpdated) {
                                XposedHelpers.callMethod(obj, "updateColorScheme", colorScheme)
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            Log.i("Art_Chen", "pre updateColorScheme failed!")
                        }

                    }
                })
        }
    }
}