//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		music notification with controls & album art
//
// $NoKeywords: $not
//===============================================================================//

package com.pgo.vinylscratcher;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

public class MusicNotification
{
	private static final int VS_MUSIC_SERVICE = 1;

	private final NotificationManager notificationManager;
	private final MusicService service;

	private RemoteViews notificationTemplate;
	private Notification notification;
	private RemoteViews expandedView;
	
	public static final String DISMISSED_ACTION = "com.pgo.vinylscratcher.notification.dismissed";
	
	// handles swipe/dismiss events
	public static class NotificationDismissedReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent != null)
			{
				if (DISMISSED_ACTION.equals(intent.getAction()))
				{
					// tell the MusicService to stop
					Intent service = new Intent(context, MusicService.class);
					service.setAction(MusicService.STOP_ACTION);
					context.startService(service);
				}
			}
		}
	}

	public MusicNotification(final MusicService service)
	{
		this.service = service;
		notification = null;
		notificationManager = (NotificationManager)service.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public void buildNotification(final String albumName, final String artistName, final String trackName, final Long albumId, final Bitmap albumArt, final boolean isPlaying)
	{
		// if the notification has already been built, don't rebuild it! just update
		if (notification != null)
		{
			updateMetadata(trackName, artistName, albumName, albumArt);
			setPlayState(isPlaying); // this will call notify() for us
			return;
		}
		
		// default notfication layout
		notificationTemplate = new RemoteViews(service.getPackageName(), R.layout.notification_template_base);
		
		// intent to send on swipe/dismissed
		Intent dismissIntent = new Intent(service, NotificationDismissedReceiver.class);
		dismissIntent.setAction(DISMISSED_ACTION);
		PendingIntent pendingDismissIntent= PendingIntent.getBroadcast(service, VS_MUSIC_SERVICE, dismissIntent, 0);

		// notification Builder
		notification = new NotificationCompat.Builder(service)
			.setSmallIcon(R.drawable.stat_notify_music)
			.setContentIntent(getPendingIntent())
			.setPriority(Notification.PRIORITY_DEFAULT)
			.setContent(notificationTemplate)
			.setDeleteIntent(pendingDismissIntent)
			.build();
		
		// control playback from the notification
		initPlaybackActions(isPlaying);
		
		// expanded notifiction style
		expandedView = new RemoteViews(service.getPackageName(), R.layout.notification_template_expanded_base);
		notification.bigContentView = expandedView;
		
		// control playback from the notification
		initExpandedPlaybackActions(isPlaying);
		
		// set up the expanded content view
		updateMetadata(trackName, artistName, albumName, albumArt);
			
		// NOTE: .startForeground will disable swipe to dismiss, but keep the service at high priority
		//service.startForeground(VS_MUSIC_SERVICE, notification);
		notificationManager.notify(VS_MUSIC_SERVICE, notification);
	}

	public void setPlayState(final boolean isPlaying)
	{
		if (notification == null || notificationManager == null) return;
		
		if (notificationTemplate != null)
		{
			notificationTemplate.setImageViewResource(R.id.notification_base_play, getPlayingDrawable(isPlaying));
		}

		if (expandedView != null)
		{
			expandedView.setImageViewResource(R.id.notification_expanded_base_play, getPlayingDrawable(isPlaying));
		}
		
		notificationManager.notify(VS_MUSIC_SERVICE, notification);
	}
	
	// the play/pause icon
	private int getPlayingDrawable(boolean isPlaying)
	{
		return isPlaying ? R.drawable.ic_media_pause : R.drawable.ic_media_play;
	}

	/**
	 * Open to the now playing screen
	 */
	private PendingIntent getPendingIntent()
	{
		Intent intent = new Intent("com.pgo.vinylscratcher.AUDIO_PLAYER");

		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("com.pgo.vinylscratcher.NOW_PLAYING", true);

		return PendingIntent.getActivity( service, 0, intent, 0 );
	}

	/**
	 * Lets the buttons in the remote view control playback in the expanded
	 * layout
	 */
	private void initExpandedPlaybackActions(boolean isPlaying)
	{
		// play and pause
		expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_play, retreivePlaybackActions(1));

		// skip tracks
		expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_next, retreivePlaybackActions(2));

		// previous tracks
		expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_previous, retreivePlaybackActions(3));

		// stop and collapse the notification
		expandedView.setOnClickPendingIntent(R.id.notification_expanded_base_collapse, retreivePlaybackActions(4));

		// update the play button image
		expandedView.setImageViewResource(R.id.notification_expanded_base_play, getPlayingDrawable(isPlaying));
	}

	/**
	 * Lets the buttons in the remote view control playback in the normal layout
	 */
	private void initPlaybackActions(boolean isPlaying)
	{
		// play and pause
		notificationTemplate.setOnClickPendingIntent(R.id.notification_base_play, retreivePlaybackActions(1));

		// skip tracks
		notificationTemplate.setOnClickPendingIntent(R.id.notification_base_next, retreivePlaybackActions(2));

		// previous tracks
		notificationTemplate.setOnClickPendingIntent(R.id.notification_base_previous, retreivePlaybackActions(3));

		// stop and collapse the notification
		//notificationTemplate.setOnClickPendingIntent(R.id.notification_base_collapse, retreivePlaybackActions(4));

		// update the play button image
		notificationTemplate.setImageViewResource(R.id.notification_base_play, getPlayingDrawable(isPlaying));
	}

	/**
	 * @param which Which {@link PendingIntent} to return
	 * @return A {@link PendingIntent} ready to control playback
	 */
	private final PendingIntent retreivePlaybackActions(final int which)
	{
		Intent action;
		PendingIntent pendingIntent;
		final ComponentName serviceName = new ComponentName(service, MusicService.class);
		
		switch (which)
		{
			case 1:
				// play and pause
				action = new Intent(MusicService.TOGGLEPAUSE_ACTION);
				action.setComponent(serviceName);
				pendingIntent = PendingIntent.getService(service, 1, action, 0);
				return pendingIntent;
			case 2:
				// skip tracks
				action = new Intent(MusicService.NEXT_ACTION);
				action.setComponent(serviceName);
				pendingIntent = PendingIntent.getService(service, 2, action, 0);
				return pendingIntent;
			case 3:
				// previous tracks
				action = new Intent(MusicService.PREVIOUS_ACTION);
				action.setComponent(serviceName);
				pendingIntent = PendingIntent.getService(service, 3, action, 0);
				return pendingIntent;
			case 4:
				// stop and collapse the notification
				action = new Intent(MusicService.STOP_ACTION);
				action.setComponent(serviceName);
				pendingIntent = PendingIntent.getService(service, 4, action, 0);
				return pendingIntent;
		}

		return null;
	}
	
	private void updateMetadata(final String trackName, final String artistName, final String albumName, final Bitmap albumArt)
	{
		if (expandedView != null)
		{
			// track name (line one)
			expandedView.setTextViewText(R.id.notification_expanded_base_line_one, trackName);
			// album name (line two)
			expandedView.setTextViewText(R.id.notification_expanded_base_line_two, albumName);
			// artist name (line three)
			expandedView.setTextViewText(R.id.notification_expanded_base_line_three, artistName);
			// album art
			expandedView.setImageViewBitmap(R.id.notification_expanded_base_image, albumArt);
		}
		
		if (notificationTemplate != null)
		{
			// track name (line one)
			notificationTemplate.setTextViewText(R.id.notification_base_line_one, trackName);
			// artist name (line two)
			notificationTemplate.setTextViewText(R.id.notification_base_line_two, artistName);
			// album art
			notificationTemplate.setImageViewBitmap(R.id.notification_base_image, albumArt);
		}
	}
	
	public void removeNotification()
	{
		service.stopForeground(true);
		notificationManager.cancel(VS_MUSIC_SERVICE);
		notification = null;
	}
}
