//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		listener interface for the music service
//
// $NoKeywords: $musiclistn
//===============================================================================//

package com.pgo.vinylscratcher;

public interface MusicServiceListener
{
	public abstract void onTrackChange(String newPath);
	public abstract void onPlaystateChanged(boolean playing);
	public abstract void onExit();
}
