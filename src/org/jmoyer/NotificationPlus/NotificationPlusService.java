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
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.os.Vibrator;

/**
 * NotificationPlusService:
 * 
 * This service runs in the foreground.  It keeps as state a boolean representing
 * whether the indicator should currently be signaled.  It uses timers to schedule
 * the work of signaling the user.  It also supports an asynchronous notification
 * mechanism to receive any notifications that should trigger the signaling of the
 * user.
 * 
 * TODO: add in a battery state monitor, and turn off the notifications if battery level too low.
 * TODO: support ignored incoming calls
 * TODO: fix clear all delete intent so it works
 * TODO: allow more flexible setting of the intervals
 * TODO: google voice support
 * 
 * GoogleVoice has a way to get a formatted xml or json response for unread messages, using this URL:
 * https://www.google.com/voice/inbox/recent/unread/
 * empty looks like:
 * <?xml version="1.0" encoding="UTF-8"?>
<response>
  <json><![CDATA[{"messages":{},"totalSize":0,"unreadCounts":{"all":0,"inbox":0,"missed":0,"placed":0,"received":0,"sms":0,"starred":0,"trash":2,"unread":0,"voicemail":0},"resultsPerPage":10}]]></json>
  <html><![CDATA[

<div class="gc-inbox-btm-paging"></div>

  <div class="gc-inbox-no-items">No unread items in your inbox.</div>
  

<div class="gc-footer-inbox">
  

<div class="gc-user-tip"><div class="goog-inline-block"><b>Tip:</b>
  Press the "c" key to start a call. Press the "t" key to start a text message.
  <a target="_blank" href="http://www.google.com/support/voice/bin/answer.py?hl=en&answer=117493&ctx=tip">Learn more</a>
  
</div></div>

  <div class="gc-footer">
  &copy;2010 Google
  - <a target="_blank" href="http://www.google.com/googlevoice/legal-notices.html">Terms</a>
  - <a target="_blank" href="http://googlevoiceblog.blogspot.com/">Blog</a>
  - <a target="_blank" href="http://www.google.com">Google Home</a>
  
</div>
</div>

<div style="display: none;">
<img alt="Callout_selected" src="/voice/resources/1487696585-callout_selected.gif"/><img alt="Tl_selected" src="/voice/resources/2635542038-tl_selected.gif"/><img alt="Top_selected" src="/voice/resources/367283939-top_selected.gif"/><img alt="Tr_selected" src="/voice/resources/2433887551-tr_selected.gif"/><img alt="Bl_selected" src="/voice/resources/3174837910-bl_selected.gif"/><img alt="Bottom_selected" src="/voice/resources/1834447710-bottom_selected.gif"/><img alt="Br_selected" src="/voice/resources/2110467394-br_selected.gif"/><img alt="Left_selected" src="/voice/resources/2503289043-left_selected.gif"/><img alt="Right_selected" src="/voice/resources/947660058-right_selected.gif"/></div>
]]></html>
</response>

 *
 * pending messages looks like:
 * <?xml version="1.0" encoding="UTF-8"?>
 * 

<response>
  <json><![CDATA[{"messages":{"e29dd4347b64828ddcfaaa1ca54fd4d43172d095":{"id":"e29dd4347b64828ddcfaaa1ca54fd4d43172d095","phoneNumber":"+16174350426","displayNumber":"(617) 435-0426","startTime":"1292780759313","displayStartDateTime":"12/19/10 12:45 PM","displayStartTime":"12:45 PM","relativeStartTime":"7 minutes ago","note":"","isRead":false,"isSpam":false,"isTrash":false,"star":false,"labels":["inbox","unread","sms","all"],"type":11,"children":""}},"totalSize":1,"unreadCounts":{"all":1,"inbox":1,"missed":0,"placed":0,"received":0,"sms":1,"starred":0,"trash":2,"unread":1,"voicemail":0},"resultsPerPage":10}]]></json>
  <html><![CDATA[

...
there's lots in there, actually.
 */
public class NotificationPlusService extends Service {

	/* Logging Tag */
	private final String TAG = "NotificationPlusService";

	private static final String DELETE_ACTION = "delete";
	private BroadcastReceiver unblankReceiver, smsReceiver, callStateReceiver, updateReceiver;
	private IntentFilter smsFilter, unblankFilter, callFilter, updateFilter;
	private Handler timerHandler;
	/* state information */
	private boolean mNotificationStatus;
	private boolean mScreenOn;
	private String previousCallState;
	/* service info */
	private final int mNotificationId = 1;
	private Notification mNotification = null;
	/* Preferences */
	private SharedPreferences.OnSharedPreferenceChangeListener prefsListener = null;
	private long mTimerInterval;
	private long mUpdateMechanisms;
	/* update mechanism flags */
	private long UPDATE_VIBRATE = 0x1;
	private long UPDATE_SOUND = 0x2;
	private long UPDATE_FLASH = 0x4;
	private Ringtone mRingtone = null;

	private Runnable mDoNotify = new Runnable() {
		   public void run() {
			   
			   if ((mUpdateMechanisms & UPDATE_VIBRATE) != 0) {
				   Vibrator mVib;
				   long[] pattern = {0, 250, 200, 250};
				   mVib = (Vibrator)(getSystemService(VIBRATOR_SERVICE));
				   mVib.vibrate(pattern, -1);
			   }
			   if ((mUpdateMechanisms & UPDATE_SOUND) != 0) {
				   // check to see if the phone is in silent mode
				   // play the default notification DEFAULT_NOTIFICATION_URI
				   //   see android.media.RingtoneManager
				   mRingtone.play();
			   }
			   Log.d(TAG, "next notification in " + mTimerInterval + " msecs");
			   timerHandler.postDelayed(mDoNotify, mTimerInterval);
		   }
	};

	private void startNotification() {
		mNotificationStatus = true;
		Log.d(TAG, "starting notification in " + mTimerInterval + " miliseconds");
		timerHandler.postDelayed(mDoNotify, mTimerInterval);
	}
	private void stopNotification() {
		mNotificationStatus = false;
		timerHandler.removeCallbacks(mDoNotify);
	}
	void updateNotificationState(boolean set, boolean ignoreScreenState) {
		if (set && mNotificationStatus != true) {
        	TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        	if (tm.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) {
        		if (ignoreScreenState || !mScreenOn) {
        			startNotification();
        		}
        	} else
        		Log.d("NotificationPlusService", "not starting notification " + mScreenOn + " " + tm.getCallState());
		} else if (!set && mNotificationStatus == true) {
			stopNotification();
		}
	}
	void updateNotificationState(boolean set) {
		updateNotificationState(set, false);
	}
	
	public static void sUpdateNotificationState(Context context, boolean set) {
		Intent newIntent = new Intent();
		newIntent.setAction("org.jmoyer.NotificationPlus.UPDATE");
		newIntent.putExtra("org.jmoyer.NotificationPlus.enable", set);
		context.sendBroadcast(newIntent);
	}

	public void onCreate() {
	}

	private void getPreferences(SharedPreferences prefs) {
		Context context = getBaseContext();

        String freq = prefs.getString(getString(R.string.notification_frequency_key), getString(R.string.frequency_1m_value));
        try {
        	mTimerInterval = Long.parseLong(freq.trim()) * 1000;
        } catch (NumberFormatException nfe) {
        	Log.w(TAG, "Invalid timer interval: " + freq);
        	mTimerInterval = 0;
        }
        if (mTimerInterval <= 0)
        		mTimerInterval = 60000;
        mUpdateMechanisms = 0;
        if (prefs.getBoolean(getString(R.string.use_system_notification_key), false)) {
        	mUpdateMechanisms |= UPDATE_SOUND;
        	Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(context,
        																  RingtoneManager.TYPE_NOTIFICATION);
        	mRingtone = RingtoneManager.getRingtone(context, ringtoneUri);
        }
        if (prefs.getBoolean(getString(R.string.use_vibrator_key), true)) {
        	mUpdateMechanisms |= UPDATE_VIBRATE;
        }
        if (prefs.getBoolean(getString(R.string.use_flash_key), false)) {
        	mUpdateMechanisms |= UPDATE_FLASH;
        }
        if (mUpdateMechanisms == 0)
        	Log.d(TAG, "no notification mechanisms selected!");
        if (!prefs.getBoolean(getString(R.string.service_enabled_key), false)) {
        	stopForeground(true);
        	stopSelf();
        }
	}

	private void watchPreferences() {
		SharedPreferences prefs = getSharedPreferences(getString(R.string.PREFS_FILE), MODE_PRIVATE);

		prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
		        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		        	getPreferences(prefs);
		        }
		};
		prefs.registerOnSharedPreferenceChangeListener(prefsListener);
	}

	private void doStartForeground() {
		int icon = R.drawable.notification;
		CharSequence tickerText = "Notification+ Started";
		long when = System.currentTimeMillis();
		Context context = getApplicationContext();
        // Create an intent triggered by clicking on the "Clear All Notifications" button
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(getBaseContext(), NotificationPlusService.class);
        deleteIntent.setAction(DELETE_ACTION);

		mNotification = new Notification(icon, tickerText, when);
		CharSequence contentTitle = "Notification+";
		CharSequence contentText = "Select to configure notifications.";
		Intent notificationIntent = new Intent(this, NotificationPlusPreferences.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		//PendingIntent mDeleteIntent = PendingIntent.
		mNotification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
		mNotification.deleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);
		startForeground(mNotificationId, mNotification);
	}

	private void setupInitialState() {
		/*
		 * Started either at boot or via launcher, which means
		 * that the screen is definitely on.
		 */
		mScreenOn = true;
		/*
		 * initial notification status is false, since we don't know anything
		 * about previous events.
		 */
		mNotificationStatus = false;

		TelephonyManager tm = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
		switch (tm.getCallState()) {
		case TelephonyManager.CALL_STATE_IDLE:
			previousCallState = TelephonyManager.EXTRA_STATE_IDLE;
			break;
		case TelephonyManager.CALL_STATE_OFFHOOK:
			previousCallState = TelephonyManager.EXTRA_STATE_OFFHOOK;
			break;
		case TelephonyManager.CALL_STATE_RINGING:
			previousCallState = TelephonyManager.EXTRA_STATE_RINGING;
			break;
		default:
			previousCallState = TelephonyManager.EXTRA_STATE_IDLE;
			break;
		}
	}

	private void logPreferences() {
		SharedPreferences prefs = getSharedPreferences(getString(R.string.PREFS_FILE), MODE_PRIVATE);
		Log.d(TAG, "enabled: " + prefs.getBoolean(getString(R.string.service_enabled_key), false));
		Log.d(TAG, "use flash: " + prefs.getBoolean(getString(R.string.use_flash_key), false));
		Log.d(TAG, "use vibrator: " + prefs.getBoolean(getString(R.string.use_vibrator_key), false));
		Log.d(TAG, "use sound: " + prefs.getBoolean(getString(R.string.use_system_notification_key), false));
		Log.d(TAG, "timer interval: " + prefs.getString(getString(R.string.notification_frequency_key), "none"));
	}

	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

		logPreferences();
		SharedPreferences prefs = getSharedPreferences(getString(R.string.PREFS_FILE), MODE_PRIVATE);

		watchPreferences();
		getPreferences(prefs);
		setupInitialState();

		timerHandler = new Handler();

		unblankReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)){
						mScreenOn = false;
						return;
				}
				mScreenOn = true;
				updateNotificationState(false);
			}
		};

		smsReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				updateNotificationState(true);
			}
		};

		callStateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String currentState = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
				if (previousCallState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
					/*
					 * OK, could be a missed call!
					 */
					if (currentState.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)){
						previousCallState = currentState;
						return;
					}
					/* OK, it went from ringing to something other than offhook: missed call. */
					previousCallState = currentState;
					updateNotificationState(true, true);
				} else {
					previousCallState = currentState;
				}
			}
		};

		updateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				boolean enable;
				if (DELETE_ACTION.equals(intent.getAction())) {
					Log.d(TAG, "got delete intent!");
					updateNotificationState(false);
					return;
				}
				Log.d(TAG, "got " + intent.getAction() + " intent");
				enable = intent.getBooleanExtra("org.jmoyer.NotificationPlus.enable", false);
				Log.d("NotificationPlusService", "update: " + enable);
				updateNotificationState(enable);
			}
		};

		smsFilter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
		registerReceiver(smsReceiver, smsFilter);
		unblankFilter = new IntentFilter();
		unblankFilter.addAction(Intent.ACTION_SCREEN_ON);
		unblankFilter.addAction(Intent.ACTION_SCREEN_OFF);
		registerReceiver(unblankReceiver, unblankFilter);
		callFilter = new IntentFilter("android.intent.action.PHONE_STATE");
		registerReceiver(callStateReceiver, callFilter);
		updateFilter = new IntentFilter("org.jmoyer.NotificationPlus.UPDATE");
		updateFilter.addAction(DELETE_ACTION);
		registerReceiver(updateReceiver, updateFilter);

		/* Finally, start the service in the foreground */
		doStartForeground();
	}

	public void onDestroy() {
		Context context = getBaseContext();
		SharedPreferences prefs = getSharedPreferences(getString(R.string.PREFS_FILE), MODE_PRIVATE);
		prefs.unregisterOnSharedPreferenceChangeListener(prefsListener);
		context.unregisterReceiver(smsReceiver);
		context.unregisterReceiver(callStateReceiver);
		context.unregisterReceiver(unblankReceiver);
		context.unregisterReceiver(updateReceiver);
	}

	public static class GoogleVoicePoller extends Service {

		@Override
		public IBinder onBind(Intent intent) {
			return null;
		}
	}
	
	public static void start(Context context) {
		context.startService(new Intent(context, NotificationPlusService.class));
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	/**
     * The IRemoteInterface is defined through IDL
     */
    private final INotificationPlusService.Stub mBinder = new INotificationPlusService.Stub() {
 		@Override
		public void iUpdateNotificationState(boolean enable)
				throws RemoteException {
 			updateNotificationState(enable);
 		}
    };
}
