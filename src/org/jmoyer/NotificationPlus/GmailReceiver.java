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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class GmailReceiver extends BroadcastReceiver {
	INotificationPlusService mService = null;
	Context mContext;
	boolean mEnable;
	
	/**
     * Class for interacting with the main interface of the service.
     */
/*
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            mService = INotificationPlusService.Stub.asInterface(service);
            try {
                mService.iUpdateNotificationState(mEnable);
            } catch (RemoteException e) {
            	// do nothing.
            	Toast.makeText(mContext, "rpc failed: " + e.toString(), Toast.LENGTH_SHORT).show();
            }
            mContext.unbindService(mConnection);
        }
        public void onServiceDisconnected(ComponentName className) {
        	mService = null;
        	mContext = null;
        }
    };
*/
    /**
     * Executes the network requests on a separate thread.
     * 
     * @param runnable The runnable instance containing network mOperations to
     *        be executed.
     */
/*
    public static void queueNotify(final Runnable runnable) {
        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } finally {

                }
            }
        };
        t.start();
    }
*/
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		mContext = context;

		SharedPreferences prefs = context.getSharedPreferences(context.getString(R.string.PREFS_FILE), Context.MODE_PRIVATE);
		if (!prefs.getBoolean(context.getString(R.string.service_enabled_key), true))
				return;
		if (!prefs.getBoolean(context.getString(R.string.gmail_enabled_key), true))
				return;
		
		if (Intent.ACTION_PROVIDER_CHANGED.equals(action)) {
			int unreadCount = intent.getIntExtra("count", 0);
			if (unreadCount > 0) {
				mEnable = true;
			} else {
				mEnable = false;
			}
			/*
			 * Can't use the context passed in to bind to a service. Even if we
			 * spawn a background thread, the runtime can kill the full process
			 * when the broadcast receiver returns. Thus the only way to forward
			 * along the event is to publish our own notification, which seems a
			 * bit daft, or to do what the docs say, and start a service:
			 * 
			 *"This presents a problem when the response to a broadcast message
			 * is time consuming and, therefore, something that should be done
			 * in a separate thread, away from the main thread where other
			 * components of the user interface run. If onReceive() spawns the
			 * thread and then returns, the entire process, including the new
			 * thread, is judged to be inactive (unless other application
			 * components are active in the process), putting it in jeopardy of
			 * being killed. The solution to this problem is for onReceive() to
			 * start a service and let the service do the job, so the system
			 * knows that there is still active work being done in the process."
			 */
			NotificationPlusService.sUpdateNotificationState(context, mEnable);
/*			
			final Runnable runnable = new Runnable() {
	            public void run() {
	    			mContext.bindService(new Intent(INotificationPlusService.class.getName()),
	                        mConnection, Context.BIND_AUTO_CREATE);
	            }
	        };
	        queueNotify(runnable);
*/
		}
	}
}
