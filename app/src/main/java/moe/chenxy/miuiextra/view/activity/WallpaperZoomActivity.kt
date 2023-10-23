package moe.chenxy.miuiextra.view.activity

import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import moe.chenxy.miuiextra.R
import moe.chenxy.miuiextra.utils.ChenUtils
import rikka.preference.MainSwitchPreference

class WallpaperZoomActivity : AppCompatActivity() {

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
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.wallpaper_scale_title)
        // Adapt transparent navbar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = Color.TRANSPARENT

        try {
            // getSharedPreferences will hooked by LSPosed and change xml file path to /data/misc/edxp**
            // will not throw SecurityException
            getSharedPreferences("chen_wallpaper_zoom_settings", Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "chen_wallpaper_zoom_settings"
            setPreferencesFromResource(R.xml.wallpaper_zoom_preferences, rootKey)

            val wallpaperScalePerf = findPreference<SeekBarPreference>("wallpaper_scale_val")
            wallpaperScalePerf?.summary = formatWallpaperScaleVal(wallpaperScalePerf!!.value)
            wallpaperScalePerf.setOnPreferenceChangeListener { preference, newValue ->
                preference.summary = formatWallpaperScaleVal(newValue as Int)
                this.context?.let { ChenUtils.performVibrateClick(it) }
                return@setOnPreferenceChangeListener true
            }
            wallpaperScalePerf.setOnPreferenceClickListener {
                val chenView = LayoutInflater.from(activity).inflate(R.layout.chen_edittext_dialog, null)
                val editText = chenView.findViewById<EditText>(R.id.chen_edittext_dialog_edittext)
                editText.hint = formatWallpaperScaleVal(wallpaperScalePerf.value)
                editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                val inputDialog = MaterialAlertDialogBuilder(this.requireContext())
                inputDialog
                    .setTitle(R.string.wallpaper_scale_summary)
                    .setView(chenView)
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        val inputText = editText.text.toString()
                        var inputFloat: Float = 0.0f
                        if (inputText.isNotEmpty()) inputFloat = inputText.toFloat()
                        if (inputFloat > 3.0f || inputFloat <= 0) {
                            Toast.makeText(
                                this.context,
                                "Input Value Invalid!!",
                                Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            wallpaperScalePerf.value = (inputFloat * 100).toInt()
                            wallpaperScalePerf.summary = formatWallpaperScaleVal(wallpaperScalePerf.value)
                        }
                    }
                    .show()
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceClickListener true
            }

            bindAnimationSeekBar(findPreference("wallpaper_zoomIn_stiffness_val"), 100,
                R.string.wallpaper_zoomIn_stiffness_summary
            )
            bindAnimationSeekBar(findPreference("wallpaper_zoomOut_stiffness_val"), 100,
                R.string.wallpaper_zoomOut_stiffness_summary
            )
            bindAnimationSeekBar(findPreference("wallpaper_zoomOut_start_velocity_val"), 1000,
                R.string.wallpaper_zoomOut_start_velocity_summary
            )
            bindAnimationSeekBar(findPreference("wallpaper_zoomIn_start_velocity_val"), 1000,
                R.string.wallpaper_zoomIn_start_velocity_summary
            )
            bindAnimationSeekBar(findPreference("wallpaper_zoomOut_val"), 10,
                R.string.wallpaper_zoomOut_summary
            )

            val mainSwitch = findPreference<MainSwitchPreference>("enable_wallpaper_zoom_optimize")
            val category = findPreference<PreferenceCategory>("wallpaper_zoom_anim_custom_category")

            mainSwitch?.addOnSwitchChangeListener { switchView, isChecked ->
                if (!isChecked) {
                    preferenceScreen.removePreference(category!!)
                } else {
                    preferenceScreen.addPreference(category!!)
                }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout),
                    R.string.may_need_reboot_miui_home, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("am force-stop com.miui.home")
                    }
                    .show()
            }
            val autoZoomSwitch = findPreference<SwitchPreferenceCompat>("wallpaper_auto_zoom_on_lockscreen")
            autoZoomSwitch?.setOnPreferenceChangeListener { preference, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceChangeListener true
            }

            val syncWallpaperAnim = findPreference<SwitchPreferenceCompat>("sync_wallpaper_and_app_anim")
            syncWallpaperAnim?.setOnPreferenceChangeListener { preference, newValue ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout),
                    R.string.may_need_reboot_miui_home, Snackbar.LENGTH_LONG)
                    .setAction(R.string.reboot) { _ ->
                        ChenUtils.execShell("am force-stop com.miui.home")
                    }
                    .show()
                return@setOnPreferenceChangeListener true
            }
//            val zoomOutRecent = findPreference<SwitchPreferenceCompat>("no_zoom_out_when_recent_out")
//            zoomOutRecent?.setOnPreferenceChangeListener { preference, newValue ->
//                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
//                Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), R.string.may_need_reboot_miui_home, Snackbar.LENGTH_LONG)
//                    .setAction(R.string.reboot) { _ ->
//                        ChenUtils.execShell("am force-stop com.miui.home")
//                    }
//                    .show()
//                return@setOnPreferenceChangeListener true
//            }

            if (!mainSwitch?.isChecked!!) {
                preferenceScreen.removePreference(category!!)
            }
        }

        private fun formatWallpaperScaleVal(value: Int): String {
            return "" + value.toFloat() / 100 + "f"
        }

        private fun bindAnimationSeekBar(preference: SeekBarPreference?, scale: Int, title: Int) {
            preference?.summary = "${(preference?.value as Int).toFloat() / scale} f"
            preference.setOnPreferenceChangeListener { pref, newValue ->
                pref.summary = "${(newValue as Int).toFloat() / scale} f"
                this.context?.let { ChenUtils.performVibrateClick(it) }
                return@setOnPreferenceChangeListener true
            }
            preference.setOnPreferenceClickListener {
                val inputDialog = MaterialAlertDialogBuilder(this.requireContext())
                val chenView = LayoutInflater.from(activity).inflate(R.layout.chen_edittext_dialog, null)
                val editText = chenView.findViewById<EditText>(R.id.chen_edittext_dialog_edittext)
                editText.hint = (preference.value.toFloat() / scale).toString()
                inputDialog
                    .setTitle(title)
                    .setView(chenView)
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        val inputText = editText.text.toString()
                        var inputFloat = 0.0f
                        if (inputText.isNotEmpty()) inputFloat = inputText.toFloat()
                        if (inputFloat > preference.max.toFloat() / scale || inputFloat < preference.min.toFloat() / scale) {
                            Toast.makeText(
                                this.context,
                                "Input Value Invalid!!",
                                Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            preference.value = (inputFloat * scale).toInt()
                            preference.summary = "$inputFloat f"
                        }
                    }
                    .show()
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceClickListener true
            }
        }
    }
}