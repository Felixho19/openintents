/* 
 * Copyright (C) 2011 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openintents.historify.data.providers.internal;

import org.openintents.historify.data.providers.Events;
import org.openintents.historify.data.providers.EventsProvider;
import org.openintents.historify.data.providers.internal.QuickPosts.OpenHelper;
import org.openintents.historify.data.providers.internal.QuickPosts.QuickPostEventsTable;
import org.openintents.historify.data.providers.internal.QuickPosts.QuickPostSourcesTable;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

/**
 * 
 * Internal event provider for storing events posted via QuickPost.
 * 
 * @author berke.andras
 *
 */
public class QuickPostsProvider extends EventsProvider {

	public static final String NAME = "QuickPostsProvider";

	private static final int QUICKPOST_SOURCES = USER_DEFINED_MATCH + 1;

	private OpenHelper mOpenHelper;

	@Override
	public boolean onCreate() {
		super.onCreate();

		mUriMatcher.addURI(getAuthority(), QuickPosts.QUICKPOST_SOURCES_PATH,
				QUICKPOST_SOURCES);
		mOpenHelper = new OpenHelper(getContext());

		return true;
	}

	@Override
	protected String getAuthority() {
		return QuickPosts.QUICKPOSTS_AUTHORITY;
	}

	@Override
	public String getType(Uri uri) {
		try {
			return super.getType(uri);
		} catch (IllegalArgumentException e) {
			if (mUriMatcher.match(uri) == QUICKPOST_SOURCES) {
				return QuickPosts.QuickPostSourcesTable.CONTENT_TYPE;
			} else {
				throw e;
			}
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		try {
			return super.query(uri, projection, selection, selectionArgs,
					sortOrder);
		} catch (IllegalArgumentException e) {
			if (mUriMatcher.match(uri) == QUICKPOST_SOURCES) {
				return queryQuickPostSourcesTable(uri, projection, selection,
						selectionArgs, sortOrder);
			} else {
				throw e;
			}
		}

	}

	private Cursor queryQuickPostSourcesTable(Uri uri, String[] projection,
			String selection, String[] selectionArgs, String sortOrder) {

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		return db.query(QuickPosts.QuickPostSourcesTable._TABLE, projection,
				selection, selectionArgs, null, null, sortOrder);
	}

	@Override
	protected Cursor queryEvent(String eventKey) {
		
		String where = null;
		String[] whereArgs = null;
		if(eventKey==null) {
			where = "1 = 0";
		}
		else {
			where = Events.EVENT_KEY + " = ?";
			whereArgs = new String[]{eventKey};
		}
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		return db.query(QuickPostEventsTable._TABLE, null, where, whereArgs, null, null, null);
	}

	@Override
	protected Cursor queryEvents(String lookupKey) {

		String where = Events.CONTACT_KEY + " = ?";
		String[] whereArgs = new String[]{lookupKey};
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		return db.query(QuickPostEventsTable._TABLE, null, where, whereArgs, null, null, Events.PUBLISHED_TIME+" DESC");
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {

		String tableName = null;

		if (mUriMatcher.match(uri) == EVENTS_UNFILTERED) {
			tableName = QuickPostEventsTable._TABLE;
		} else if (mUriMatcher.match(uri) == QUICKPOST_SOURCES) {
			tableName = QuickPostSourcesTable._TABLE;
		} else {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		long id = db.insert(tableName, null, values);
		if (id != -1) {
			return Uri.withAppendedPath(uri, String.valueOf(id));
		}

		return null;
	}
}