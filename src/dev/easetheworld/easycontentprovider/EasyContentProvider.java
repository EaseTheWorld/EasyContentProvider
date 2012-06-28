package dev.easetheworld.easycontentprovider;

import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public abstract class EasyContentProvider extends ContentProvider {
	
	private SQLiteOpenHelper mDbHelper;
	private UriOpsMatcher mUriOpsMatcher;
	
	public class UriOpsMatcher {
		private String mAuthority;
		private UriMatcher mUriMatcher;
		private List<UriOps> mOpsList;
		
		public UriOpsMatcher(String authority) {
			mAuthority = authority;
			mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
			mOpsList = new ArrayList<UriOps>();
		}
		
		// assume table name is the first segment of the path
		public UriOps addUriOps(String path) {
			String tableName = path;
			int slashIndex = tableName.indexOf('/');
			if (slashIndex >= 0)
				tableName = tableName.substring(0, slashIndex);
			return addUriOps(path, tableName);
		}
		
		public UriOps addUriOps(String path, String tableName) {
			UriOps ops = new UriOps(tableName);
			ops.setContentType(getDefaultContentType(path, mAuthority));
			mUriMatcher.addURI(mAuthority, path, mOpsList.size());
			mOpsList.add(ops);
			return ops;
		}
		
		private UriOps getUriOps(Uri uri) {
			int code = mUriMatcher.match(uri);
			if (code == -1)
				throw new IllegalArgumentException("Unknown URL: " + uri.toString());
			else {
				android.util.Log.i("nora", "getUriOps "+uri+", type="+mOpsList.get(code).getType());
				return mOpsList.get(code);
			}
		}
	}
		
	// if path is "cheeses", type is "vnd.android.cursor.dir/authority.cheeses"
	// if path is "cheeses/#", type is "vnd.android.cursor.item/authority.cheeses"
	// if path is "cheeses/#/sub", type is "vnd.android.cursor.dir/authority.cheeses.sub"
	// if path is "cheeses/#/sub/#", type is "vnd.android.cursor.item/authority.cheeses.sub"
	protected String getDefaultContentType(String path, String authority) {
		StringBuilder sb = new StringBuilder();
		if (path.endsWith("#")) {
			sb.append("vnd.android.cursor.item/");
		} else {
			sb.append("vnd.android.cursor.dir/");
		}
		sb.append(authority);
		if (path.contains("/")) {
			String[] segments = path.split("/");
			for (String segment : segments) {
				if (segment.equals("#"))
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
	
	public static final int OP_QUERY = 1;
	public static final int OP_INSERT = 1<<1;
	public static final int OP_UPDATE = 1<<2;
	public static final int OP_DELETE = 1<<3;
	public static final int OP_READ = OP_QUERY;
	public static final int OP_WRITE = OP_INSERT | OP_UPDATE | OP_DELETE;
	
	public static class UriOps {
		private String mContentType;
		private String mTableName;
		private String[] mPathSegmentColumns;
		
		private int mPermission = OP_READ | OP_WRITE; // allow all operations by default
		
		private UriOps(String tableName) {
			mTableName = tableName;
		}
		
		public UriOps setContentType(String type) {
			mContentType = type;
			return this;
		}
		
		public UriOps setPermission(int permission) {
			mPermission = permission;
			return this;
		}
		
		// To handle the uri including sub path segments like xxx/#/#. 
		// paghSegmentColumns must have same size as uri.getPathSegments().size() - 1
		// Set null for static segments.
		// For example,
		// if uri is table1/#/sub/#,
		// pathSegmentColumns should be {"column1", null, "column2"}
		public UriOps setPathSegmentColumns(String[] pathSegmentColumns) {
			mPathSegmentColumns = pathSegmentColumns;
			return this;
		}
		
		private void checkPermission(int op) {
			if ((mPermission & op) == 0)
				throw new SecurityException("Permission denied.");
		}
		
		protected Cursor query(ContentResolver cr, SQLiteDatabase db, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
			checkPermission(OP_QUERY);
			
			SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
			qb.setTables(mTableName);
			if (mPathSegmentColumns != null) {
				for (int i=0; i<mPathSegmentColumns.length; i++) {
					qb.appendWhere(mPathSegmentColumns[i]+ "=" + uri.getPathSegments().get(i+1));
				}
			}
			android.util.Log.d("nora", "query uri="+uri.getPathSegments().size()+", "+uri.getPathSegments());
			Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			if (c != null) {
				c.setNotificationUri(cr, uri);
			}
			return c;
		}
		
		protected Uri insert(ContentResolver cr, SQLiteDatabase db, Uri uri, ContentValues values) {
			return insert(cr, db, uri, values, true);
		}
		
		protected Uri insert(ContentResolver cr, SQLiteDatabase db, Uri uri, ContentValues values, boolean notify) {
			checkPermission(OP_INSERT);
			
			Uri newUri = null;
			long rowId = db.insert(mTableName, null, values);
			if (rowId >= 0) {
				newUri = ContentUris.withAppendedId(uri, rowId);
				if (notify)
					cr.notifyChange(uri, null);
			}
			return newUri;
		}
		
		protected int update(ContentResolver cr, SQLiteDatabase db, Uri uri, ContentValues values, String selection, String[] selectionArgs) {
			checkPermission(OP_UPDATE);
			
			selection = appendPathSegmentSelection(selection, uri);
			int rows = db.update(mTableName, values, selection, selectionArgs);
			if (rows > 0) {
				cr.notifyChange(uri, null);
			}
			return rows;
		}
		
		protected int delete(ContentResolver cr, SQLiteDatabase db, Uri uri, String selection, String[] selectionArgs) {
			checkPermission(OP_DELETE);
			
			selection = appendPathSegmentSelection(selection, uri);
			int rows = db.delete(mTableName, selection, selectionArgs);
			if (rows > 0) {
				cr.notifyChange(uri, null);
			}
			return rows;
		}
		
		protected int bulkInsert(ContentResolver cr, SQLiteDatabase db, Uri uri, ContentValues[] values) {
			int numValues = values.length;
	        for (int i = 0; i < numValues; i++) {
	            insert(cr, db, uri, values[i], false);
	        }
			cr.notifyChange(uri, null);
	        return numValues;
		}
		
		private String getType() {
			return mContentType;
		}
		
		private String appendPathSegmentSelection(String selection, Uri uri) {
			if (mPathSegmentColumns == null)
				return selection;
			
			StringBuilder sb = new StringBuilder();
			if (selection != null)
				sb.append(selection);
			for (int i=0; i<mPathSegmentColumns.length; i++) {
				if (mPathSegmentColumns[i] == null)
					continue;
				if (sb.length() == 0)
					sb.append("(");
				else
					sb.append(" AND (");
				sb.append(mPathSegmentColumns[i]);
				sb.append("=");
				sb.append(uri.getPathSegments().get(i+1));
				sb.append(")");
			}
			return sb.toString();
		}
	}

	@Override
	public boolean onCreate() {
		String authority = getAuthority();
		android.util.Log.i("nora", "authority="+authority);
		mDbHelper = onCreateSQLiteOpenHelper(getContext());
		mUriOpsMatcher = new UriOpsMatcher(authority);
		onAddUriOps(mUriOpsMatcher);
		return true;
	}
	
	private String getAuthority() {
		try {
			ComponentName cn = new ComponentName(getContext(), getClass());
			ProviderInfo pi = getContext().getPackageManager().getProviderInfo(cn, 0);
			return pi.authority;
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	abstract protected SQLiteOpenHelper onCreateSQLiteOpenHelper(Context context);
	
	abstract protected void onAddUriOps(UriOpsMatcher matcher);
	
	@Override
	public String getType(Uri uri) {
		return mUriOpsMatcher.getUriOps(uri).getType();
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		SQLiteDatabase db = mDbHelper.getReadableDatabase();
		if (db == null) return null;
		UriOps ops = mUriOpsMatcher.getUriOps(uri);
		return ops.query(getContext().getContentResolver(), db, uri, projection, selection, selectionArgs, sortOrder);
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return null;
		
		UriOps ops = mUriOpsMatcher.getUriOps(uri);
		return ops.insert(getContext().getContentResolver(), db, uri, values);
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		UriOps ops = mUriOpsMatcher.getUriOps(uri);
		return ops.update(getContext().getContentResolver(), db, uri, values, selection, selectionArgs);
	}
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		UriOps ops = mUriOpsMatcher.getUriOps(uri);
		return ops.delete(getContext().getContentResolver(), db, uri, selection, selectionArgs);
	}

	@Override
	public ContentProviderResult[] applyBatch(
			ArrayList<ContentProviderOperation> operations)
			throws OperationApplicationException {
		// TODO Auto-generated method stub
		return super.applyBatch(operations);
	}

	@Override
	public int bulkInsert(Uri uri, ContentValues[] values) {
		SQLiteDatabase db = mDbHelper.getWritableDatabase();
		if (db == null) return 0;
		
		UriOps ops = mUriOpsMatcher.getUriOps(uri);
		return ops.bulkInsert(getContext().getContentResolver(), db, uri, values);
	}
}