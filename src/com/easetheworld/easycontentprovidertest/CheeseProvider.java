package com.easetheworld.easycontentprovidertest;

import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.provider.BaseColumns;
import dev.easetheworld.easycontentprovider.EasyContentProvider;

public class CheeseProvider extends EasyContentProvider {

	public static final String AUTHORITY = "com.easetheworld.easycontentprovidertest.provider";
	
	public static class CheeseTable  {
		public static final String TABLE_NAME = "cheese";
		public static final Uri CONTENT_URI = Uri.withAppendedPath(Uri.parse("content://"+AUTHORITY), TABLE_NAME);
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
			new UriOps(AUTHORITY, CheeseTable.TABLE_NAME),
			new UriOps(AUTHORITY, CheeseTable.TABLE_NAME+"/#") // '#' must be added before '*' because '*' includes '#' 
				.setUriSelection(CheeseTable.ID+"=?"),
			new UriOps(AUTHORITY, CheeseTable.TABLE_NAME+"/*")
				.setUriSelection(CheeseTable.NAME+"=?"),
		};
	}
}