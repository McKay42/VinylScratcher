//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		main entry point
//
// $NoKeywords: $main
//===============================================================================//

package com.pgo.vinylscratcher;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.pgo.vinylscratcher.MusicService.MusicBinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class MainActivity extends Activity implements MusicServiceListener, OnSeekBarChangeListener
{
	private MusicService musicSrv = null;
	private Intent playIntent = null;
	private boolean musicSrvBound = false;
	private boolean isInForeground = true;

	private String lastDirectory;
	private String lastActiveFile;
	private String nowPlayingDirectory;
	
	private ListView fileView;
	private FileArrayAdapter fileAdapter;
	private FileBrowser fileBrowser = null;
	
	private ImageButton playButton = null;
	private TextView curTime = null;
	private TextView remTime = null;
	private SeekBar seekbar = null;
	private TimeHandler seekbarUpdater;
	private boolean shouldBePlaying = false;
	private boolean isFingeringSeekbar = false;
	private int lastPos = 0;

	private int numBackButtonPressedAtEnd = 0;

	private boolean scheduledGoToNowPlaying = false;

	private AtomicReference<Runnable> scheduledRunnableAfterMusicServiceConnects = new AtomicReference<Runnable>(null);
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setIcon(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
		
		destroyBackup();
		
		// create file browser
		fileView = (ListView)findViewById(R.id.song_list);
		fileView.setFastScrollEnabled(true);
		fileBrowser = new FileBrowser(this);
		lastDirectory = Environment.getExternalStorageDirectory().getPath();
		setTitle(lastDirectory);
		fileAdapter = fileBrowser.build(lastDirectory);
		fileView.setAdapter(fileAdapter);
		
		// handle playback bar elements
		playButton = (ImageButton)findViewById(R.id.playButton);
		curTime = (TextView)findViewById(R.id.seek_bar_curtime);
		remTime = (TextView)findViewById(R.id.seek_bar_remtime);
		seekbar = (SeekBar)findViewById(R.id.seek_bar);
		seekbar.setOnSeekBarChangeListener(this);
		seekbarUpdater = new TimeHandler(this);
		isFingeringSeekbar = false;
	}
	
	public void onSoundFileClicked(final String path)
	{
		if (Config.DEBUG)
			System.out.println("onSoundFileClicked(): isMusicServiceValid = " + isMusicServiceValid());

		if (path != null)
		{
			// this can happen if e.g. the notification was destroyed (which will kill the service, on purpose)
			// NOTE: restarting the service is async, so we have to schedule the selection action until after we can actually start playing the song
			boolean wasMusicServiceInvalid = false;
			if (!isMusicServiceValid())
				wasMusicServiceInvalid = true;

			final Runnable runnable = new Runnable() {
				@Override
				public void run()
				{
					if (isMusicServiceValid())
					{
						nowPlayingDirectory = (new File(path)).getParent();
						musicSrv.setSongAndPlay(path, fileBrowser.getActiveFiles());
					}
				}
			};

			if (wasMusicServiceInvalid)
			{
				scheduledRunnableAfterMusicServiceConnects.set(runnable);
				keepMusicServiceUp();
			}
			else
			{
				scheduledRunnableAfterMusicServiceConnects.set(null);
				runnable.run();
			}
		}
	}
	
	// this gets forwarded to FileBrowser, which will in turn call onSoundFileClicked() above in here if it deems the click a sound file
	public void onFileBrowserClicked(View view)
	{
		Integer position = Integer.parseInt(view.getTag().toString());
		FileBrowserItem fbi = (FileBrowserItem) fileView.getAdapter().getItem(position);
		goToPath(fbi.getPath(), true);
	}

	private boolean goToPath(String path)
	{
		return goToPath(path, false);
	}
	private boolean goToPath(String path, boolean animateForward)
	{
		return goToPath(path, animateForward, false);
	}
	private boolean goToPath(String path, boolean animateForward, boolean scrollToNowPlaying)
	{
		if (Config.DEBUG)
			System.out.println("goToPath = " + path);

		if (path != null)
		{
			final FileArrayAdapter fileAdt = fileBrowser.onFileClicked(path);
			if (fileAdt != null)
			{
				// valid directory change, update everything
				numBackButtonPressedAtEnd = 0;
				lastDirectory = path;
				fileAdapter = fileAdt;
				
				File temp = new File(lastDirectory);
				if (temp.getParent() == null)
					getActionBar().setDisplayHomeAsUpEnabled(false);
				else
					getActionBar().setDisplayHomeAsUpEnabled(true);

				// update title and set new adapter
				setTitle(lastDirectory);
				fileAdapter.setSelectedItem(lastActiveFile);
				fileView.setAdapter(fileAdt);

				if (scrollToNowPlaying)
				{
					final int selectedPosition = fileAdapter.getSelectedItemPosition();
					fileView.post(new Runnable() {
						@Override
						public void run()
						{
							if (selectedPosition < fileView.getFirstVisiblePosition() || selectedPosition > fileView.getLastVisiblePosition())
								fileView.smoothScrollToPosition(selectedPosition);
						}
					});
				}

				return true;
			}
		}

		return false;
	}
	
	@Override
	public void onBackPressed()
	{
		if (lastDirectory.length() > 1)
		{
			File test = new File(lastDirectory);
			String parent = test.getParent();

			if (!goToPath(parent, false))
			{
				numBackButtonPressedAtEnd++;
				if (numBackButtonPressedAtEnd > 3)
					super.onBackPressed();
				else
					Util.showMessageToast(getApplicationContext(), 4 - numBackButtonPressedAtEnd + " more to exit");
			}
		}
		else
			super.onBackPressed();
	}
	
	
	
	//**********************//
	//	  Service Events	//
	//**********************//
	
	@Override
	public void onTrackChange(String newPath)
	{
		lastActiveFile = newPath;
		fileAdapter.setSelectedItem(newPath);
	}

	@Override
	public void onPlaystateChanged(boolean playing)
	{
		if (Config.DEBUG)
			System.out.println("onPlayStateChanged() playing = " + playing);

		// update play button image
		String uri = "drawable/" + (playing ? "ic_media_pause": "ic_media_play");
		int imageResource = getResources().getIdentifier(uri, null, getPackageName());
		if (imageResource != 0)
			playButton.setImageDrawable(getResources().getDrawable(imageResource));

		shouldBePlaying = playing;
		if (playing)
			seekbarUpdater.sendMessage(seekbarUpdater.obtainMessage(REFRESH_TIME));
	}
	
	@Override
	public void onExit()
	{
		if (Config.DEBUG)
			System.out.println("MainActivity::onExit() : stopping everything");
		
		backupEverything();
		
		handleMusicServiceUnbind();
		
		if (musicSrv != null)
			musicSrv.releaseEverything();
		if (playIntent != null)
			stopService(playIntent);
	}
	
	// handles label and seekbar refreshes
	private static final int REFRESH_TIME = 1;
	private static final class TimeHandler extends Handler
	{
		private final WeakReference<MainActivity> parent;

		public TimeHandler(final MainActivity player)
		{
			parent = new WeakReference<MainActivity>(player);
		}
		
		@Override
		public void handleMessage(final Message msg)
		{
			switch (msg.what)
			{
			case REFRESH_TIME:
				final long next = parent.get().refreshCurrentTime();
				parent.get().queueNextRefresh(next);
				break;
			default:
				break;
			}
		}
	};
	
	private void queueNextRefresh(final long delay)
	{
		if (isMusicServiceValid() && shouldBePlaying && isInForeground)
		{
			final Message message = seekbarUpdater.obtainMessage(REFRESH_TIME);
			seekbarUpdater.removeMessages(REFRESH_TIME);
			seekbarUpdater.sendMessageDelayed(message, delay);
		}
	}
	
	private long refreshCurrentTime()
	{
		if (!isMusicServiceValid())
			return 500;
		
		int duration = musicSrv.getDuration();
		int pos = musicSrv.getPosition();
		
		// handle seekbar & text udpates
		if (pos >= 0 && duration > 0 && musicSrv.isPlaying() && !isFingeringSeekbar)
		{
			lastPos = pos;
			final int progress = (int)(1000.0 * (double)pos / (double)duration);
			seekbar.setProgress(progress);
		}
		refreshCurrentTimeText(duration, musicSrv.isPlaying() ? pos : lastPos);
		
		// calculate optimal next update time for a smooth transition
		// HACKHACK: this is hardcoded bullshit
		final long remaining = 1000 - pos % 1000;
		
		int width = seekbar.getWidth();
		if (width == 0)
			width = 320;
		
		final long smoothrefreshtime = duration / width;
		if (smoothrefreshtime > remaining)
			return remaining;
		
		if (smoothrefreshtime < 20 || isFingeringSeekbar)
			return 20;
		
		return smoothrefreshtime;
	}
	
	private void refreshCurrentTimeText(int duration, int pos)
	{
		if (duration < 0 || pos < 0 || duration == Integer.MAX_VALUE || pos == Integer.MAX_VALUE)
			return;
		
		int seconds = 0;
		int minutes = 0;
		
		if (isFingeringSeekbar)
		{
			double seekbarPercent = (double)seekbar.getProgress() / (double)seekbar.getMax();
			double newPos = seekbarPercent*duration;
			seconds = (int)(newPos / 1000.0) % 60;
			minutes = (int)(newPos / 1000.0 / 60.0);
		}
		else
		{
			seconds = (int)((double)pos / 1000.0) % 60;
			minutes = (int)((double)pos / 1000.0 / 60.0);
		}
		
		if (seconds > 60 || minutes > 9)
			return;
		
		curTime.setText(String.valueOf(minutes) + ":" + (seconds < 10 ? "0" : "") + String.valueOf(seconds));
		
		if (!isFingeringSeekbar)
		{
			seconds = (int)((double)duration / 1000.0) % 60;
			minutes = (int)((double)duration / 1000.0 / 60.0);
			
			if (seconds > 60 || minutes > 9)
				return;
			
			remTime.setText(String.valueOf(minutes) + ":" + (seconds < 10 ? "0" : "") + String.valueOf(seconds));
		}
	}
	
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (isMusicServiceValid() && !shouldBePlaying && !musicSrv.isPlaying())
			seekbarUpdater.sendMessage(seekbarUpdater.obtainMessage(REFRESH_TIME));
	}
	
	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
		isFingeringSeekbar = false;
		if (!isMusicServiceValid())
			return;
		
		float percent = (float)seekBar.getProgress() / (float)seekBar.getMax();
		int duration = musicSrv.getDuration();
		
		musicSrv.seekTo((int)(duration*percent));
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
		isFingeringSeekbar = true;
	}
	
	
	
	//****************//
	//	  Playback    //
	//****************//
	
	public void onPlayClicked(View view)
	{
		if (isMusicServiceValid())
			musicSrv.pause();
	}
	
	public void onNextSongClicked(View view)
	{
		if (isMusicServiceValid())
			musicSrv.next();
	}
	
	public void onPreviousSongClicked(View view)
	{
		if (isMusicServiceValid())
			musicSrv.previous();
	}

	public void onNowPlayingClicked(View view)
	{
		goToPath(nowPlayingDirectory, false, true);
	}

	public void onHomeClicked(View view)
	{
		goToPath(Environment.getExternalStorageDirectory().getPath(), false);
	}
	
	
	
	//****************//
	//	  Activity    //
	//****************//
	
	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);

		outState.putString("lastDirectory", lastDirectory);
		outState.putString("nowPlayingDirectory", nowPlayingDirectory);
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);

		if (savedInstanceState != null)
		{
			lastDirectory = savedInstanceState.getString("lastDirectory");
			nowPlayingDirectory = savedInstanceState.getString("nowPlayingDirectory");
		}
	}
	
	private void backupEverything()
	{
		if (Config.DEBUG)
			System.out.println("MainActivity::backupEverything()");

		// destroyed
		Util.saveStringToSharedPreferences(lastActiveFile, "currentSong", this);
		Util.saveStringArrayToSharedPreferences(fileBrowser.getActiveFiles(), "currentSongList", this);

		// kept
		Util.saveStringToSharedPreferences(nowPlayingDirectory, "nowPlayingDirectory", this);
	}
	
	private void destroyBackup()
	{
		if (Config.DEBUG)
			System.out.println("MainActivity::destroyBackup()");
		
		Util.saveStringToSharedPreferences("", "currentSong", this);
		Util.saveStringArrayToSharedPreferences(new ArrayList<String>(), "currentSongList", this);
	}
	
	@Override
	protected void onStop()
	{
		handleMusicServiceUnbind();
		super.onStop();
		
		backupEverything();
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		destroyBackup();
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();

		isInForeground = false;
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();

		if (Config.DEBUG)
			System.out.println("onResume()");
		
		isInForeground = true;
		if (shouldBePlaying)
			seekbarUpdater.sendMessage(seekbarUpdater.obtainMessage(REFRESH_TIME));

		// TODO: this is not necessarily the desired action. what if no music is playing, and I don't want to start the service? just scrolling through?
		// TODO: use case: notification has been killed, user has exited the app, user opens the app again but doesn't intend to start listening to music
		// TODO: fix resuming sometimes not correctly handling currentSong (unselected list, un-updated buttons etc.), why is this random? probably correlated with onRebind()?
		keepMusicServiceUp();



		// check if we got here through clicking on the notification, and go to now playing in that case
		{
			// if the activity was murdered by the back button, then onNewIntent() is not called, but the extras are set on the activity intent instead, so extra check here
			// NOTE: this will cause double execution of onNewIntent() under regular use, so keep that in mind
			onNewIntent(getIntent());

			if (scheduledGoToNowPlaying)
			{
				scheduledGoToNowPlaying = false;

				if (nowPlayingDirectory == null)
					nowPlayingDirectory = Util.loadStringFromSharedPreferences("nowPlayingDirectory", null, this);

				onNowPlayingClicked(null);
			}
		}
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		if (Config.DEBUG)
			System.out.println("onNewIntent()");

		if (intent.hasExtra("com.pgo.vinylscratcher.NOW_PLAYING"))
			scheduledGoToNowPlaying = true;
	}
	
	// handle connection to the music service
	private ServiceConnection musicConnection = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			MusicBinder binder = (MusicBinder)service;
			
			// get service and pass list
			musicSrv = binder.getService();
			musicSrv.setListener(MainActivity.this);
			musicSrvBound = true;
			
			handleOnMusicServiceRebind();
		}
	 
		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			musicSrvBound = false;
		}
	};

	public boolean isMusicServiceValid()
	{
		return musicSrvBound == true && musicSrv != null;
	}
	
	private void keepMusicServiceUp()
	{
		if (Config.DEBUG)
			System.out.println("MainActivity::keepMusicServiceUp() : isCurrentlyRunning = "+isMusicServiceValid());
		
		if (!isMusicServiceValid())
		{
			// start the service
			playIntent = new Intent(this, MusicService.class);
			startService(new Intent(this, MusicService.class));
			
			// and bind to it
			handleMusicServiceBind();
		}
	}
	
	private void handleMusicServiceBind()
	{
		if (playIntent != null && musicConnection != null)
			bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
		else
			Log.e(AUDIO_SERVICE, "MainActivity::handleMusicServiceBind() : playIntent == null || musicConnection == null!!!");
	}
	
	private void handleMusicServiceUnbind()
	{
		if (musicConnection != null && musicSrvBound)
		{
			unbindService(musicConnection);
			musicSrvBound = false;
		}
		else
			Log.e(AUDIO_SERVICE, "MainActivity::handleMusicServiceUnbind() : musicConnection == null!!!");
	}
	
	private void handleOnMusicServiceRebind()
	{
		// also restore the current music file to the MusicService if the MusicService was stopped/killed by destroying the Notification
		if (isMusicServiceValid())
		{
			ArrayList<String> activeFiles = Util.loadStringArrayFromSharedPreferences("currentSongList", this);
			nowPlayingDirectory = Util.loadStringFromSharedPreferences("nowPlayingDirectory", null, this);
			final String selectedItem = Util.loadStringFromSharedPreferences("currentSong", null, this);

			///System.out.println("MainActivity::handleOnMusicServiceRebind() : restoring selected music: "+selectedItem);
			///System.out.println("MainActivity::handleOnMusicServiceRebind() : active files: "+activeFiles);

			final Runnable runnable = scheduledRunnableAfterMusicServiceConnects.getAndSet(null);

			if (runnable == null)
			{
				if (activeFiles.size() > 0 && selectedItem != null && selectedItem.length() > 0 && !musicSrv.isPlaying())
					musicSrv.setSongWithoutAutoplay(selectedItem, activeFiles);
			}
			else
				runnable.run();
		}
	}
	

	
	//***************//
	//	  Options    //
	//***************//
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);

		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case R.id.action_about:
			{
				// version 0.1:
				// - Original version from 2014
				// version 0.2:
				// - Fixed Android 10+ not listing SD card under /storage/
				// - TODO: kill music service + notification if quitting or back-button-ing app while no music is playing. alternatively, only start music service when actually starting to play music
				// - TODO: fix setSelectedItem and onTrackChanged bug which keeps controls not-playing while actually playing (after murdering activity via back button), always happens when selectedItem is non-null onResume

				AlertDialog aboutDialog = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme_Holo_Dialog)).create();
				aboutDialog.setTitle("Vinyl Scratcher");
				aboutDialog.setMessage("© PG 2014\n\nVersion " + Config.VERSION + "\n\n");
				aboutDialog.show();
				TextView textView = (TextView)aboutDialog.findViewById(android.R.id.message);
				textView.setTextSize(15);
			}
			break;

		case R.id.action_home:
			onHomeClicked(null);
			break;

		case R.id.action_nowplaying:
			onNowPlayingClicked(null);
			break;
			/*
		case R.id.action_end:
			if (musicSrv != null)
				musicSrv.releaseEverything();
			if (playIntent != null)
				stopService(playIntent);
			musicSrv = null;
			finish();
			break;
			*/
		case android.R.id.home:
			if (lastDirectory.length() > 1)
			{
				File test = new File(lastDirectory);
				String parent = test.getParent();
				if (parent != null)
					goToPath(parent, false);
			}
			break;
		}

		return super.onOptionsItemSelected(item);
	}
}
