package com.easetheworld.easycontentprovidertest;

import java.io.FileNotFoundException;
import java.util.List;

import android.content.ContentResolver;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import dev.easetheworld.easycontentprovider.BaseUriOps;
import dev.easetheworld.easycontentprovider.EasyContentProvider;

public class CheeseProvider extends EasyContentProvider {

	public static final String AUTHORITY = "com.easetheworld.easycontentprovidertest.provider";
	
	public static class CheeseTable  {
		public static final String TABLE_NAME = "cheese";
		public static final Uri CONTENT_URI = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).path(TABLE_NAME).build();
		public static final String ID = BaseColumns._ID;
		public static final String NAME = "name";
		public static final String MEMO = "memo";
		public static final String FLAG1 = "flag1";
		
		public static final int COLUMN_INDEX_ID = 0;
		public static final int COLUMN_INDEX_NAME = 1;
		public static final int COLUMN_INDEX_MEMO = 2;
	}
	
	@Override
	protected DatabaseHistory[] onCreateDatabaseHistory() {
		DatabaseHistory[] history = new DatabaseHistory[] {
			new DatabaseHistory() {
				@Override
				public void upgrade(SQLiteDatabase db) {
					db.execSQL("CREATE TABLE IF NOT EXISTS " + CheeseTable.TABLE_NAME + "(" +
							 CheeseTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
							 CheeseTable.NAME + " TEXT NOT NULL," +
							 CheeseTable.MEMO + " TEXT" +
							");");
				}
			},
			new DatabaseHistory() {
				@Override
				public void upgrade(SQLiteDatabase db) {
					db.execSQL("ALTER TABLE " + CheeseTable.TABLE_NAME + " ADD COLUMN " + CheeseTable.FLAG1 + " INTEGER;");
				}
			},
		};
		return history;
	}

	@Override
	protected UriOps[] onCreateUriOps() {
		return new UriOps[] {
			new BaseUriOps(AUTHORITY, CheeseTable.TABLE_NAME),
			new BaseUriOps(AUTHORITY, CheeseTable.TABLE_NAME+"/#") // '#' must be added before '*' because '*' includes '#' 
				.setUriSelection(CheeseTable.ID+"=?"),
			new BaseUriOps(AUTHORITY, CheeseTable.TABLE_NAME+"/*")
				.setUriSelection(CheeseTable.NAME+"=?"),
			new OpenFileUriOps(AUTHORITY, "file/*"),
		};
	}
	
	// example of extending UriOps and using it.
	private static class OpenFileUriOps extends UriOps {
		public OpenFileUriOps(String authority, String uriPath) {
			super(authority, uriPath);
		}

		ParcelFileDescriptor openFile(Uri uri, String mode) {
			List<String> segments = uri.getPathSegments();
			String arg = segments.get(mUriWildcardPosition.get(0)); // first wild card in uri
			android.util.Log.i("OpenFileUriOps", "arg="+arg+", mode="+mode);
			// open file or do something
			return null;
		}
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		UriOps ops = getUriOps(uri);
		if (ops instanceof OpenFileUriOps)
			return ((OpenFileUriOps)ops).openFile(uri, mode);
		else
			return null;
	}
}