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
import org.jmoyer.NotificationPlus.NotificationPlusPreferences;
import android.util.Log;

public class BootServiceStarter extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		if (NotificationPlusPreferences.serviceEnabled(context))
			NotificationPlusService.start(context);
		else
			Log.d("NotificationPlus", "Service not enabled.");
	}

}
