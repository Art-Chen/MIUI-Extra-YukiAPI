package moe.chenxy.miuiextra.hooker.entity.systemui

import android.util.Log
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XSharedPreferences
import moe.chenxy.miuiextra.BuildConfig

object SystemUIMainHooker : YukiBaseHooker() {
    override fun onHook() {
        val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")

        loadHooker(SystemUIPluginHook)

        if (mainPrefs.getBoolean("music_notification_optimize", false)) {
            // Newer MIUI replaced Music Notification design, it will not work after it, so ignored all failure
            try {
                loadHooker(HomeHandleAnimatorHooker)
            } catch (e: Throwable) {
                Log.w("Art_Chen", "Failed to load SystemUIMusicNotificationHook. This version of SystemUI may not supported!")
            }
        }

        if (mainPrefs.getBoolean("chen_home_handle_anim", false)) {
            loadHooker(HomeHandleAnimatorHooker)
        }

        loadHooker(StatusBarBlurUtilsHooker)
    }
}