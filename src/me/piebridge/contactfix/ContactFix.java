/*
 * vim: set sw=4 ts=4:
 *
 * Copyright (C) 2012 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.contactfix;

import android.app.Activity;
import android.os.Bundle;

import android.content.Context;
import android.content.ContentProviderOperation;
import android.content.OperationApplicationException;

import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;

import android.util.Log;
import java.util.ArrayList;

import android.os.Handler;;
import android.view.View;
import android.widget.TextView;
import android.widget.ProgressBar;

import me.piebridge.hanzitopinyin.HanziToPinyin;

public class ContactFix extends Activity
{
	private TextView textView;
	private ProgressBar progressBar;

	// use handler
	private final Handler handler = new Handler();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		textView = (TextView)findViewById(R.id.text);
		progressBar = (ProgressBar)findViewById(R.id.progress);
		progressBar.setIndeterminate(false);
		progressBar.setVisibility(View.VISIBLE);

		// in a new thread
		new Thread(new Runnable() {
			public void run() {
				updatePhoneticName();
				updatePhoneNumber();
				complete();
			}
		}).start();

	}

	private void complete() {
		handler.post(new Runnable() {
			public void run() {
				textView.setText(R.string.completed);
				progressBar.setVisibility(View.GONE);
				// call finish(); ?
			}
		});
	}

	// inner class should use final
	private void showText(final String text) {
		handler.post(new Runnable() {
			public void run() {
				textView.setText(text);
			}
		});
	}

	private void updatePhoneticName() {
		// query display_name and phonetic_given_name
		final Cursor cursor = getContentResolver().query(Data.CONTENT_URI,
				new String[] {Data._ID, Data.RAW_CONTACT_ID, StructuredName.DISPLAY_NAME, StructuredName.PHONETIC_GIVEN_NAME},
				Data.MIMETYPE + "='" + StructuredName.CONTENT_ITEM_TYPE + "'",
				null, null);

		int size = 0;
		final ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

		try {
			while (cursor.moveToNext()) {
				final long dataId = cursor.getLong(0);
				final long rawContactId = cursor.getLong(1);
				if (!cursor.isNull(2)) {
					final String displayName = cursor.getString(2);
					final String pinyinName = HanziToPinyin.convert(displayName);
					// let the phonetic name to be pinyin
					String phoneticName = pinyinName;

					// get phonetic name if its specified
					if (!cursor.isNull(3) && cursor.getString(3).length() > 0) {
						phoneticName = cursor.getString(3);
					}

					// only for chinese
					if (pinyinName.length() > 0) { 
						size += 1;
						showText(getResources().getString(R.string.dealing, new Object[] {displayName}));
						Log.d(getResources().getString(R.string.app_name), displayName + ": " + pinyinName + ": " + phoneticName);

						// modify display name to other will trigger the name_lookup
						ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
								.withSelection(Data._ID + "=?", new String[]{String.valueOf(dataId)})
								.withValue(StructuredName.DISPLAY_NAME, displayName + " ")
								.withValue(StructuredName.GIVEN_NAME, displayName + " ")
								.withValue(StructuredName.FAMILY_NAME, "")
								.withValue(StructuredName.PREFIX, "")
								.withValue(StructuredName.MIDDLE_NAME, "")
								.withValue(StructuredName.SUFFIX, "")
								.withValue(StructuredName.PHONETIC_GIVEN_NAME, phoneticName)
								.withValue(StructuredName.PHONETIC_MIDDLE_NAME, "")
								.withValue(StructuredName.PHONETIC_FAMILY_NAME, "")
								.build());

						// recover display name
						ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
								.withSelection(Data._ID + "=?", new String[]{String.valueOf(dataId)})
								.withValue(StructuredName.DISPLAY_NAME, displayName)
								.withValue(StructuredName.GIVEN_NAME, displayName)
								.withValue(StructuredName.FAMILY_NAME, "")
								.withValue(StructuredName.PREFIX, "")
								.withValue(StructuredName.MIDDLE_NAME, "")
								.withValue(StructuredName.SUFFIX, "")
								.withValue(StructuredName.PHONETIC_GIVEN_NAME, phoneticName)
								.withValue(StructuredName.PHONETIC_MIDDLE_NAME, "")
								.withValue(StructuredName.PHONETIC_FAMILY_NAME, "")
								.build());

					}
				}
			}
		} finally {
			cursor.close();
		}

		showText(getResources().getString(R.string.applying, new Object[] {size}));

		try {
			getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		} catch (Exception e) {
			Log.e(getResources().getString(R.string.app_name), "SORRY", e);
		}
	}

	private void updatePhoneNumber() {
		// query phone number and type
		final Cursor cursor = getContentResolver().query(Data.CONTENT_URI,
				new String[] {Data._ID, Data.RAW_CONTACT_ID, Phone.NUMBER, Phone.TYPE},
				Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'",
				null, null);

		int size = 0;
		final ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

		try {
			while (cursor.moveToNext()) {
				final long dataId = cursor.getLong(0);
				final long rawContactId = cursor.getLong(1);
				if (!cursor.isNull(2)) {
					final String orig = cursor.getString(2);
					final int type = cursor.getInt(3);

					// remove the '-' in phone number
					final String number = orig.replace("-", "");
					if (!number.equals(orig)) {
						size += 1;
						showText(getResources().getString(R.string.dealing, new Object[] {number}));
						ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
								.withSelection(Data._ID + "=?", new String[]{String.valueOf(dataId)})
								.withValue(Phone.NUMBER, number)
								.withValue(Phone.TYPE, type)
								.build());
					}
				}
			}
		} finally {
			cursor.close();
		}

		showText(getResources().getString(R.string.applying, new Object[] {size}));

		try {
			getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
		} catch (Exception e) {
			Log.e(getResources().getString(R.string.app_name), "SORRY", e);
		}
	}

}
