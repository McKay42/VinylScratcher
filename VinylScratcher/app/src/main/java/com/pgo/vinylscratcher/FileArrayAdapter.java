//================ Copyright (c) 2014, PG, All rights reserved. =================//
//
// Purpose:		file browser array adapter
//
// $NoKeywords: $adpt
//===============================================================================//

package com.pgo.vinylscratcher;

import java.util.List;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class FileArrayAdapter extends ArrayAdapter<FileBrowserItem>
{
	private int id;
	private Context context;
	private List<FileBrowserItem> items;
	private LayoutInflater fileInf;

	private String selectedItem;
	private int selectedBackground;
	private int defaultBackground;

	public FileArrayAdapter(Context context, int textViewResourceId, List<FileBrowserItem> objects)
	{
		super(context, textViewResourceId, objects);
		this.context = context;
		fileInf = LayoutInflater.from(context);
		id = textViewResourceId;
		items = objects;

		selectedBackground = context.getResources().getColor(R.color.file_background_selected_color);
		defaultBackground = context.getResources().getColor(R.color.file_background_color);
	}

	public void setSelectedItem(String path)
	{
		boolean fireNotify = true;
		if (path != null && path.equals(selectedItem))
		    fireNotify = false;

		if (fireNotify)
		{
		    selectedItem = path;
		    notifyDataSetChanged();
		}
	}

	public String getSelectedItem()
	{
		return selectedItem;
	}

	public int getSelectedItemPosition()
	{
		for (int i=0; i<items.size(); i++)
		{
			if (items.get(i).getPath().equals(selectedItem))
				return i;
		}

		return 0;
	}

	@Override
	public int getCount()
	{
		return items.size();
	}

	@Override
	public FileBrowserItem getItem(int i)
	{
		return items.get(i);
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		View v = convertView;
		if (v == null)
		{
		    v = fileInf.inflate(id, null);
		}

		final FileBrowserItem o = items.get(position);
		if (o != null)
		{
		    // get textview, set tag
		    TextView t1 = (TextView) v.findViewById(R.id.TextView01);
		    t1.setGravity(Gravity.CENTER_VERTICAL);
		    v.setTag(position);

		    boolean isSelected = o.getPath() != null && o.getPath().equals(selectedItem);

		    // set image
		    ImageView imageCity = (ImageView) v.findViewById(R.id.fd_Icon1);
		    String uri = "drawable/" + (isSelected ? "vsplaying": o.getImage());
		    int imageResource = context.getResources().getIdentifier(uri, null, context.getPackageName());
		    if (imageResource != 0)
		    {
		        Drawable image = context.getResources().getDrawable(imageResource);
		        imageCity.setImageDrawable(image);
		    }

		    // set text
		    if (t1 != null)
		        t1.setText(o.getName());

		    // handle selection
		    if (isSelected)
		        v.setBackgroundColor(selectedBackground);
		    else
		        v.setBackground(context.getResources().getDrawable(R.drawable.listitem_background));
		}

		return v;
	}
}
