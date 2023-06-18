package moe.chenxy.miuiextra.view.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.chenxy.miuiextra.R
import moe.chenxy.miuiextra.utils.ChenUtils
import rikka.preference.MainSwitchPreference
import rikka.widget.mainswitchbar.OnMainSwitchChangeListener

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


        try {
            // getSharedPreferences will hooked by LSPosed and change xml file path to /data/misc/edxp**
            // will not throw SecurityException
            getSharedPreferences("chen_vibrator_effect_settings", Context.MODE_WORLD_READABLE)
        } catch (exception: SecurityException) {
            AlertDialog.Builder(this)
                .setMessage("Unsupported Xposed detected! Please install and active LSPosed!")
                .setPositiveButton(
                    "OK"
                ) { _: DialogInterface?, _: Int -> finish() }
                .setNegativeButton("Ignore", null)
                .show()
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
                val mapBeforeEditText = chenView.findViewById<EditText>(R.id.chen_effect_map_before)
                val mapToEditText = chenView.findViewById<EditText>(R.id.chen_effect_map_to)
                mapBeforeEditText.inputType = (InputType.TYPE_CLASS_NUMBER)
                mapToEditText.inputType = (InputType.TYPE_CLASS_NUMBER)

                val inputDialog = MaterialAlertDialogBuilder(this.requireContext())
                inputDialog
                    .setTitle(R.string.want_to_map_id)
                    .setView(chenView)
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        val mapBeforeText = mapBeforeEditText.text.toString()
                        val mapToText = mapToEditText.text.toString()
                        var mapBeforeInt = 0
                        var mapToInt = 0

                        if (mapBeforeText.isNotEmpty() && mapToText.isNotEmpty()) {
                            mapBeforeInt = mapBeforeText.toInt()
                            mapToInt = mapToText.toInt()
                        }

                        if (mapToInt < 0 && mapBeforeInt < 0) {
                            Toast.makeText(
                                this.context,
                                "Input Value Invalid!!",
                                Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            addNewRemapItem(mapBeforeInt, mapToInt)
                        }
                    }
                inputDialog.show()
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
                return@setOnPreferenceClickListener true
            }

            findPreference<Preference>("try_effect")?.setOnPreferenceClickListener {
                val inputDialog = MaterialAlertDialogBuilder(this.requireContext())
                val editText = EditText(context)
                inputDialog
                    .setTitle("ID")
                    .setView(editText)
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        val idText = editText.text.toString()
                        var idInt = 0

                        if (idText.isNotEmpty()) {
                            idInt = idText.toInt()
                        }

                        if (idInt < 0) {
                            Toast.makeText(
                                this.context,
                                "Input Value Invalid!!",
                                Toast.LENGTH_SHORT
                            ).show();
                        } else {
                            ChenUtils.performVibrateAnyIndex(requireContext(), idInt)
                        }
                    }
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