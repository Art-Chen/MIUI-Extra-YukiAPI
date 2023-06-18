package moe.chenxy.miuiextra

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginStart
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import rikka.insets.setInitialMargin


class SettingsActivity : AppCompatActivity() {

    @SuppressLint("WorldReadableFiles")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setTitle(R.string.title_activity_settings)

        try {
            // getSharedPreferences will hooked by LSPosed and change xml file path to /data/misc/edxp**
            // will not throw SecurityException
            getSharedPreferences("chen_main_settings", Context.MODE_WORLD_READABLE)
        } catch (exception: SecurityException) {
            AlertDialog.Builder(this)
                .setMessage("Unsupported Xposed detected! Please install and active LSPosed!")
                .setPositiveButton(
                    "OK"
                ) { _: DialogInterface?, _: Int -> finish() }
                .setNegativeButton("Ignore", null)
                .show()
        }

        val versionCode = Build.VERSION.RELEASE_OR_CODENAME
        if (versionCode != "13" && versionCode != "Tiramisu") {
            AlertDialog.Builder(this)
                .setMessage("Unsupported Android Version detected! Only Android T(13) Supported! Some feature may not work as expected!!")
                .setPositiveButton(
                    "Exit"
                ) { _: DialogInterface?, _: Int -> finish() }
                .setNegativeButton("Continue", null)
                .show()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "chen_main_settings"
            setPreferencesFromResource(R.xml.chen_preferences, rootKey)

            val remapPerf = findPreference<Preference>("vibrator_map")
            val intent = Intent(context, VibratorRemapActivity::class.java)

            listenMiuiCustomMode(findPreference("force_enable_refresh_custom")!!)
            listenMiuiPolicyPerf(findPreference("force_current_refresh_rate")!!)

            remapPerf?.intent = intent;
            findPreference<Preference>("vibrator_effect_map")?.intent = Intent(context, VibratorEffectRemapActivity::class.java)
            findPreference<Preference>("wallpaper_zoom")?.intent = Intent(context, WallpaperZoomActivity::class.java)

            findPreference<SwitchPreferenceCompat>("force_disable_mibridge_dynamic_refresh_scene")?.setOnPreferenceChangeListener { _, _ ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("miui_home_anim_enhance")?.setOnPreferenceChangeListener { _, _ ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot_miui_home, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("am force-stop com.miui.home")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("miui_unlock_anim_enhance")?.setOnPreferenceChangeListener { _, _ ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot_miui_home, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("am force-stop com.miui.home")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SeekBarPreference>("miui_unlock_wallpaper_anim_fade_anim_val")!!.shouldDisableView = true
            if (!findPreference<SwitchPreferenceCompat>("miui_unlock_wallpaper_anim_fade")!!.isChecked) {
                findPreference<SeekBarPreference>("miui_unlock_wallpaper_anim_fade_anim_val")!!.isEnabled = false
            }
            findPreference<SwitchPreferenceCompat>("miui_unlock_wallpaper_anim_fade")?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                findPreference<SeekBarPreference>("miui_unlock_wallpaper_anim_fade_anim_val")!!.isEnabled =
                    newValue as Boolean
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.miui.miwallpaper")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("use_chen_screen_on_anim")?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.miui.miwallpaper")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            val yValSeekBar = findPreference<SeekBarPreference>("home_handle_y_val")
            val animTurboMode = findPreference<SwitchPreferenceCompat>("chen_home_handle_anim_turbo_mode")
            val immersionMode = findPreference<SwitchPreferenceCompat>("chen_home_handle_no_space")
            val autoTransparent = findPreference<SwitchPreferenceCompat>("chen_home_handle_auto_transparent")
            val autoTransparentOnHome = findPreference<SwitchPreferenceCompat>("chen_home_handle_full_transparent_at_miuihome")
            yValSeekBar?.isEnabled = findPreference<SwitchPreferenceCompat>("chen_home_handle_anim")?.isChecked == true
            animTurboMode?.isEnabled = findPreference<SwitchPreferenceCompat>("chen_home_handle_anim")?.isChecked == true
            immersionMode?.isEnabled = findPreference<SwitchPreferenceCompat>("chen_home_handle_anim")?.isChecked == true
            autoTransparent?.isEnabled = findPreference<SwitchPreferenceCompat>("chen_home_handle_anim")?.isChecked == true
            autoTransparentOnHome?.isEnabled = findPreference<SwitchPreferenceCompat>("chen_home_handle_anim")?.isChecked == true && autoTransparent?.isChecked == true
            findPreference<SwitchPreferenceCompat>("chen_home_handle_anim")?.setOnPreferenceChangeListener { _, newValue ->
                yValSeekBar?.isEnabled = newValue as Boolean
                animTurboMode?.isEnabled = newValue as Boolean
                immersionMode?.isEnabled = newValue as Boolean
                autoTransparent?.isEnabled = newValue as Boolean
                autoTransparentOnHome?.isEnabled = autoTransparent?.isChecked == true && newValue == true
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.android.systemui")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }
            bindAnimationSeekBarNoEditText(findPreference("home_handle_y_val"), 1)

            animTurboMode?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.android.systemui")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            immersionMode?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.android.systemui")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            autoTransparent?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                autoTransparentOnHome?.isEnabled = autoTransparent.isEnabled && newValue == true
                return@setOnPreferenceChangeListener true
            }
            autoTransparentOnHome?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceChangeListener true
            }

            val colorFadeOnCustom = findPreference<SeekBarPreference>("screen_on_color_fade_anim_val")
            val colorFadeOffCustom = findPreference<SeekBarPreference>("screen_off_color_fade_anim_val")
            val colorFadeCustom = findPreference<SwitchPreferenceCompat>("color_fade_anim_smoothly")
            if (colorFadeOffCustom != null) {
                bindScreenOnOffClickAlert(colorFadeOffCustom, R.string.screen_off_color_fade_anim_val_title)
            }
            if (colorFadeOnCustom != null) {
                bindScreenOnOffClickAlert(colorFadeOnCustom, R.string.screen_on_color_fade_anim_val_title)
            }
            findPreference<SeekBarPreference>("miui_unlock_wallpaper_anim_fade_anim_val")?.let {
                bindScreenOnOffClickAlert(
                    it, R.string.miui_unlock_wallpaper_anim_fade_anim_val_title)
            }

            if (!colorFadeCustom?.isChecked!!) {
                colorFadeOnCustom?.shouldDisableView = true
                colorFadeOffCustom?.shouldDisableView = true
                colorFadeOnCustom?.isEnabled = false
                colorFadeOffCustom?.isEnabled = false
            }
            colorFadeCustom.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                if (newValue as Boolean) {
                    colorFadeOnCustom?.isEnabled = true
                    colorFadeOffCustom?.isEnabled = true
                } else {
                    colorFadeOnCustom?.isEnabled = false
                    colorFadeOffCustom?.isEnabled = false
                }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall system_server")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("music_notification_optimize_foreground_color")?.shouldDisableView = true
            findPreference<SwitchPreferenceCompat>("music_notification_optimize_foreground_color")?.isEnabled =
                findPreference<SwitchPreferenceCompat>("music_notification_optimize")?.isChecked == true

            findPreference<SwitchPreferenceCompat>("music_notification_optimize")?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                findPreference<SwitchPreferenceCompat>("music_notification_optimize_foreground_color")?.isEnabled = newValue as Boolean
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.android.systemui")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("music_notification_optimize_foreground_color")?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.android.systemui")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("do_not_override_pending_transition")?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall system_server")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("disable_wallpaper_auto_darken")?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.miui.miwallpaper")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("use_chen_volume_animation")?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.android.systemui")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }

            findPreference<SwitchPreferenceCompat>("hide_app_icon")?.isChecked = !isLauncherIconVisible()

            findPreference<SwitchPreferenceCompat>("hide_app_icon")?.setOnPreferenceChangeListener { _, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                setLauncherIconVisible(!(newValue as Boolean))
                return@setOnPreferenceChangeListener true
            }
        }

        private val setColorFadeSettings : Runnable = Runnable {
            context?.sendBroadcast(Intent("chen.miui.extra.update.colorfade"))
        }

        private fun bindAnimationSeekBarNoEditText(preference: SeekBarPreference?, scale: Int) {
            preference?.summary = "${(preference?.value as Int).toFloat() / scale} f"
            preference.setOnPreferenceChangeListener { pref, newValue ->
                pref.summary = "${(newValue as Int).toFloat() / scale} f"
                this.context?.let { ChenUtils.performVibrateClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.android.systemui")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }
        }

        private fun bindScreenOnOffClickAlert(preference: SeekBarPreference, title: Int) {
            val mHandler = Handler(Looper.getMainLooper())

            preference.summary = "${preference.value} ms"
            preference.setOnPreferenceChangeListener { preference, newValue ->
                mHandler.removeCallbacks(setColorFadeSettings)
                preference.summary = "$newValue ms"
                this.context?.let { ChenUtils.performVibrateClick(it) }
                mHandler.postDelayed(setColorFadeSettings, 100)
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
                    .setPositiveButton(R.string.confirm
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
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
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
            if (Settings.System.getString(context?.contentResolver, "custom_mode_switch") == "true") {
                customModePerf.isChecked = true;
            }

            if (Settings.System.getString(context?.contentResolver, "custom_mode_is_chen_policy") == "true") {
                customModePerf.summaryOn = "Using Chen Custom Policy Mode"
            } else {
                customModePerf.summaryOn = "Using MIUI Official Policy Mode"
            }

            customModePerf.setOnPreferenceChangeListener { _, _ ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot_PowerKeeper, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("killall com.miui.powerkeeper")
                        ChenUtils.execShell("killall com.xiaomi.misettings")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }
        }

        private fun listenMiuiPolicyPerf(refreshRatePolicyPerf: SwitchPreferenceCompat) {
            refreshRatePolicyPerf.setOnPreferenceChangeListener { preference, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceChangeListener true
            }
        }
    }

}