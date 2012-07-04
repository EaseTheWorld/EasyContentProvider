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
	 * @return authority cannot be null.
	 */
	abstract protected String getAuthority();
	
	abstract protected UriOps[] onCreateUriOps();
	
	abstract protected DatabaseHistory[] onCreateDatabaseHistory();
	
	// If subclass wants to make its own SQLiteOpenHelper, override this.
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
	 * This is like UriMatcher.match(uri) except this returns UriOps
	 * 
	 * @param uri
	 * @return
	 */
	protected UriOps getUriOps(Uri uri) {
		return mUriOpsMatcher.match(uri);
	}
	
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
	
	// default UriOps has no op except getType.
	public static class UriOps {
		private String mUriPath;
		
		protected List<Integer> mUriWildcardPosition;
		protected static boolean isUriWildcard(String s) {
			// '#' : any number, '*' : any text
			return "#*".indexOf(s) >= 0;
		}
		
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
	
	/*
	 * authority should be available before onCreate, because it will be used in static class xxxTable's CONTENT_URI
	private String getAuthority() {
		try {
			ComponentName cn = new ComponentName(getContext(), getClass());
			ProviderInfo pi = getContext().getPackageManager().getProviderInfo(cn, 0);
			return pi.authority;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
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