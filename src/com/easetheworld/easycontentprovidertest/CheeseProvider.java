package com.easetheworld.easycontentprovidertest;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import dev.easetheworld.easycontentprovider.EasyContentProvider;

public class CheeseProvider extends EasyContentProvider {

	public static final String AUTHORITY = "com.easetheworld.easycontentprovidertest.provider";
	
	@Override
	protected SQLiteOpenHelper onCreateSQLiteOpenHelper(Context context) {
		return new DatabaseHelper(context);
	}
	
	@Override
	protected void onAddUriOps(UriOpsMatcher matcher) {
		matcher.addUriOps(AUTHORITY, CheeseTable.TABLE_NAME);
		matcher.addUriOps(AUTHORITY, CheeseTable.TABLE_NAME+"/#")
			.setPathSegmentColumns(new String[] {CheeseTable.ID});
	}
	
	

	// Database Schema

	public static class CheeseTable  {
		public static final String TABLE_NAME = "cheese";
		public static final Uri CONTENT_URI = Uri.withAppendedPath(Uri.parse("content://"+AUTHORITY), TABLE_NAME);
		public static final String ID = BaseColumns._ID;
		public static final String NAME = "name";
		public static final String MEMO = "memo";
		
		public static final int COLUMN_INDEX_ID = 0;
		public static final int COLUMN_INDEX_NAME = 1;
		public static final int COLUMN_INDEX_MEMO = 2;
	}
	
	// DatabaseHelper
	
	private static class DatabaseHelper extends SQLiteOpenHelper {
		private static final String NAME = "cheeses.db";
		private static final int VERSION = 1;
		
		private DatabaseHelper(Context context) {
			super(context, NAME, null, VERSION);
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE IF NOT EXISTS " + CheeseTable.TABLE_NAME + "(" +
					 CheeseTable.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
					 CheeseTable.NAME + " TEXT NOT NULL," +
					 CheeseTable.MEMO + " TEXT" +
					");");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		}
	}
}