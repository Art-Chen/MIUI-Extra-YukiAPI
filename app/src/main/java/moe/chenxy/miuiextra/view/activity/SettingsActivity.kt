package moe.chenxy.miuiextra.view.activity

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.TwoStatePreference
import androidx.preference.size
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import moe.chenxy.miuiextra.R
import moe.chenxy.miuiextra.utils.ChenUtils
import rikka.preference.SimpleMenuPreference
import kotlin.system.exitProcess

private var isActivated = false
const val SHELL_RESTART_MIUI_HOME = "am force-stop com.miui.home"
const val SHELL_RESTART_SYSTEMUI = "killall com.android.systemui"
const val SHELL_RESTART_MI_WALLPAPER = "killall com.miui.miwallpaper"
class SettingsActivity : AppCompatActivity() {


    @SuppressLint("WorldReadableFiles")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true)
        setContentView(R.layout.settings_activity)

        // Adapt transparent navbar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = Color.TRANSPARENT


        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setTitle(R.string.title_activity_settings)


        try {
            // getSharedPreferences will hooked by LSPosed and change xml file path to /data/misc/**
            // will not throw SecurityException
            getSharedPreferences("chen_main_settings", Context.MODE_WORLD_READABLE)
            isActivated = true
        } catch (exception: SecurityException) {
//            AlertDialog.Builder(this)
//                .setMessage(R.string.unsupported_xposed_version)
//                .setPositiveButton(
//                    R.string.confirm
//                ) { _: DialogInterface?, _: Int ->
//                    finish()
//                    exitProcess(0)
//                }
//                .setNegativeButton("Ignore", null)
//                .show()
            isActivated = false
        }

    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private fun setActivateStatus(isActivated: Boolean, preference: Preference) {
            if (!isActivated) {
                preference.title = resources.getString(R.string.deactivated)
                resources.getDrawable(R.drawable.ic_round_error_outline, null)?.let {
                    DrawableCompat.setTint(it, Color.RED)
                    preference.icon = it
                }
            }
        }

        private fun checkAndroidVersion(preference: Preference?) {
            if (ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.T)) {
                preference?.let { preferenceScreen.removePreference(it) }
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference is TwoStatePreference) {
                val category = preference.parent as PreferenceCategory
                when (category.key) {
                    "wallpaper_settings" -> showRebootSnackBar(R.string.may_need_reboot, SHELL_RESTART_MI_WALLPAPER)
                    "status_bar_and_cc_category", "home_handle" -> showRebootSnackBar(R.string.may_need_reboot, SHELL_RESTART_SYSTEMUI)
                }
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
            }
            return super.onPreferenceTreeClick(preference)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "chen_main_settings"
            setPreferencesFromResource(R.xml.chen_preferences, rootKey)

            checkAndroidVersion(findPreference("android_version_warn"))

            findPreference<Preference>("activate_status")?.let {
                setActivateStatus(
                    isActivated,
                    it
                )
            }

            listenMiuiCustomMode(findPreference("force_enable_refresh_custom")!!)

            findPreference<Preference>("vibrator_map")?.intent = Intent(context, VibratorRemapActivity::class.java)
            findPreference<Preference>("vibrator_effect_map")?.intent = Intent(context, VibratorEffectRemapActivity::class.java)
            findPreference<Preference>("wallpaper_zoom")?.intent = Intent(context, WallpaperZoomActivity::class.java)

            findPreference<SwitchPreferenceCompat>("miui_home_anim_enhance")?.setOnPreferenceChangeListener { _, _ ->
                showRebootSnackBar(R.string.may_need_reboot_miui_home, SHELL_RESTART_MIUI_HOME)
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("miui_unlock_wallpaper_anim_fade")?.setOnPreferenceChangeListener { _, newValue ->
                showRebootSnackBar(null, SHELL_RESTART_MI_WALLPAPER)
                return@setOnPreferenceChangeListener true
            }

            bindAnimationSeekBarNoEditText(findPreference("home_handle_y_val"), 1)

            val colorFadeOnCustom = findPreference<SeekBarPreference>("screen_on_color_fade_anim_val")
            val colorFadeOffCustom = findPreference<SeekBarPreference>("screen_off_color_fade_anim_val")
            val colorFadeCustom = findPreference<SwitchPreferenceCompat>("color_fade_anim_smoothly")
            if (colorFadeOffCustom != null) {
                bindScreenOnOffClickAlert(colorFadeOffCustom,
                    R.string.screen_off_color_fade_anim_val_title
                )
            }
            if (colorFadeOnCustom != null) {
                bindScreenOnOffClickAlert(colorFadeOnCustom,
                    R.string.screen_on_color_fade_anim_val_title
                )
            }
            findPreference<SeekBarPreference>("miui_unlock_wallpaper_anim_fade_anim_val")?.let {
                bindScreenOnOffClickAlert(
                    it, R.string.miui_unlock_wallpaper_anim_fade_anim_val_title
                )
            }

            colorFadeCustom?.setOnPreferenceChangeListener { _, _ ->
                showRebootSnackBar(null, null)
                return@setOnPreferenceChangeListener true
            }

            val musicNotificationOptimize = findPreference<SwitchPreferenceCompat>("music_notification_optimize")
            val musicNotificationOptimizeForegroundColor = findPreference<SwitchPreferenceCompat>("music_notification_optimize_foreground_color")
            if (!ChenUtils.isAboveAndroidVersion(ChenUtils.Companion.AndroidVersion.U)) {
                musicNotificationOptimize?.setOnPreferenceChangeListener { _, _ ->
                    showRebootSnackBar(null, SHELL_RESTART_SYSTEMUI)
                    return@setOnPreferenceChangeListener true
                }

                musicNotificationOptimizeForegroundColor?.setOnPreferenceChangeListener { _, _ ->
                    showRebootSnackBar(null, SHELL_RESTART_SYSTEMUI)
                    return@setOnPreferenceChangeListener true
                }
            } else {
                // These options is unneeded for U
                val group = musicNotificationOptimize?.parent
                group?.removePreference(musicNotificationOptimize)
                group?.removePreference(musicNotificationOptimizeForegroundColor!!)

                if (group?.size == 0) {
                    preferenceScreen.removePreference(group)
                }
            }

            findPreference<SwitchPreferenceCompat>("do_not_override_pending_transition")?.setOnPreferenceChangeListener { _, newValue ->
                showRebootSnackBar(null, null)
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("use_chen_volume_animation")?.setOnPreferenceChangeListener { _, newValue ->
                showRebootSnackBar(null, SHELL_RESTART_SYSTEMUI)
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("hide_app_icon")?.isChecked = !isLauncherIconVisible()

            findPreference<SwitchPreferenceCompat>("hide_app_icon")?.setOnPreferenceChangeListener { _, newValue ->
                setLauncherIconVisible(!(newValue as Boolean))
                return@setOnPreferenceChangeListener true
            }

            bindAnimationSeekBarNoEditText(findPreference("blur_scale_val"), 1)
            bindAnimationSeekBarNoEditText(findPreference("home_handle_auto_trans_alpha_val"), 1)
        }

        private val setColorFadeSettings : Runnable = Runnable {
            context?.sendBroadcast(Intent("chen.miui.extra.update.colorfade"))
        }

        private fun showRebootSnackBar(resId: Int?, shell: String?) {
            Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout),
                resId ?: R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                .setAction(R.string.reboot) { _ ->
                    ChenUtils.execShell(shell ?: "/system/bin/svc power reboot")
                }
                .show()
        }

        private fun bindAnimationSeekBarNoEditText(preference: SeekBarPreference?, scale: Int) {
            preference?.summary = "${(preference?.value as Int).toFloat() / scale} f"
            preference.setOnPreferenceChangeListener { pref, newValue ->
                pref.summary = "${(newValue as Int).toFloat() / scale} f"
                ChenUtils.performVibrateHeavyClick(requireContext())

                showRebootSnackBar(null, SHELL_RESTART_SYSTEMUI)
                return@setOnPreferenceChangeListener true
            }
        }

        private fun bindScreenOnOffClickAlert(preference: SeekBarPreference, title: Int) {
            val mHandler = Handler(Looper.getMainLooper())

            preference.summary = "${preference.value} ms"
            preference.setOnPreferenceChangeListener { preference, newValue ->
                mHandler.removeCallbacks(setColorFadeSettings)
                preference.summary = "$newValue ms"
                mHandler.postDelayed(setColorFadeSettings, 100)
                ChenUtils.performVibrateHeavyClick(requireContext())
                return@setOnPreferenceChangeListener true
            }
            preference.setOnPreferenceClickListener {
                val inputDialog = MaterialAlertDialogBuilder(this.requireContext())
                val chenView = LayoutInflater.from(activity).inflate(R.layout.chen_edittext_dialog, null)
                val editText = chenView.findViewById<EditText>(R.id.chen_edittext_dialog_edittext)
                editText.hint = preference.value.toString()
                inputDialog
                    .setTitle(title)
                    .setView(chenView)
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        val inputText = editText.text.toString()
                        var inputInt = 0
                        if (inputText.isNotEmpty()) inputInt = inputText.toInt()
                        if (inputInt > preference.max || inputInt < preference.min) {
                            Toast.makeText(
                                this.context,
                                "Input Value Invalid!!",
                                Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            preference.value = inputInt
                            preference.summary = "$inputInt ms"
                            mHandler.postDelayed(setColorFadeSettings, 100)
                        }
                    }
                    .show()
                return@setOnPreferenceClickListener true
            }
        }


        private val ALIAS_ACTIVITY_NAME = "moe.chenxy.miuiextra.SettingsActivityAlias"

        @SuppressLint("QueryPermissionsNeeded")
        private fun isLauncherIconVisible(): Boolean {
            val component = context?.let { ComponentName(it, ALIAS_ACTIVITY_NAME) }
            val manager = activity?.packageManager
            val intent = Intent().setComponent(component)

            val list: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager!!.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                manager!!.queryIntentActivities(
                    intent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            }

            return list.isNotEmpty()
        }

        private fun setLauncherIconVisible(visible: Boolean) {
            if (isLauncherIconVisible() == visible) return
            val component = context?.let { ComponentName(it, ALIAS_ACTIVITY_NAME) }
            val manager = activity?.packageManager
            val newState =
                if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (component != null) {
                manager?.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
            }
        }

        private fun listenMiuiCustomMode(customModePerf: SwitchPreferenceCompat) {
            val isNativeSupported = Settings.Global.getString(context?.contentResolver, "custom_mode_is_native_supported") == "true"
            if (Settings.System.getString(context?.contentResolver, "custom_mode_switch") == "true" && isNativeSupported) {
                // Only set Checked when native supported. this solved custom mode can't disable after enabled
                customModePerf.isChecked = true
            }

            if (Settings.System.getString(context?.contentResolver, "custom_mode_is_chen_policy") == "true") {
                customModePerf.summaryOn = "Using Chen Custom Policy Mode"
            } else {
                customModePerf.summaryOn = "Using MIUI Official Policy Mode" + if (isNativeSupported) " (Native Supported)" else " (Force Enabled)"
            }

            customModePerf.setOnPreferenceChangeListener { _, _ ->
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout),
                    R.string.may_need_reboot_PowerKeeper, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.miui.powerkeeper")
                        ChenUtils.execShell("killall com.xiaomi.misettings")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }
        }
    }

}