package dev.easetheworld.easycontentprovider;

import java.util.List;

import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

// basic database operations(query, insert, update, delete)
public class BaseUriOps extends EasyContentProvider.UriOps implements
	EasyContentProvider.OpQuery,
	EasyContentProvider.OpInsert,
	EasyContentProvider.OpUpdate,
	EasyContentProvider.OpDelete {
	
	private String mTableName;
	
	// assume table name is the first segment of the path
	public BaseUriOps(String uriPath) {
		this(uriPath, getFirstSegment(uriPath));
	}
	
	private static String getFirstSegment(String uriPath) {
		String tableName = uriPath;
		int slashIndex = tableName.indexOf('/');
		if (slashIndex >= 0)
			tableName = tableName.substring(0, slashIndex);
		return tableName;
	}
	
	public BaseUriOps(String uriPath, String tableName) {
		super(uriPath);
		mTableName = tableName;
	}
	
	public String getTableName() {
		return mTableName;
	}
	
	private String mUriSelection;
	
	/**
	 * To handle the uri including sub path segments like xxx/#/#.
	 * Each '?' in selection will be matched to '#' or '*' in uri path.
	 * @param selection
	 * @return
	 */
	public BaseUriOps setUriSelection(String... selection) {
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
	
	@Override
	public Cursor query(SQLiteDatabase db, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		selection = appendUriSelection(selection);
		selectionArgs = appendUriSelectionArgs(uri, selectionArgs);
		
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(mTableName);
		return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
	}
	
	@Override
	public Uri insert(SQLiteDatabase db, Uri uri, ContentValues values) {
		Uri newUri = null;
		long rowId = db.insert(mTableName, null, values);
		if (rowId >= 0)
			newUri = ContentUris.withAppendedId(uri, rowId);
		return newUri;
	}
	
	@Override
	public int bulkInsert(SQLiteDatabase db, Uri uri, ContentValues[] values) {
		int result = 0;
		// use DatabaseUtils.InsertHelper to reuse compiled sql statement
		DatabaseUtils.InsertHelper insertHelper = new DatabaseUtils.InsertHelper(db, mTableName);
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
		return result;
	}

	@Override
	public int update(SQLiteDatabase db, Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		selection = appendUriSelection(selection);
		selectionArgs = appendUriSelectionArgs(uri, selectionArgs);
		
		return db.update(mTableName, values, selection, selectionArgs);
	}
	
	@Override
	public int delete(SQLiteDatabase db, Uri uri, String selection, String[] selectionArgs) {
		selection = appendUriSelection(selection);
		selectionArgs = appendUriSelectionArgs(uri, selectionArgs);
		
		return db.delete(mTableName, selection, selectionArgs);
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
}