//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		file browser item
//
// $NoKeywords: $item
//===============================================================================//

package com.pgo.vinylscratcher;

public class FileBrowserItem implements Comparable<FileBrowserItem>
{
	private String name;
	private String path;
	private String image;
	private boolean isSoundFile;
	
	public FileBrowserItem(String name, String path, String image, boolean isSoundFile)
	{
		this.name = name;
		this.path = path;
		this.image = image;
		this.isSoundFile = isSoundFile;
	}
   
	@Override
	public int compareTo(FileBrowserItem another)
	{
		if (this.name != null)
			return this.name.toLowerCase().compareTo(another.getName().toLowerCase());
		else
			throw new IllegalArgumentException();
	}
	
	public String getName() {return name;}
	public String getPath() {return path;}
	public String getImage()  {return image;}
	public boolean isSoundFile() {return isSoundFile;}
	
	@Override
	public String toString()
	{
		return "FileBrowserItem {name = "+name+" ; path = "+path+" ; image = "+image+" ; isSoundFile = "+isSoundFile+"}";
	}
}
