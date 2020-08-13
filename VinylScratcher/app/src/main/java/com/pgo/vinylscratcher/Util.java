//================ Copyright (c) 2015, PG, All rights reserved. =================//
//
// Purpose:		helper functions
//
// $NoKeywords: $cbase
//===============================================================================//

package com.pgo.vinylscratcher;

import java.util.ArrayList;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class Util
{
	private static Toast prevToast = null;

	public static boolean saveStringToSharedPreferences(String str, String stringName, Context context)
	{
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
		editor.putString(stringName, str);
		return editor.commit();
	}
	
	public static String loadStringFromSharedPreferences(String stringName, String defaultValue, Context context)
	{
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		return sp.getString(stringName, defaultValue);
	}
	
	public static boolean saveStringArrayToSharedPreferences(ArrayList<String> array, String arrayName, Context context)
	{
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
		editor.putInt(arrayName +"_size", array.size());

		for (int i=0; i<array.size(); i++)
		{
			editor.putString(arrayName + "_" + i, array.get(i));
		}

		return editor.commit();
	}
	
	public static ArrayList<String> loadStringArrayFromSharedPreferences(String arrayName, Context context)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

		int size = prefs.getInt(arrayName + "_size", 0);
		ArrayList<String> array = new ArrayList<String>(size);

		for (int i=0; i<size; i++)
		{
			array.add(prefs.getString(arrayName + "_" + i, null));
		}

		return array;
	}

	public static void showMessageToast(Context context, String message)
	{
		if (prevToast != null)
			prevToast.cancel();

		Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
		toast.show();

		prevToast = toast;
	}
}
