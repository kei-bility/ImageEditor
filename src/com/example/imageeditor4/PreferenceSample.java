package com.example.imageeditor4;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class PreferenceSample extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefsFragment()).commit();
	}

	public static class PrefsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
		@Override
		public void onCreate(Bundle savedInstancestate) {
			super.onCreate(savedInstancestate);
			addPreferencesFromResource(R.xml.preference);
		}

		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key){
			final Preference preference = getPreferenceScreen().findPreference(key);
			if (preference instanceof ListPreference) {
				final ListPreference listPreference = (ListPreference)preference;
				final CharSequence entry = listPreference.getEntry();
				listPreference.setSummary(entry == null ? "100" : entry);
			}
		}
		@Override
		public void onResume(){
			super.onResume();
			final ListPreference list = (ListPreference)getPreferenceScreen().findPreference("list_preference");
			final CharSequence entry = list.getEntry();
			list.setSummary(entry == null ? "100" : entry);
			getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		}
		@Override
		public void onPause(){
			super.onPause();
			getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		}
	}
}
