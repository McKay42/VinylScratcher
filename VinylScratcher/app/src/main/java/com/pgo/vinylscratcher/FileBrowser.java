//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		walks through the file system and builds a FileArrayAdapter
//
// $NoKeywords: $adpt
//===============================================================================//

package com.pgo.vinylscratcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class FileBrowser
{
	private static Comparator SORTING_COMPARATOR = new NaturalOrderComparator();

	private MainActivity parent;
	private ArrayList<String> files;
	
	public static boolean isSoundFile(String extension)
	{
		if (extension.equalsIgnoreCase("mp3") || extension.equalsIgnoreCase("ogg") || extension.equalsIgnoreCase("wav")
		 || extension.equalsIgnoreCase("mp4") || extension.equalsIgnoreCase("flac") || extension.equalsIgnoreCase("aac")
		 || extension.equalsIgnoreCase("m4a"))
			return true;
		else
			return false;
	}
	
	public FileBrowser()
	{
		files = new ArrayList<String>();
		this.parent = null;
	}
	
	public FileBrowser(MainActivity parent)
	{
		files = new ArrayList<String>();
		this.parent = parent;
	}
	
	public FileArrayAdapter build(String path)
	{
		files.clear();

		File f = new File(path);

		// NOTE: originally there was a check here which denied navigating outside of canRead() isDirectory(), but alas this produced too many false positives
		// NOTE: since navigation is based on string manipulation, and not File calls, navigation to root "/" is always possible

		ArrayList<FileBrowserItem> dir = new ArrayList<FileBrowserItem>();
		ArrayList<FileBrowserItem> fls = new ArrayList<FileBrowserItem>();
		
		// go through the file system
		try
		{
			File[] dirs = f.listFiles();

			for (File curFile: dirs)
			{
				// only show files/folders we actually have access to
				if (curFile.isHidden() || !curFile.canRead())
					continue;
				
				String filename = curFile.getName();
				String absolutePath = curFile.getAbsolutePath();
				
				// directory VS file
				if (curFile.isDirectory())
				{
					/*
					int numFiles = 0;
					int numDirectories = 0;

					for (File subFile : curFile.listFiles())
					{
						if (subFile.isFile())
							numFiles++;
						else if (subFile.isDirectory())
							numDirectories++;
					}
					*/

					dir.add(new FileBrowserItem(filename /*+ "   (" + numFiles + "," + numDirectories + ")"*/, absolutePath, "vsfolder", false));
				}
				else
				{
					String extension = filename.substring((filename.lastIndexOf(".") + 1), filename.length());
					boolean soundFile = isSoundFile(extension);
					fls.add(new FileBrowserItem(filename, absolutePath, soundFile ? "vsmusicfile" : "vsfile", soundFile));
				}
			}
		}
		catch (Exception e) {}
		
		// first sort the directories, then sort the files, then merge the two lists so that all directories are sorted on top
		Collections.sort(dir, SORTING_COMPARATOR);
		Collections.sort(fls, SORTING_COMPARATOR);
		dir.addAll(fls);
		
		// "playlist"
		for (FileBrowserItem fbi : fls)
		{
			if (fbi.isSoundFile())
				files.add(fbi.getPath());
		}
		
		/*
		if (!f.getName().equalsIgnoreCase(Environment.getExternalStorageDirectory().getPath()) && f.getParent() != null)
		{
			FileBrowserItem back = new FileBrowserItem("..", (new File(f.getParent())).getAbsolutePath(), "vsback", false);
			//dir.add(0, back); // unnecessary, since the action bar already serves this purpose
			//dir.add(back); // convenient for large phones, but redundant due to hw/sw back button
		}
		*/

		return new FileArrayAdapter(parent, R.layout.file_view, dir);
	}
	
	FileArrayAdapter onFileClicked(String path)
	{
		File folderTest = new File(path);
		if (folderTest.isDirectory())
			return build(path);
		else
		{
			// forward the sound file click back to the main activity, so it can notify the MusicService
			parent.onSoundFileClicked(path);
			return null;
		}
	}
	
	// only call this after having called build()! because it will be null
	public ArrayList<String> getActiveFiles()
	{
		return new ArrayList<String>(files);
	}
}
