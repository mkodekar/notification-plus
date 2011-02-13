package org.jmoyer.NotificationPlus;

/*
Copyright (C) 2011, Jeff Moyer <phro@alum.wpi.edu>

This file is part of Notification Plus.

Notification Plus is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Notification Plus is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Notification Plus.  If not, see <http://www.gnu.org/licenses/>.
*/
import org.jmoyer.NotificationPlus.R;

import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

public class NotificationPlusPreferences extends PreferenceActivity {
	private final String TAG = "NotificationPlusPreferences";

	Preference.OnPreferenceChangeListener prefChangeListener = null;

	private void logPreferences(SharedPreferences prefs) {
		Log.d(TAG, "enabled: " + prefs.getBoolean(getString(R.string.service_enabled_key), false));
		Log.d(TAG, "use flash: " + prefs.getBoolean(getString(R.string.use_flash_key), false));
		Log.d(TAG, "use vibrator: " + prefs.getBoolean(getString(R.string.use_vibrator_key), false));
		Log.d(TAG, "use sound: " + prefs.getBoolean(getString(R.string.use_system_notification_key), false));
	}

	public static boolean serviceEnabled(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(context.getString(R.string.PREFS_FILE), MODE_PRIVATE);
		return prefs.getBoolean(context.getString(R.string.service_enabled_key), false);
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences prefs = getSharedPreferences(getString(R.string.PREFS_FILE), MODE_PRIVATE);
        logPreferences(prefs);

        if (prefs.getBoolean("@string/service_enabled_key", true))
        	NotificationPlusService.start(getBaseContext());
    }

    /*
     * Preferences can only be changed while the preference activity is in the foreground.  Thus,
     * the preference change listener is registered in onResume, and unregistered in onPause.
     */
    public void onResume() {
		super.onResume();
		configureServicePreferences();
	}

    public void onPause() {
    	super.onPause();
    	prefChangeListener = null;
    }

    private void configureServicePreferences() {
		final CheckBoxPreference enabledPreference = (CheckBoxPreference)findPreference(getString(R.string.service_enabled_key));
		prefChangeListener = new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				setServiceStarted((Boolean)newValue);
				return true;
			}
		};
		enabledPreference.setOnPreferenceChangeListener(prefChangeListener);
	}

	private void setServiceStarted(boolean start) {
		if (start)
			NotificationPlusService.start(this);
		else
			;
	}
}