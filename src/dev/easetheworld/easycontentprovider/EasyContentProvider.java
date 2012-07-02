package dev.easetheworld.easycontentprovider;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public abstract class EasyContentProvider extends ContentProvider {
	
	private SQLiteOpenHelper mDbHelper;
	private UriOpsMatcher mUriOpsMatcher;
	
	// If subclass wants to make its own SQLiteOpenHelper, override this.
	protected SQLiteOpenHelper onCreateSQLiteOpenHelper(Context context) {
		DatabaseHistory[] history = onCreateDatabaseHistory();
		return new DatabaseHistoryBuilder(context, getClass().getSimpleName()+".db", history);
	}
	
	abstract protected UriOps[] onCreateUriOps();
	abstract protected DatabaseHistory[] onCreateDatabaseHistory();

	@Override
	public boolean onCreate() {
		mDbHelper = onCreateSQLiteOpenHelper(getContext());
		mUriOpsMatcher = new UriOpsMatcher(onCreateUriOps());
		return true;
	}
	
	/**
	 * get the proper interface from the UriOps for the uri
	 * 
	 * @param <T>
	 * @param uri
	 * @param clazz
	 * @return if the uri is correct and the UriOps has the interface, return the interface. Otherwise throw exception. never null.
	 */
	@SuppressWarnings("unchecked")
	protected final <T> T getUriOp(Uri uri, Class<T> clazz) {
		UriOps ops = mUriOpsMatcher.match(uri);
		try {
			return (T)ops;
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Unknown URI: " + uri.toString());
		}
	}
	
	private static class UriOpsMatcher {
		private UriMatcher mUriMatcher;
		private UriOps[] mUriOpsArray;
		
		private UriOpsMatcher(UriOps[] uriOps) {
			mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
			for (int i=0; i<uriOps.length; i++)
				mUriMatcher.addURI(uriOps[i].mAuthority, uriOps[i].mUriPath, i);
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
	
	// default UriOps has no op.
	public static class UriOps {
		private String mAuthority;
		private String mUriPath;
		
		protected List<Integer> mUriWildcardPosition;
		protected static boolean isUriWildcard(String s) {
			// '#' : any number, '*' : any text
			return "#*".indexOf(s) >= 0;
		}
		
		public UriOps(String authority, String uriPath) {
			mAuthority = authority;
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
		
		protected String getUriPath() {
			return mUriPath;
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
		
		return getUriOp(uri, OpQuery.class).query(getContext().getContentResolver(), db, uri, projection, selection, selectionArgs, sortOrder);
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return null;
		
		Uri result = getUriOp(uri, OpInsert.class).insert(db, uri, values);
		if (result != null)
			getContext().getContentResolver().notifyChange(result, null);
		return result;
	}

	@Override
	public int bulkInsert(Uri uri, ContentValues[] values) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		int result = getUriOp(uri, OpInsert.class).bulkInsert(db, uri, values);
		if (result > 0)
			getContext().getContentResolver().notifyChange(uri, null);
		return result;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		int result = getUriOp(uri, OpUpdate.class).update(db, uri, values, selection, selectionArgs);
		if (result > 0)
			getContext().getContentResolver().notifyChange(uri, null);
		return result;
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		int result = getUriOp(uri, OpDelete.class).delete(db, uri, selection, selectionArgs);
		if (result > 0)
			getContext().getContentResolver().notifyChange(uri, null);
		return result;
	}
	
	@Override
	public String getType(Uri uri) {
		return getUriOp(uri, OpGetType.class).getType();
	}
	
	protected static interface DatabaseHistory {
		void upgrade(SQLiteDatabase db);
	}
	
	private class DatabaseHistoryBuilder extends SQLiteOpenHelper {
		
		private DatabaseHistory[] mHistory;

		public DatabaseHistoryBuilder(Context context, String name, DatabaseHistory[] history) {
			super(context, name, null, history.length);
			mHistory = history;
			android.util.Log.i("nora", "dhh name="+name+", version="+history.length);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			android.util.Log.i("nora", "dhh onCreate");
			buildDatabaseHistory(db, 0, mHistory.length); // build from nothing
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			android.util.Log.i("nora", "dhh onUpgrade "+oldVersion+" -> "+newVersion);
			buildDatabaseHistory(db, oldVersion, newVersion); // build from old version
		}
		
		private void buildDatabaseHistory(SQLiteDatabase db, int oldVersion, int newVersion) {
			for (int v=oldVersion; v<newVersion; v++) {
				android.util.Log.i("nora", "dhh buildDatabaseHistory "+v);
				mHistory[v].upgrade(db);
			}
		}
	}
	
	public static interface OpQuery {
		Cursor query(ContentResolver cr, SQLiteDatabase db, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder);
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
	
	public static interface OpGetType {
		String getType();
	}
}