//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		background music service
//
// $NoKeywords: $mserv
//===============================================================================//

package com.pgo.vinylscratcher;

import java.io.File;
import java.util.ArrayList;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.AudioManager;

public class MusicService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener
{
	private final IBinder musicBind = new MusicBinder();
	private MusicServiceListener listener;
	
	private AudioManager audioManager;
	private MediaPlayer player;
	
	private int currentSongIndex;
	private ArrayList<String> songList;
	private Song currentSong;
	
	private boolean scheduledBlockNextPlay;
	private int failedPlayTries;
	private NoisyAudioStreamReceiver noisyListener;
	private IntentFilter noisyFilter;
	
	// lockscreen controls
	private LockscreenControls lockscreenControls;
	
	// notifications
	private MusicNotification notification;



	//****************//
	//	   EVENTS	  //
	//****************//
	
	public static final String PLAYSTATE_CHANGED = "com.pgo.vinylscratcher.playstatechanged";
	public static final String POSITION_CHANGED = "com.pgo.vinylscratcher.positionchanged";
	public static final String META_CHANGED = "com.pgo.vinylscratcher.metachanged";
	public static final String QUEUE_CHANGED = "com.pgo.vinylscratcher.queuechanged";
	public static final String REPEATMODE_CHANGED = "com.pgo.vinylscratcher.repeatmodechanged";
	public static final String SHUFFLEMODE_CHANGED = "com.pgo.vinylscratcher.shufflemodechanged";
	
	public static final String TOGGLEPAUSE_ACTION = "com.pgo.vinylscratcher.togglepause";
	public static final String STOP_ACTION = "com.pgo.vinylscratcher.stop";
	public static final String NEXT_ACTION = "com.pgo.vinylscratcher.next";
	public static final String PREVIOUS_ACTION = "com.pgo.vinylscratcher.previous";


	
	// handle audio focus changes
	private OnAudioFocusChangeListener audioFocusChangeListener = new OnAudioFocusChangeListener()
	{
		private static final float duckPercent = 0.25f;
		
		private float volumeBackup = 0.5f;
		private boolean restoreBackup = false;
		private boolean shouldResumePlayback = false;
		
		@Override
		public void onAudioFocusChange(int focusChange)
		{
			if (focusChange == AudioManager.AUDIOFOCUS_LOSS)
			{
				if (isPlaying())
					shouldResumePlayback = true;
				
				if (focusChange != AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
				{
					restoreBackup = false;
					stop();
				}
				else
				{
					restoreBackup = true;
					volumeBackup = (float)audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / (float)audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

					if (Config.DEBUG)
						System.out.println("current volume: "+volumeBackup);

					player.setVolume( (duckPercent*volumeBackup), (duckPercent*volumeBackup) );
				}
			}
			else
			{
				if (restoreBackup)
				{
					restoreBackup = false;

					if (Config.DEBUG)
						System.out.println("restoring volume...");

					player.setVolume(volumeBackup, volumeBackup);
				}
			
				// continue playing
				if (shouldResumePlayback)
				{
					shouldResumePlayback = false;
					pause();
				}
			}

			if (Config.DEBUG)
				System.out.println("Audio focus changed to " + focusChange + "!");
		}
	};
	
	// handle headphone plug/unplugging
	private class NoisyAudioStreamReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
			{
				stopTemporarily();
			}
		}
	}
	


	// returns the music service
	public class MusicBinder extends Binder
	{
		MusicService getService()
		{
			return MusicService.this;
		}
	}
	
	// on creation of the service, initialize everything
	@Override
	public void onCreate()
	{
		super.onCreate();

		failedPlayTries = 0;
		currentSongIndex = -1;
		scheduledBlockNextPlay = false;
		currentSong = null;
		listener = null;
		
		songList = new ArrayList<String>();
		audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		notification = new MusicNotification(this);
		lockscreenControls = new LockscreenControls(audioManager, getPackageName(), this);
		
		noisyListener = new NoisyAudioStreamReceiver();
		noisyFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		
		// initialize the intent filter and each action
		final IntentFilter filter = new IntentFilter();
		{
			filter.addAction(TOGGLEPAUSE_ACTION);
			filter.addAction(STOP_ACTION);
			filter.addAction(NEXT_ACTION);
			filter.addAction(PREVIOUS_ACTION);
		}
		
		player = new MediaPlayer();

		// set all vars of MediaPlayer and initialize lockscreen controls
		{
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);

			player.setOnPreparedListener(this);
			player.setOnCompletionListener(this);
			player.setOnErrorListener(this);
		}
	}



	private boolean play(boolean tryNextIfFailed)
	{
		if (player == null)
		{
			Log.e(AUDIO_SERVICE, "play() : player == null!!!");
			return false;
		}
		if (songList.size() < 1 || currentSongIndex < 0 || currentSongIndex >= songList.size())
		{
			if (Config.DEBUG)
				System.out.println("currentSong = " + currentSong + ", songlistsize = " + songList.size() + ", currenSongIndex = " + currentSongIndex);

			return false;
		}
		
		// fill currentSong
		if (!updateSongMetadata(songList.get(currentSongIndex)))
		{
			return tryNextIfFailed ? tryPlayNextSong() : false;
		}
		
		// first, reset the player
		player.reset();
		try
		{
			// set data source and preload
			try
			{
				player.setDataSource(currentSong.getPath());
			}
			catch (Exception e)
			{
				return tryNextIfFailed ? tryPlayNextSong() : false;
			}
			
			player.prepareAsync();
			
			// try getting audio focus
			boolean shouldPlay = false;
			int result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
			if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
				shouldPlay = true;

			// update notification and lockscreen
			updateNotification(shouldPlay);
			lockscreenControls.setPlaying();
			
			// if we are here then everything worked, so reset the fail counter, also update length
			failedPlayTries = 0;
			
			// register the noisy filter
			registerReceiver(noisyListener, noisyFilter);
			
			// notify listener
			if (listener != null)
			{
				listener.onPlaystateChanged(true);
				listener.onTrackChange(currentSong.getPath());
			}
		}
		catch (Exception e)
		{
			currentSong = null;
			Log.e(AUDIO_SERVICE, "Error setting data source", e);
			
			// notify listener
			if (listener != null)
				listener.onPlaystateChanged(false);
			
			return false;
		}
		
		return true;
	}
	
	private boolean tryPlayNextSong()
	{
		// couldn't play the file, try the next one
		if (failedPlayTries < songList.size())
		{
			failedPlayTries++;
			return next();
		}
		return false;
	}
	
	private boolean tryPlayPreviousSong()
	{
		// couldn't play the file, try the next one
		if (failedPlayTries < songList.size())
		{
			failedPlayTries++;
			return previous();
		}
		return false;
	}

	public void pause()
	{
		if (!isReady())
		{
			Log.e(AUDIO_SERVICE, "pause() : !isReady()!!!");
			return;
		}
		if (currentSongIndex < 0 || currentSongIndex >= songList.size())
			return;
		
		if (player.isPlaying())
		{
			player.pause();
			
			// update notification and lockscreen
			updateNotification(false);
			lockscreenControls.setPaused();
			
			// remove noisy listener
			unregisterReceiver(noisyListener);
			
			// notify listener
			if (listener != null)
				listener.onPlaystateChanged(false);
		}
		else
		{
			player.start();
			
			// update notification and lockscreen
			updateNotification(true);
			lockscreenControls.setPlaying();
			
			// add noisy listener again
			registerReceiver(noisyListener, noisyFilter);
			
			// notify listener
			if (listener != null)
				listener.onPlaystateChanged(true);
		}
	}
	
	private void stop()
	{
		if (player == null)
		{
			Log.e(AUDIO_SERVICE, "stop() : player == null!!!");
			return;
		}
		
		// stop the music
		if (player.isPlaying())
			player.pause();
		
		// remove notification and lockscreen controls
		notification.removeNotification();
		lockscreenControls.setPaused();
		
		// notify listener
		if (listener != null)
			listener.onPlaystateChanged(false);
	}
	
	private void stopTemporarily()
	{
		if (player == null)
		{
			Log.e(AUDIO_SERVICE, "stop() : player == null!!!");
			return;
		}
		
		// stop the music
		if (player.isPlaying())
			player.pause();
		
		lockscreenControls.setPaused();
		notification.setPlayState(false);
		
		// notify listener
		if (listener != null)
			listener.onPlaystateChanged(false);
	}
	
	public boolean next()
	{
		if (!isReady())
		{
			Log.e(AUDIO_SERVICE, "next() : !isReady()!!!");
			return false;
		}
		
		// increase index
		currentSongIndex++;
		if (currentSongIndex >= songList.size())
		{
			currentSongIndex = 0;
		}

		// and play it
		return play(true);
	}
	
	public boolean previous()
	{
		if (!isReady())
		{
			Log.e(AUDIO_SERVICE, "previous() : !isReady()!!!");
			return false;
		}
		
		// decrease index
		currentSongIndex--;
		if (currentSongIndex < 0)
		{
			currentSongIndex = songList.size() - 1;
		}
		
		// and play it
		return !play(false) ? tryPlayPreviousSong() : true;
	}
	
	public void seekTo(int ms)
	{
		if (!isReady())
			return;
		
		player.seekTo(ms);
	}


	
	//****************//
	//	   UPDATE	  //
	//****************//
	
	private void updateNotification(boolean shouldBePlaying)
	{
		notification.buildNotification(getAlbum(), getArtist(), getTitle(), null, null, isPlaying() || shouldBePlaying);
	}
	
	private boolean updateSongMetadata(String path)
	{
		// build retriever
		File curFile = new File(path);
		MediaMetadataRetriever mediaMetadataRetriever = (MediaMetadataRetriever) new MediaMetadataRetriever();
		Uri uri = (Uri)Uri.fromFile(curFile);
		
		// try loading the audio file's metadata; any invalid file which isn't an audio file will throw an exception here
		try
		{
			mediaMetadataRetriever.setDataSource(this, uri);
		}
		catch (Exception e)
		{
			Log.e(AUDIO_SERVICE, "Tried to play invalid audio file.");
			return false;
		}
		
		// get everything
		String title = (String) mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
		String album = (String) mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
		String artist = (String) mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
		String composer = (String) mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
		String genre = (String) mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
		int year = 0;
		try
		{
			year = Integer.valueOf((String) mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR));
		}
		catch (Exception e) {}
		
		currentSong = new Song(title, album, artist, composer, genre, year, path, curFile.getName());
		return true;
	}
	
	
	
	//****************//
	//		SET		  //
	//****************//
	
	public boolean setSongAndPlay(String path, ArrayList<String> songList)
	{
		// reset the failed tries and the current song index
		this.songList = songList;
		if (updateSongMetadata(path))
		{
			failedPlayTries = 0;
			currentSongIndex = -1;
			
			for (int i=0; i<songList.size(); i++)
			{
				// only play it if its actually in the list
				if (songList.get(i).equals(currentSong.getPath()))
				{
					currentSongIndex = i;
					break;
				}
			}
		}
		else
		{
			failedPlayTries++;
			
			for (int i=0; i<songList.size(); i++)
			{
				// only play it if its actually in the list
				if (songList.get(i).equals(path))
				{
					currentSongIndex = i;
					break;
				}
			}
		}
		
		return play(true);
	}
	
	public void setSongWithoutAutoplay(String path, ArrayList<String> songList)
	{
		// reset the failed tries and the current song index
		this.songList = songList;
		if (updateSongMetadata(path))
		{
			failedPlayTries = 0;
			currentSongIndex = -1;
			
			for (int i=0; i<songList.size(); i++)
			{
				// only play it if its actually in the list
				if (songList.get(i).equals(currentSong.getPath()))
				{
					currentSongIndex = i;
					break;
				}
			}
		}
		else
		{
			failedPlayTries++;
			
			for (int i=0; i<songList.size(); i++)
			{
				// only play it if its actually in the list
				if (songList.get(i).equals(path))
				{
					currentSongIndex = i;
					break;
				}
			}
		}

		// NOTE: this has sideeffects if multiple calls setSongWithoutAutoplay() and setSong() (because of async onPrepared())
		scheduledBlockNextPlay = true;
		play(false);
	}
	
	public void setListener(MusicServiceListener l)
	{
		listener = l;
	}
	


	//****************//
	//		GET		  //
	//****************//

	public String getTitle()
	{
		return currentSong != null ? (currentSong.getTitle() == null ? currentSong.getFilename() : currentSong.getTitle()) : "<null>";
	}
	
	public String getAlbum()
	{
		return currentSong != null ? currentSong.getAlbum() : "<null>";
	}
	
	public String getArtist()
	{
		return currentSong != null ? currentSong.getArtist() : "<null>";
	}
	
	public String getComposer()
	{
		return currentSong != null ? currentSong.getComposer() : "<null>";
	}
	
	public String getGenre()
	{
		return currentSong != null ? currentSong.getGenre() : "<null>";
	}
	
	public int getYear()
	{
		return currentSong != null ? currentSong.getYear() : -1;
	}
	
	public int getDuration()
	{
		return isReady() ? player.getDuration() : 0;
	}
	
	public int getPosition()
	{
		return isPlaying() ? player.getCurrentPosition() : 0;
	}


	
	public boolean isPlaying()
	{
		return isReady() && player.isPlaying();
	}
	
	public boolean isReady()
	{
		return player != null;
	}
	
	
	
	//***************************//
	//	   MEDIAPLAYER EVENTS    //
	//***************************//
	
	@Override
	public void onPrepared(MediaPlayer mp)
	{
		if (scheduledBlockNextPlay)
		{
			scheduledBlockNextPlay = false;
			
			// HACKHACK: dirty copy-paste, also just in general a bad solution
			// update notification and lockscreen
			updateNotification(false);
			lockscreenControls.setPaused();
			
			// remove noisy listener
			unregisterReceiver(noisyListener);
			
			// notify listener
			if (listener != null)
				listener.onPlaystateChanged(false);
			
			return; // nothing more to do here
		}
		
		mp.start();
		
		// sound should really be playing by now, enable and update lockscreen controls
		lockscreenControls.setPlaying();
		lockscreenControls.setMetadata(getTitle(), getAlbum(), getArtist(), getComposer(), getGenre(), getYear(), getDuration());
	}

	@Override
	public void onCompletion(MediaPlayer mp)
	{
		if (!next())
			lockscreenControls.setStopped();
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra)
	{
		Log.e(AUDIO_SERVICE, "MusicService Error: what = " + what + ", extra = " + extra);
		return true; // don't care
	}
	
	
	
	//**********************//
	//	   SYSTEM EVENTS	//
	//**********************//
	
	// this catches intents sent by the Lockscreen as well as the MusicNotification
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null && intent.getAction() != null)
		{
			switch (intent.getAction())
			{
			case TOGGLEPAUSE_ACTION:
				pause();
				return START_STICKY;
			case NEXT_ACTION:
				next();
				return START_STICKY;
			case PREVIOUS_ACTION:
				previous();
				return START_STICKY;
			case STOP_ACTION:
				stop();
				if (listener != null)
					listener.onExit();
				return START_STICKY;
			}
		}
		return super.onStartCommand(intent, flags, startId);
	}
	
	public void releaseEverything()
	{
		stop();
		if (player != null)
		{
			player.release();
			player = null;
		}
		audioManager.abandonAudioFocus(audioFocusChangeListener);
		notification.removeNotification();

		try
		{
			unregisterReceiver(noisyListener);
		}
		catch (Exception e) {}
	}
	
	@Override
	public IBinder onBind(Intent intent)
	{
		onRebind(intent);
		return musicBind;
	}
	
	@Override
	public void onRebind(Intent intent)
	{
		super.onRebind(intent);
		if (listener != null)
		{
			listener.onPlaystateChanged(isPlaying());
			if (currentSong != null)
				listener.onTrackChange(currentSong.getPath());
		}
	}

	@Override
	public boolean onUnbind(Intent intent)
	{
		return true; // we do want onRebind()
	}
	
	@Override
	public void onDestroy()
	{
		releaseEverything();
		super.onDestroy();
	}
}
