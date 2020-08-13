//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		nice wrapper around bullshit RemoteControlClient
//
// $NoKeywords: $lockctrl
//===============================================================================//

package com.pgo.vinylscratcher;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;

public class LockscreenControls
{
	private RemoteControlClient rcc;
	private ComponentName mediaButtonReceiver;
	int lastState;
	float playbackspeed;
	
	public LockscreenControls(AudioManager audioManager, String packageName, Context applicationContext)
	{
		// create and register the media button receiver
		mediaButtonReceiver = new ComponentName(packageName, LockscreenButtonIntentReceiver.class.getName());
		audioManager.registerMediaButtonEventReceiver(mediaButtonReceiver);
		
		// create the pendingIntent for the RemoteControlClient
		Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
		mediaButtonIntent.setComponent(mediaButtonReceiver);
		PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(applicationContext, 0, mediaButtonIntent, 0);
		
		// create and register the now buildable RemoteControlClient
		rcc = new RemoteControlClient(mediaPendingIntent);
		audioManager.registerRemoteControlClient(rcc);
		
		int flags = RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
			| RemoteControlClient.FLAG_KEY_MEDIA_NEXT
			| RemoteControlClient.FLAG_KEY_MEDIA_PLAY
			| RemoteControlClient.FLAG_KEY_MEDIA_PAUSE
			| RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
			| RemoteControlClient.FLAG_KEY_MEDIA_STOP;

		// set flags and set playing (must set playing now, else it won't show up later)
		rcc.setTransportControlFlags(flags);
		rcc.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
		
		lastState = RemoteControlClient.PLAYSTATE_PAUSED;
		playbackspeed = 1.0f;
	}
	
	// seekbar
	public void setPlaybackSpeed(float playbackspeed)
	{
		this.playbackspeed = playbackspeed;
	}
	
	public void setMediaPosition(long timeInMs)
	{
		rcc.setPlaybackState(lastState, timeInMs, playbackspeed);
	}
	
	// metadata
	public void setMetadata(String title, String album, String artist, String composer, String genre, int year, long duration)
	{
		rcc.editMetadata(true)
			.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, title)
			.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, artist)
			.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, album)
			.putString(MediaMetadataRetriever.METADATA_KEY_COMPOSER, composer)
			.putString(MediaMetadataRetriever.METADATA_KEY_GENRE, genre)
			.putLong(MediaMetadataRetriever.METADATA_KEY_YEAR, (long) year)
			.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, duration)
			// TODO:
			//.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, albumArt)
		.apply();
	}
	
	// states
	public void setPlaying()
	{
		rcc.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
		lastState = RemoteControlClient.PLAYSTATE_PLAYING;
	}
	public void setPaused()
	{
		rcc.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
		lastState = RemoteControlClient.PLAYSTATE_PAUSED;
	}
	public void setStopped()
	{
		rcc.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
		lastState = RemoteControlClient.PLAYSTATE_STOPPED;
	}
	public void setBuffering()
	{
		rcc.setPlaybackState(RemoteControlClient.PLAYSTATE_BUFFERING);
		lastState = RemoteControlClient.PLAYSTATE_BUFFERING;
	}
	public void setSkippingForwards()
	{
		rcc.setPlaybackState(RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS);
		lastState = RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS;
	}
	public void setSkippingBackwards()
	{
		rcc.setPlaybackState(RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS);
		lastState = RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS;
	}
}
