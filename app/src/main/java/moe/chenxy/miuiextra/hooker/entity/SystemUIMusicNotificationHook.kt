package moe.chenxy.miuiextra.hooker.entity

import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntClass
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.StringClass
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedHelpers
import moe.chenxy.miuiextra.BuildConfig


object SystemUIMusicNotificationHook : YukiBaseHooker() {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")

    // Media Control Panel Hook
    override fun onHook() {
        var mLastArtwork: Icon? = null
        mainPrefs.reload()

        "com.android.systemui.media.MediaControlPanel".hook {
            injectMember {
                method {
                    name = "bindArtworkAndColors"
                    param("com.android.systemui.media.MediaData", StringClass, BooleanType)
                }
                beforeHook {
                    val artwork: Icon =
                        XposedHelpers.callMethod(this.args[0], "getArtwork") as Icon
                    if (artwork != mLastArtwork) {
                        Log.v("Art_Chen", "Force updated artwork!")
                        mLastArtwork = artwork
                        this.args[2] = true
                    }
                }
            }
        }


        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaControlPanel".hook {
            // Set App Name View Only
            injectMember {
                method {
                    name = "setInfoText"
                    param(
                        "com.android.systemui.media.MediaData",
                        "com.android.systemui.media.MediaViewHolder"
                    )
                }
                replaceUnit {
                    val appName =
                        XposedHelpers.callMethod(this.args[1], "getAppName") as TextView
                    appName.text = XposedHelpers.callMethod(
                        this.args[0],
                        "getApp"
                    ) as String
                }
            }

            if (mainPrefs.getBoolean("music_notification_optimize_foreground_color", false)) {
                injectMember {
                    method {
                        name = "setForegroundColors"
                        param("com.android.systemui.statusbar.notification.mediacontrol.ProcessArtworkTask\$Result")
                    }
                    beforeHook {
                        this.result = null
                    }
                }
            }
        }
        var mediaViewHolder: Any? = null
        if (mainPrefs.getBoolean("music_notification_optimize_foreground_color", false)) {
            "com.android.systemui.media.ColorSchemeTransition\$accentPrimary$2".hook {
                injectMember {
                    method {
                        name = "invoke"
                        param(IntClass)
                    }
                    beforeHook {
                        mediaViewHolder = XposedHelpers.getObjectField(
                            XposedHelpers.getObjectField(
                                this.instance,
                                "this\$0"
                            ), "mediaViewHolder"
                        )
                        val action0 =
                            XposedHelpers.callMethod(mediaViewHolder, "getAction0") as ImageView
                        val action1 =
                            XposedHelpers.callMethod(mediaViewHolder, "getAction1") as ImageView
                        val action2 =
                            XposedHelpers.callMethod(mediaViewHolder, "getAction2") as ImageView
                        val action3 =
                            XposedHelpers.callMethod(mediaViewHolder, "getAction3") as ImageView
                        val action4 =
                            XposedHelpers.callMethod(mediaViewHolder, "getAction4") as ImageView
                        action0.backgroundTintList = ColorStateList.valueOf(this.args[0] as Int)
                        action1.backgroundTintList = ColorStateList.valueOf(this.args[0] as Int)
                        action2.backgroundTintList = ColorStateList.valueOf(this.args[0] as Int)
                        action3.backgroundTintList = ColorStateList.valueOf(this.args[0] as Int)
                        action4.backgroundTintList = ColorStateList.valueOf(this.args[0] as Int)
                    }
                }
            }

            "com.android.systemui.media.ColorSchemeTransition\$colorSeamless$1".hook {
                injectMember {
                    method {
                        name = "invoke"
                        param("com.android.systemui.monet.ColorScheme")
                    }
                    afterHook {
                        mediaViewHolder = XposedHelpers.getObjectField(
                            XposedHelpers.getObjectField(
                                this.instance,
                                "this\$0"
                            ), "mediaViewHolder"
                        )
                    }
                }
            }

            "com.android.systemui.media.ColorSchemeTransition\$colorSeamless$2".hook {
                injectMember {
                    method {
                        name = "invoke"
                        param(IntType)
                    }
                    afterHook {
                        val seamlessIcon = XposedHelpers.callMethod(
                            mediaViewHolder,
                            "getSeamlessIcon"
                        ) as ImageView
                        seamlessIcon.imageTintList = ColorStateList.valueOf(this.args[0] as Int)
                    }
                }
            }

            // Apply Color Changes
            "com.android.systemui.media.ColorSchemeTransition".hook {
                injectMember {
                    method {
                        name = "updateColorScheme"
                        param("com.android.systemui.monet.ColorScheme", BooleanType)
                    }
                    beforeHook {
                        try {
                            mediaViewHolder =
                                XposedHelpers.getObjectField(this.instance, "mediaViewHolder")
                            val colorScheme = this.args[0]

                            val colorTransitionsNotUpdated = arrayOf(
                                XposedHelpers.getObjectField(this.instance, "surfaceColor"),
                                XposedHelpers.getObjectField(this.instance, "colorSeamless"),
                                XposedHelpers.getObjectField(this.instance, "accentPrimary"),
                                XposedHelpers.getObjectField(this.instance, "accentSecondary"),
                                XposedHelpers.getObjectField(this.instance, "textPrimary"),
                                XposedHelpers.getObjectField(
                                    this.instance,
                                    "textPrimaryInverse"
                                ),
                                XposedHelpers.getObjectField(this.instance, "textSecondary"),
                                XposedHelpers.getObjectField(this.instance, "textTertiary"),
                            )
                            for (obj in colorTransitionsNotUpdated) {
                                XposedHelpers.callMethod(obj, "updateColorScheme", colorScheme)
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                            Log.i("Art_Chen", "pre updateColorScheme failed!")
                        }
                    }
                }
            }
        }
    }
}