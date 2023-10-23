package moe.chenxy.miuiextra.view.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import moe.chenxy.miuiextra.R
import moe.chenxy.miuiextra.utils.ChenUtils
import rikka.preference.MainSwitchPreference
import rikka.widget.mainswitchbar.OnMainSwitchChangeListener

const val MAX_EFFECT_ID = 400
class VibratorEffectRemapActivity : AppCompatActivity() {

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
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.vibrator_effect_map_title)
        // Adapt transparent navbar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = Color.TRANSPARENT

        try {
            // getSharedPreferences will hooked by LSPosed and change xml file path to /data/misc/edxp**
            // will not throw SecurityException
            getSharedPreferences("chen_vibrator_effect_settings", Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {

        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        var remapItemCategory: PreferenceCategory? = null
        var sharedPreferences: SharedPreferences? = null
        var mapList: MutableList<String>? = ArrayList()
        private val mapListName = "chen_vibrator_effect_map_list"

        private val clickItemClickListener = OnPreferenceClickListener { perf ->
            MaterialAlertDialogBuilder(this.requireContext())
                .setMessage(R.string.want_to_delete)
                .setNegativeButton("No") { _, _ -> }
                .setPositiveButton("Yes") {_, _ ->
                    val arr = keyToStr(perf.key).split(",")
                    removeRemapItem(arr[0].toInt(), arr[1].toInt())
                }
                .show()
            return@OnPreferenceClickListener true
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "chen_vibrator_effect_settings"
            sharedPreferences = preferenceManager.sharedPreferences
            mapList = sharedPreferences!!.getStringSet(mapListName, LinkedHashSet())
                ?.let { ArrayList(it) }
            setPreferencesFromResource(R.xml.vibrator_effect_mapper_perf, rootKey)

            remapItemCategory = findPreference("effect_map_category")
            val remapOptionCategory = findPreference<PreferenceCategory>("effect_map_options_category")

            val mainSwitchChangeListener = OnMainSwitchChangeListener { _, isChecked ->
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                if (!isChecked) {
                    preferenceScreen.removePreference(remapItemCategory!!)
                    preferenceScreen.removePreference(remapOptionCategory!!)
                } else {
                    preferenceScreen.addPreference(remapItemCategory!!)
                    preferenceScreen.addPreference(remapOptionCategory!!)
                    initRemapItem()
                }
            }

            findPreference<Preference>("add_new_effect_map")?.setOnPreferenceClickListener {
                val chenView = LayoutInflater.from(activity).inflate(R.layout.chen_effect_map_add_dialog, null)

                val mapBeforePicker = chenView.findViewById<NumberPicker>(R.id.chen_effect_map_from)
                val mapToPicker = chenView.findViewById<NumberPicker>(R.id.chen_effect_map_to)
                mapBeforePicker.maxValue = MAX_EFFECT_ID
                mapBeforePicker.minValue = 0

                mapToPicker.maxValue = MAX_EFFECT_ID
                mapToPicker.minValue = 0

                mapBeforePicker.setOnValueChangedListener { _, _, _ -> ChenUtils.performVibrateAnyIndex(requireContext(), mapBeforePicker.value) }
                mapToPicker.setOnValueChangedListener { _, _, _ -> ChenUtils.performVibrateAnyIndex(requireContext(), mapToPicker.value) }

                val inputDialog = MaterialAlertDialogBuilder(this.requireContext())
                inputDialog.setOnDismissListener { ChenUtils.cancelAnyVibration(requireContext()) }
                inputDialog
                    .setTitle(R.string.want_to_map_id)
                    .setView(chenView)
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        if (mapBeforePicker.value == mapToPicker.value) {
                            Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), "Before and After are the same!! ignored!!", Snackbar.LENGTH_LONG)
                                .show()
                            return@setPositiveButton
                        }
                        addNewRemapItem(mapBeforePicker.value, mapToPicker.value)
                        Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout), "Mapped ${mapBeforePicker.value} to ${mapToPicker.value}", Snackbar.LENGTH_LONG)
                            .setAction("Try it!") {
                                ChenUtils.performVibrateAnyIndex(it.context, mapBeforePicker.value)
                            }
                            .show()
                        this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                    }
                inputDialog.show()
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceClickListener true
            }

            findPreference<Preference>("try_effect")?.setOnPreferenceClickListener {
                val inputDialog = MaterialAlertDialogBuilder(this.requireContext())
                val chenView = LayoutInflater.from(activity).inflate(R.layout.chen_haptic_effect_try_layout, null)
                val numberPicker = chenView.findViewById<NumberPicker>(R.id.effect_picker)
                val tryBtn = chenView.findViewById<MaterialButton>(R.id.try_effect_btn)

                numberPicker.maxValue = MAX_EFFECT_ID // set the max id to 400, the id will no t over than 400 if mi isn't crazy
                numberPicker.minValue = 0

                numberPicker.setOnValueChangedListener { _, _, _ -> ChenUtils.performVibrateClick(requireContext()) }

                tryBtn.setOnClickListener {
                    ChenUtils.performVibrateAnyIndex(requireContext(), numberPicker.value)
                }

                inputDialog
                    .setTitle(R.string.try_effect)
                    .setView(chenView)
                    .setPositiveButton(R.string.confirm) { _, _ -> }
                inputDialog.show()
                return@setOnPreferenceClickListener true
            }

            initRemapItem()
            if (!findPreference<MainSwitchPreference>("enable_vibrator_effect_remap")!!.isChecked) {
                preferenceScreen.removePreference(remapItemCategory!!)
                preferenceScreen.removePreference(remapOptionCategory!!)
            }

            findPreference<MainSwitchPreference>("enable_vibrator_effect_remap")?.addOnSwitchChangeListener(mainSwitchChangeListener)
        }

        private fun addNewRemapItem(from: Int, to: Int): Boolean {
            addNewRemapItemPreference(from, to)
            return addItemToSharedPerf("$from,$to")
        }

        private fun removeRemapItem(from: Int, to: Int) {
            mapList!!.remove("$from,$to")
            sharedPreferences?.edit()?.putStringSet(mapListName, LinkedHashSet(mapList))!!.apply()
            removeRemapItemPreference(from, to)
        }

        private fun addNewRemapItemPreference(from: Int, to: Int) {
            if (findPreference<Preference>("chen_vibrator_effect_map_${from}_to_$to") == null) {
                val tmpPerf = Preference(requireContext())
                tmpPerf.title = "$from -> $to"
                tmpPerf.key = "chen_vibrator_effect_map_${from}_to_$to"
                tmpPerf.onPreferenceClickListener = clickItemClickListener

                remapItemCategory!!.addPreference(tmpPerf)
            }
        }

        private fun keyToStr(key: String): String {
            val newKey = key.replace("chen_vibrator_effect_map_", "")
            return newKey.replace("_to_", ",")
        }

        private fun removeRemapItemPreference(from: Int, to: Int) {
            findPreference<Preference>("chen_vibrator_effect_map_${from}_to_$to")?.let {
                remapItemCategory!!.removePreference(
                    it
                )
            }
        }

        private fun addItemToSharedPerf(item: String): Boolean {
            if (mapList!!.contains(item)) return false
            mapList!!.add(item)
            sharedPreferences?.edit()?.putStringSet(mapListName, LinkedHashSet(mapList))!!.apply()
            return true
        }

        private fun initRemapItem() {
            for (item in mapList!!) {
                val strArr = item.split(",")
                addNewRemapItemPreference(strArr[0].toInt(), strArr[1].toInt())
            }
        }
    }
}