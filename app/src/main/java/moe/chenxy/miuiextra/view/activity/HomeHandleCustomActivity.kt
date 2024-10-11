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
import androidx.preference.iterator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import moe.chenxy.miuiextra.R
import moe.chenxy.miuiextra.utils.ChenUtils
import rikka.preference.MainSwitchPreference

class HomeHandleCustomActivity : AppCompatActivity() {

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
        supportActionBar?.setTitle(R.string.home_handle_header)
        // Adapt transparent navbar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = Color.TRANSPARENT

        try {
            // getSharedPreferences will hooked by LSPosed and change xml file path to /data/misc/edxp**
            // will not throw SecurityException
            getSharedPreferences("chen_main_settings", Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "chen_main_settings"
            setPreferencesFromResource(R.xml.home_handle_anim_pref, rootKey)

            for (pref in preferenceScreen.iterator()) {
                if (pref is PreferenceCategory) {
                    for (pref1 in pref) {
                        if (pref1 is SeekBarPreference) {
                            pref1.title?.let {
                                bindAnimationSeekBar(
                                    pref1,
                                    it,
                                    if (pref1.key.contains("duration"))
                                        "ms"
                                    else if (pref1.key.contains("offset"))
                                        "px"
                                    else
                                        "%"
                                )
                            }
                        }
                    }
                }
                if (pref is SeekBarPreference) {
                    pref.title?.let { bindAnimationSeekBar(pref, it, if (pref.key.contains("duration")) "ms" else "%") }
                }
            }
        }

//        private fun bindAnimationSeekBarNoEditText(preference: SeekBarPreference?, scale: Int, endsWith: String) {
//            preference?.summary = "${(preference?.value as Int).toFloat() / scale} $endsWith"
//            preference.setOnPreferenceChangeListener { pref, newValue ->
//                pref.summary = "${(newValue as Int).toFloat() / scale} $endsWith"
//                ChenUtils.performVibrateClick(requireContext())
//                return@setOnPreferenceChangeListener true
//            }
//        }

        private fun bindAnimationSeekBar(preference: SeekBarPreference?, title: CharSequence, endsWith: String) {
            preference?.summary = "${(preference?.value as Int)} $endsWith"
            preference?.setOnPreferenceChangeListener { pref, newValue ->
                pref.summary = "${(newValue as Int)} $endsWith"
                this.context?.let { ChenUtils.performVibrateClick(it) }
                return@setOnPreferenceChangeListener true
            }
            preference?.setOnPreferenceClickListener {
                val inputDialog = MaterialAlertDialogBuilder(this.requireContext())
                val chenView = LayoutInflater.from(activity).inflate(R.layout.chen_edittext_dialog, null)
                val editText = chenView.findViewById<EditText>(R.id.chen_edittext_dialog_edittext)
                editText.hint = (preference?.value).toString()
                inputDialog
                    .setTitle(title)
                    .setView(chenView)
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        val inputText = editText.text.toString()
                        var inputFloat = 0
                        if (inputText.isNotEmpty()) inputFloat = inputText.toInt()
                        if (inputFloat > preference.max || inputFloat < preference.min) {
                            Toast.makeText(
                                this.context,
                                "Input Value Invalid!!",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            preference.value = inputFloat
                            preference.summary = "$inputFloat $endsWith"
                        }
                    }
                    .show()
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceClickListener true
            }
        }
    }
}