package moe.chenxy.miuiextra.hooker

import android.util.Log
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.YukiHookAPI.configs
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import de.robv.android.xposed.XSharedPreferences
import moe.chenxy.miuiextra.BuildConfig
import moe.chenxy.miuiextra.hooker.entity.MiWallpaperHook
import moe.chenxy.miuiextra.hooker.entity.MiuiHomeHook
import moe.chenxy.miuiextra.hooker.entity.MiuiSettingsHook
import moe.chenxy.miuiextra.hooker.entity.PowerKeeperHook
import moe.chenxy.miuiextra.hooker.entity.SystemHooker
import moe.chenxy.miuiextra.hooker.entity.SystemUIMusicNotificationHook
import moe.chenxy.miuiextra.hooker.entity.SystemUINavigationBarHook
import moe.chenxy.miuiextra.hooker.entity.SystemUIPluginHook

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    private val mainPrefs = XSharedPreferences(BuildConfig.APPLICATION_ID, "chen_main_settings")

    override fun onHook() = YukiHookAPI.encase {
        mainPrefs.reload()
        loadSystem(SystemHooker)
        if (mainPrefs.getBoolean("chen_home_handle_anim", false)) {
            loadApp("com.android.systemui", SystemUINavigationBarHook)
        }
        loadApp("com.android.systemui", SystemUIPluginHook)
        if (mainPrefs.getBoolean("music_notification_optimize", false)) {
            // Newer MIUI replaced Music Notification design, it will not work after it, so ignored all failure
            try {
                loadApp("com.android.systemui", SystemUIMusicNotificationHook)
            } catch (e: Throwable) {
                Log.w("Art_Chen", "Failed to load SystemUIMusicNotificationHook. This version of SystemUI may not supported!")
            }
        }
        loadApp("com.miui.powerkeeper", PowerKeeperHook)
        loadApp("com.miui.home", MiuiHomeHook)
        loadApp("com.xiaomi.misettings", MiuiSettingsHook)
        loadApp("com.miui.miwallpaper", MiWallpaperHook)

    }

    override fun onInit() = configs {
//        isDebug = BuildConfig.DEBUG
        isDebug = false
    }
}