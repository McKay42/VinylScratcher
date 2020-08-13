//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		lockscreen button press receiver
//
// $NoKeywords: $lockbtn
//===============================================================================//

package com.pgo.vinylscratcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class LockscreenButtonIntentReceiver extends BroadcastReceiver
{
	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction()))
		{
			KeyEvent event = (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			
			if (event.getAction() == KeyEvent.ACTION_UP)
			{
				// create intent
				Intent service = new Intent(context, MusicService.class);
				
				switch (event.getKeyCode())
				{
				case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
					service.setAction(MusicService.TOGGLEPAUSE_ACTION);
					context.startService(service);
					break;
				case KeyEvent.KEYCODE_MEDIA_NEXT:
					service.setAction(MusicService.NEXT_ACTION);
					context.startService(service);
					break;
				case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
					service.setAction(MusicService.PREVIOUS_ACTION);
					context.startService(service);
					break;
				}
			}
		}
	}
}