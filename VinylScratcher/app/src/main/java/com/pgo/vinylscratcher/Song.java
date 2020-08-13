//================ Copyright (c) 2015, PG, All rights reserved. =================//
//
// Purpose:		represents a playable music file
//
// $NoKeywords: $song
//===============================================================================//

package com.pgo.vinylscratcher;

public class Song
{
	private String title;
	private String album;
	private String artist;
	private String composer;
	private String genre;
	private int year;
	private String path;
	private String filename;
	
	public Song(String title, String album, String artist, String composer, String genre, int year, String path, String filename)
	{
		this.title = title;
		this.album = album;
		this.artist = artist;
		this.composer = composer;
		this.genre = genre;
		this.year = year;
		this.path = path;
		this.filename = filename;
	}
	
	public String getTitle() {return title;}
	public String getAlbum() {return album;}
	public String getArtist() {return artist;}
	public String getComposer() {return composer;}
	public String getGenre() {return genre;}
	public int getYear() {return year;}
	public String getPath() {return path;}
	public String getFilename() {return filename;}
}
