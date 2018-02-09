package com.simples.j.whereami

import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.*
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import java.io.File

/**
 * A [PreferenceActivity] that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 *
 * See [Android Design: Settings](http://developer.android.com/design/patterns/settings.html)
 * for design guidelines and the [Settings API Guide](http://developer.android.com/guide/topics/ui/settings.html)
 * for more information on developing a Settings UI.
 */
class SettingsActivity : AppCompatPreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupActionBar()

        fragmentManager.beginTransaction().replace(android.R.id.content, GeneralPreferenceFragment()).commit()
    }

    /**
     * Set up the [android.app.ActionBar], if the API is available.
     */
    private fun setupActionBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    /**
     * {@inheritDoc}
     */
    override fun onIsMultiPane(): Boolean {
        return isXLargeTablet(this)
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    override fun isValidFragment(fragmentName: String): Boolean {
        return PreferenceFragment::class.java.name == fragmentName
                || GeneralPreferenceFragment::class.java.name == fragmentName
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    class GeneralPreferenceFragment : PreferenceFragment() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            addPreferencesFromResource(R.xml.pref_general)
            setHasOptionsMenu(true)

            val pName = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName

            val version = findPreference(resources.getString(R.string.pref_version_id))
            version.summary = pName
            version.setOnPreferenceClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}")))
                }
                catch (e: ActivityNotFoundException) {
                    Log.i(activity.packageName, "Unable to go to play store.")
                }
                true
            }

            val clearAll = findPreference(resources.getString(R.string.pref_clear_all_id)).setOnPreferenceClickListener {
                val dialog = AlertDialog.Builder(activity)
                        .setTitle(resources.getString(R.string.clear_all_title))
                        .setMessage(resources.getString(R.string.clear_all_message))
                        .setPositiveButton(resources.getString(R.string.confirm), { _, _ ->
                            Toast.makeText(activity, resources.getString(R.string.clear_all_toast), Toast.LENGTH_SHORT).show()
                            activity.setResult(MapActivity.REQUEST_CLEAR_ALL)
                        })
                        .setNegativeButton(resources.getString(R.string.cancel), { _, _ -> })
                dialog.create().show()
                true
            }

            val locationPath = findPreference(resources.getString(R.string.pref_location_id))
            locationPath.summary = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), activity.getString(R.string.save_file_name)).path
            locationPath.setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                val uri = Uri.fromFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
                intent.setDataAndType(uri, "resource/folder")
                if(intent.resolveActivity(activity.packageManager) != null) {
                    startActivity(Intent.createChooser(intent, activity.getString(R.string.open_folder)))
                }
                true
            }

            findPreference(resources.getString(R.string.pref_github_id)).setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.github_url)))
                startActivity(intent)
                true
            }

            bindPreferenceSummaryToValue(findPreference(resources.getString(R.string.pref_distance_action_id)))
            bindPreferenceSummaryToValue(findPreference(resources.getString(R.string.pref_area_action_id)))
//            findPreference(resources.getString(R.string.pref_tracking_id)).onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
//                if(value is Boolean) {
//                    if(value) activity.startService(Intent(activity, TrackingService::class.java))
//                    else activity.stopService(Intent(activity, TrackingService::class.java))
//                }
//                true
//            }
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            val id = item.itemId
            if (id == android.R.id.home) {
                activity.finish()
                return true
            }
            return super.onOptionsItemSelected(item)
        }

        private fun bindPreferenceSummaryToValue(preference: Preference) {
            // Set the listener to watch for value changes.
            preference.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener

            // Trigger the listener immediately with the preference's
            // current value.
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.context)
                            .getString(preference.key, ""))
        }

    }

    companion object {

        private val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
            val stringValue = value.toString()

            if (preference is ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                val listPreference = preference
                val index = listPreference.findIndexOfValue(stringValue)

                // Set the summary to reflect the new value.
                preference.summary = listPreference.entries[index]

            }
            else {
                preference.summary = stringValue
            }
            true
        }

        private fun isXLargeTablet(context: Context): Boolean {
            return context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK >= Configuration.SCREENLAYOUT_SIZE_XLARGE
        }

    }
}
