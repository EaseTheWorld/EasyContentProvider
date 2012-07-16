/*
 * Copyright (C) 2012 EaseTheWorld
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
 * 
 * https://github.com/EaseTheWorld/EasyContentProvider
 */

package dev.easetheworld.easycontentprovider;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public abstract class EasyContentProvider extends ContentProvider {
	
	private Uri mAuthorityUri;
	private SQLiteOpenHelper mDbHelper;
	private UriOpsMatcher mUriOpsMatcher;
	
	/**
	 * This will be called only once in onCreate().
	 * 
	 * @return authority The authority of this provider. cannot be null.
	 */
	abstract protected String getAuthority();
	
	/**
	 * This will be called only once in onCreate().
	 * 
	 * @return The array of UriOps for this provider
	 */
	abstract protected UriOps[] onCreateUriOps();
	
	/**
	 * If you don't have to make your own SQLiteOpenHelper,
	 * use this to manage db history and create/upgrade database easily.
	 * Just add the modification of the db at the end of the array.
	 * Make sure that you do not change the old history.
	 * The length of the array itself is the db version.
	 * 
	 * If you want to make your own SQLiteOpenHelper, just return null.
	 * 
	 * This will be called only once in onCreateSqLiteOpenHelper().
	 * 
	 * @return The array of the DatabaseHistory.
	 */
	abstract protected DatabaseHistory[] onCreateDatabaseHistory();
	
	/**
	 * If you want to take advantage of DatabaseHistory, don't override this.
	 * Database name will be same as the name of provider.
	 * 
	 * If you want to make your own SQLiteOpenHelper, just override this without calling super
	 * so that onCreateDatabaseHistory won't be called.
	 * 
	 * This will be called only once in onCreate().
	 * 
	 * @param context
	 * @return
	 */
	protected SQLiteOpenHelper onCreateSQLiteOpenHelper(Context context) {
		DatabaseHistory[] history = onCreateDatabaseHistory();
		return new DatabaseHistoryBuilder(context, getClass().getSimpleName()+".db", history);
	}

	@Override
	public boolean onCreate() {
		// check authority
		String authority = getAuthority();
		if (authority == null)
			throw new IllegalStateException("Authority cannot be null");
		mAuthorityUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(authority).build();
		
		// create db
		mDbHelper = onCreateSQLiteOpenHelper(getContext());
		
		// create uris
		mUriOpsMatcher = new UriOpsMatcher(authority, onCreateUriOps());
		return true;
	}
	
	/**
	 * This is like UriMatcher.match(uri) but this returns UriOps
	 * 
	 * @param uri
	 * @return
	 */
	protected UriOps getUriOps(Uri uri) {
		return mUriOpsMatcher.match(uri);
	}
	
	/**
	 * Inner class for uri-UriOps match
	 */
	private static class UriOpsMatcher {
		private UriMatcher mUriMatcher;
		private UriOps[] mUriOpsArray;
		
		private UriOpsMatcher(String authority, UriOps[] uriOps) {
			mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
			for (int i=0; i<uriOps.length; i++) {
				UriOps ops = uriOps[i];
				if (ops.getType() == null) // fill type
					ops.setType(UriOps.getDefaultType(authority, ops.mUriPath));
				mUriMatcher.addURI(authority, ops.mUriPath, i);
			}
			mUriOpsArray = uriOps;
		}
		
		private UriOps match(Uri uri) {
			int code = mUriMatcher.match(uri);
			if (code == -1)
				throw new IllegalArgumentException("Unknown URI: " + uri.toString());
			else {
				UriOps ops = mUriOpsArray[code];
				return ops;
			}
		}
	}
	
	/**
	 * This class has the operations which are matched to a uri.
	 * default UriOps has no operation except getType() which is automatically generated based on uri.
	 * If you want to set your own content type, use setType().
	 */
	public static class UriOps {
		private String mUriPath;
		
		protected List<Integer> mUriWildcardPosition;
		protected static boolean isUriWildcard(String s) {
			// '#' : any number, '*' : any text
			return "#*".indexOf(s) >= 0;
		}
		
		/**
		 * @param uriPath uri(excluding authority) that matches this operations
		 */
		public UriOps(String uriPath) {
			mUriPath = uriPath;
			
			// find the position of wild cards in uri path
			String[] segments = uriPath.split("/");
			for (int i=0; i<segments.length; i++) {
				if (isUriWildcard(segments[i])) {
					if (mUriWildcardPosition == null)
						mUriWildcardPosition = new ArrayList<Integer>();
					mUriWildcardPosition.add(i);
				}
			}
		}
		
		protected final String getUriPath() {
			return mUriPath;
		}
		
		private String mType;
		
		/**
		 * @param type
		 * @return this object to allow for chaining
		 */
		public UriOps setType(String type) {
			mType = type;
			return this;
		}
		
		public String getType() {
			return mType;
		}
		
		// if path is "cheeses", type is "vnd.android.cursor.dir/authority.cheeses"
		// if path is "cheeses/#", type is "vnd.android.cursor.item/authority.cheeses"
		// if path is "cheeses/#/sub", type is "vnd.android.cursor.dir/authority.cheeses.sub"
		// if path is "cheeses/#/sub/#", type is "vnd.android.cursor.item/authority.cheeses.sub"
		private static String getDefaultType(String authority, String path) {
			StringBuilder sb = new StringBuilder();
			// check last character is wild card
			if (isUriWildcard(path.substring(path.length()-1))) {
				sb.append(ContentResolver.CURSOR_ITEM_BASE_TYPE);
			} else {
				sb.append(ContentResolver.CURSOR_DIR_BASE_TYPE);
			}
			sb.append("/");
			sb.append(authority);
			String[] segments = path.split("/");
			for (String segment : segments) {
				if (isUriWildcard(segment))
					continue;
				sb.append(".");
				sb.append(segment);
			}
			return sb.toString();
		}
    }
	
	/**
	 * If the UriOps matched with given uri implements OpQuery, this will call OpQuery.query()
	 * The returned cursor will be set notification uri.
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		SQLiteDatabase db = mDbHelper.getReadableDatabase();
		if (db == null) return null;
		
		UriOps ops = getUriOps(uri);
		Cursor result = null;
		if (ops instanceof OpQuery)
			result = ((OpQuery)ops).query(db, uri, projection, selection, selectionArgs, sortOrder);
		
		if (result != null)
			result.setNotificationUri(getContext().getContentResolver(), uri);
		return result;
	}

	/**
	 * If the UriOps matched with given uri implements OpInsert, this will call OpInsert.insert()
	 * This will call notify database change once.
	 */
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return null;
		
		UriOps ops = getUriOps(uri);
		Uri result = null;
		if (ops instanceof OpInsert)
			result = ((OpInsert)ops).insert(db, uri, values);
		
		if (result != null)
			notifyChange(result);
		return result;
	}

	/**
	 * If the UriOps matched with given uri implements OpInsert, this will call OpInsert.bulkInsert()
	 * This will call notify database change only once.
	 */
	@Override
	public int bulkInsert(Uri uri, ContentValues[] values) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		UriOps ops = getUriOps(uri);
		int result = 0;
		if (ops instanceof OpInsert)
			result = ((OpInsert)ops).bulkInsert(db, uri, values);
		
		if (result > 0)
			notifyChange(uri);
		return result;
	}

	/**
	 * If the UriOps matched with given uri implements OpUpdate, this will call OpUpdate.update()
	 * This will call notify database change once.
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		UriOps ops = getUriOps(uri);
		int result = 0;
		if (ops instanceof OpUpdate)
			result = ((OpUpdate)ops).update(db, uri, values, selection, selectionArgs);
		
		if (result > 0)
			notifyChange(uri);
		return result;
	}
	
	/**
	 * If the UriOps matched with given uri implements OpDelete, this will call OpDelete.delete()
	 * This will call notify database change once.
	 */
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		UriOps ops = getUriOps(uri);
		int result = 0;
		if (ops instanceof OpDelete)
			result = ((OpDelete)ops).delete(db, uri, selection, selectionArgs);
		
		if (result > 0)
			notifyChange(uri);
		return result;
	}
	
	@Override
	public String getType(Uri uri) {
		return getUriOps(uri).getType();
	}
	
	private final ThreadLocal<Boolean> mApplyingBatch = new ThreadLocal<Boolean>();
	
	/**
	 * This will call notify database change only once to increase performance.
	 */
	@Override
	public ContentProviderResult[] applyBatch(
			ArrayList<ContentProviderOperation> operations)
			throws OperationApplicationException {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return null;
		
		ContentProviderResult[] result;
		db.beginTransaction();
		try {
			mApplyingBatch.set(true); // insert, delete, update shouldn't notify
			result = super.applyBatch(operations);
			db.setTransactionSuccessful();
		} finally {
			mApplyingBatch.set(false);
			db.endTransaction();
		}
		
		notifyChange(mAuthorityUri);
		return result;
	}
	
	protected boolean notifyChange(Uri uri) {
		Boolean b = mApplyingBatch.get();
		if (b != null && b) return false; // is in applyBatch
		
		getContext().getContentResolver().notifyChange(uri, null);
		return true;
	}

	/**
	 * If your database need to be modified and increase version,
	 * implement a DatabaseHistory and append it at the end of the DatabaseHistory array returned by onCreateDatabaseHistory().
	 */
	protected static interface DatabaseHistory {
		void upgrade(SQLiteDatabase db);
	}
	
	private class DatabaseHistoryBuilder extends SQLiteOpenHelper {
		
		private DatabaseHistory[] mHistory;

		public DatabaseHistoryBuilder(Context context, String name, DatabaseHistory[] history) {
			super(context, name, null, history.length);
			mHistory = history;
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			buildDatabaseHistory(db, 0, mHistory.length); // build from nothing
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			buildDatabaseHistory(db, oldVersion, newVersion); // build from old version
		}
		
		private void buildDatabaseHistory(SQLiteDatabase db, int oldVersion, int newVersion) {
			for (int v=oldVersion; v<newVersion; v++) {
				mHistory[v].upgrade(db);
			}
		}
	}
	
	public static interface OpQuery {
		Cursor query(SQLiteDatabase db, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder);
	}
	
	public static interface OpInsert {
		Uri insert(SQLiteDatabase db, Uri uri, ContentValues values);
		int bulkInsert(SQLiteDatabase db, Uri uri, ContentValues[] values);
	}
	
	public static interface OpUpdate {
		int update(SQLiteDatabase db, Uri uri, ContentValues values, String selection, String[] selectionArgs);
	}
	
	public static interface OpDelete {
		int delete(SQLiteDatabase db, Uri uri, String selection, String[] selectionArgs);
	}
}