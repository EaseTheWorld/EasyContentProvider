package dev.easetheworld.easycontentprovider;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public abstract class EasyContentProvider extends ContentProvider {
	
	private SQLiteOpenHelper mDbHelper;
	private UriOpsMatcher mUriOpsMatcher;
	
	// TODO add version history block
	abstract protected SQLiteOpenHelper onCreateSQLiteOpenHelper(Context context);
	abstract protected UriOps[] onCreateUriOps();

	@Override
	public boolean onCreate() {
		mDbHelper = onCreateSQLiteOpenHelper(getContext());
		mUriOpsMatcher = new UriOpsMatcher(onCreateUriOps());
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
	
	public static class UriOps {
		private String mAuthority;
		private String mUriPath;
		
		private String mContentType;
		private String mTableName;
		
		private List<Integer> mUriWildcardPosition;
		private static boolean isUriWildcard(String s) {
			// '#' : any number, '*' : any text
			return "#*".indexOf(s) >= 0;
		}
		
		// assume table name is the first segment of the path
		public UriOps(String authority, String uriPath) {
			this(authority, uriPath, getFirstSegment(uriPath));
		}
		
		public UriOps(String authority, String uriPath, String tableName) {
			mAuthority = authority;
			mUriPath = uriPath;
			mTableName = tableName;
			
			// find the position of wild cards in uri path
			String[] segments = uriPath.split("/");
			for (int i=0; i<segments.length; i++) {
				if (isUriWildcard(segments[i])) {
					if (mUriWildcardPosition == null)
						mUriWildcardPosition = new ArrayList<Integer>();
					mUriWildcardPosition.add(i);
				}
			}
			
			mContentType = getDefaultContentType(authority, uriPath);
		}
		
		public String getUriPath() {
			return mUriPath;
		}
		
		public String getTableName() {
			return mTableName;
		}
		
		public UriOps setContentType(String type) {
			mContentType = type;
			return this;
		}
		
		public static final int OP_QUERY = 1;
		public static final int OP_INSERT = 1<<1;
		public static final int OP_UPDATE = 1<<2;
		public static final int OP_DELETE = 1<<3;
		public static final int OP_READ = OP_QUERY;
		public static final int OP_WRITE = OP_INSERT | OP_UPDATE | OP_DELETE;
		private int mPermission = OP_READ | OP_WRITE; // allow all operations by default
		
		public UriOps setPermission(int permission) {
			mPermission = permission;
			return this;
		}
		
		private String mUriSelection;
		
		/**
		 * To handle the uri including sub path segments like xxx/#/#.
		 * Each '?' in selection will be matched to '#' or '*' in uri path.
		 * @param selection
		 * @return
		 */
		public UriOps setUriSelection(String... selection) {
			if (selection != null) {
				if (selection.length == 1) {
					mUriSelection = selection[0];
				} else {
					StringBuilder sb = new StringBuilder();
					for (int i=0; i<selection.length; i++) {
						if (i == 0)
							sb.append("(");
						else
							sb.append(" AND (");
						sb.append(selection[i]);
						sb.append(")");
					}
					mUriSelection = sb.toString();
				}
			}
			return this;
		}
		
		private void checkPermission(int op) {
			if ((mPermission & op) == 0)
				throw new UnsupportedOperationException(mUriPath);
		}
		
		protected Cursor query(ContentResolver cr, SQLiteDatabase db, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
			checkPermission(OP_QUERY);
			
			selection = appendUriSelection(selection);
			selectionArgs = appendUriSelectionArgs(uri, selectionArgs);
			
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(mTableName);
			Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			if (c != null)
				c.setNotificationUri(cr, uri);
			return c;
		}
		
		protected Uri insert(SQLiteDatabase db, Uri uri, ContentValues values) {
			checkPermission(OP_INSERT);
			
			Uri newUri = null;
			long rowId = db.insert(mTableName, null, values);
			if (rowId >= 0)
				newUri = ContentUris.withAppendedId(uri, rowId);
			return newUri;
		}
		
		protected int update(SQLiteDatabase db, Uri uri, ContentValues values, String selection, String[] selectionArgs) {
			checkPermission(OP_UPDATE);
			
			selection = appendUriSelection(selection);
			selectionArgs = appendUriSelectionArgs(uri, selectionArgs);
			
			int rows = db.update(mTableName, values, selection, selectionArgs);
			return rows;
		}
		
		protected int delete(SQLiteDatabase db, Uri uri, String selection, String[] selectionArgs) {
			checkPermission(OP_DELETE);
			
			selection = appendUriSelection(selection);
			selectionArgs = appendUriSelectionArgs(uri, selectionArgs);
			
			int rows = db.delete(mTableName, selection, selectionArgs);
			return rows;
		}
		
		private String appendUriSelection(String selection) {
			if (TextUtils.isEmpty(selection))
				return mUriSelection;
			else if (TextUtils.isEmpty(mUriSelection))
				return selection;
			else
		        return "(" + selection + ") AND (" + mUriSelection + ")";
		}
		
		private String[] appendUriSelectionArgs(Uri uri, String[] selectionArgs) {
			if (mUriSelection != null && mUriWildcardPosition != null) {
				// concat selectionArgs and uriSelectionArgs
				String[] uriSelectionArgs = new String[mUriWildcardPosition.size()];
				List<String> segments = uri.getPathSegments();
				for (int i=0; i<uriSelectionArgs.length; i++)
					uriSelectionArgs[i] = segments.get(mUriWildcardPosition.get(i));
				return appendSelectionArgs(selectionArgs, uriSelectionArgs);
			} else
				return selectionArgs;
		}
		
		// from honeycomb DatabaseUtils.java
		/**
	     * Appends one set of selection args to another. This is useful when adding a selection
	     * argument to a user provided set.
	     */
	    private static String[] appendSelectionArgs(String[] originalValues, String[] newValues) {
	        if (originalValues == null || originalValues.length == 0) {
	            return newValues;
	        }
	        String[] result = new String[originalValues.length + newValues.length];
	        System.arraycopy(originalValues, 0, result, 0, originalValues.length);
	        System.arraycopy(newValues, 0, result, originalValues.length, newValues.length);
	        return result;
	    }
		
		private static String getFirstSegment(String uriPath) {
			String tableName = uriPath;
			int slashIndex = tableName.indexOf('/');
			if (slashIndex >= 0)
				tableName = tableName.substring(0, slashIndex);
			return tableName;
		}
		
		// if path is "cheeses", type is "vnd.android.cursor.dir/authority.cheeses"
		// if path is "cheeses/#", type is "vnd.android.cursor.item/authority.cheeses"
		// if path is "cheeses/#/sub", type is "vnd.android.cursor.dir/authority.cheeses.sub"
		// if path is "cheeses/#/sub/#", type is "vnd.android.cursor.item/authority.cheeses.sub"
		private static String getDefaultContentType(String authority, String path) {
			StringBuilder sb = new StringBuilder();
			// check last character is wild card
			if (isUriWildcard(path.substring(path.length()-1))) {
				sb.append("vnd.android.cursor.item/");
			} else {
				sb.append("vnd.android.cursor.dir/");
			}
			sb.append(authority);
			if (path.contains("/")) {
				String[] segments = path.split("/");
				for (String segment : segments) {
					if (isUriWildcard(segment))
						continue;
					sb.append(".");
					sb.append(segment);
				}
			} else {
				sb.append(".");
				sb.append(path);
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
	public String getType(Uri uri) {
		return getUriOps(uri).mContentType;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		SQLiteDatabase db = mDbHelper.getReadableDatabase();
		if (db == null) return null;
		
		UriOps ops = getUriOps(uri);
		return ops.query(getContext().getContentResolver(), db, uri, projection, selection, selectionArgs, sortOrder);
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return null;
		
		UriOps ops = getUriOps(uri);
		Uri result = ops.insert(db, uri, values);
		if (result != null)
			getContext().getContentResolver().notifyChange(uri, null);
		return result;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		UriOps ops = getUriOps(uri);
		int result = ops.update(db, uri, values, selection, selectionArgs);
		if (result >= 0)
			getContext().getContentResolver().notifyChange(uri, null);
		return result;
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		UriOps ops = getUriOps(uri);
		int result = ops.delete(db, uri, selection, selectionArgs);
		if (result >= 0)
			getContext().getContentResolver().notifyChange(uri, null);
		return result;
	}

	@Override
	public int bulkInsert(Uri uri, ContentValues[] values) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		int result = 0;
		UriOps ops = getUriOps(uri);
		
		// use DatabaseUtils.InsertHelper to reuse compiled sql statement
		DatabaseUtils.InsertHelper insertHelper = new DatabaseUtils.InsertHelper(db, ops.mTableName);
		db.beginTransaction();
		try {
	        for (int i = 0; i < values.length; i++) {
	        	if (insertHelper.insert(values[i]) >= 0)
	        		result++;
	        }
	        db.setTransactionSuccessful();
	    } finally {
	        db.endTransaction();
	        insertHelper.close();
	    }
		
        // notify once
        if (result > 0)
			getContext().getContentResolver().notifyChange(uri, null);
        return result;
	}
}