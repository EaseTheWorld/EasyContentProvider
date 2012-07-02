package com.easetheworld.easycontentprovidertest;

import java.io.FileNotFoundException;

import android.content.AsyncQueryHandler;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.easetheworld.easycontentprovidertest.CheeseProvider.CheeseContract;

public class TestActivity extends FragmentActivity implements LoaderManager.LoaderCallbacks<Cursor> {
	
	private SimpleCursorAdapter mAdapter;
	private AsyncQueryHandler mQueryHandler;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        ListView lv = (ListView)findViewById(android.R.id.list);
        
        mAdapter = new SimpleCursorAdapter(this,
                android.R.layout.simple_list_item_2, null,
                new String[] { CheeseContract.NAME, CheeseContract.MEMO },
                new int[] { android.R.id.text1, android.R.id.text2},
                0); 
        lv.setAdapter(mAdapter);
        registerForContextMenu(lv);
        
        getSupportLoaderManager().initLoader(0, null, this);
        
		mQueryHandler = new AsyncQueryHandler(getContentResolver()) {

			@Override
			protected void onDeleteComplete(int token, Object cookie, int result) {
				super.onDeleteComplete(token, cookie, result);
			}

			@Override
			protected void onInsertComplete(int token, Object cookie, Uri uri) {
				super.onInsertComplete(token, cookie, uri);
			}

			@Override
			protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
				// TODO Auto-generated method stub
				super.onQueryComplete(token, cookie, cursor);
			}

			@Override
			protected void onUpdateComplete(int token, Object cookie, int result) {
				// TODO Auto-generated method stub
				super.onUpdateComplete(token, cookie, result);
			}
		};
    }

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		return new CursorLoader(this, CheeseContract.CONTENT_URI, null, null, null, null);
//		return new CursorLoader(this, Uri.withAppendedPath(CheeseTable.CONTENT_URI, "Kugelkase"), null, null, null, null);
//		return new CursorLoader(this, Uri.withAppendedPath(CheeseTable.CONTENT_URI, "10"), null, null, null, null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		mAdapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
	}
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add("Reset Cheese Data");
		return super.onCreateOptionsMenu(menu);
	}
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		new Cheeses.InsertRandomCheeseDataTask(this).execute();
		return super.onOptionsItemSelected(item);
	}
	
    private static final int MENU_DELETE = 0;
    private static final int MENU_MEMO = 1;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, MENU_DELETE, 0, "Delete");
		menu.add(0, MENU_MEMO, 1, "Memo");
	}
	
    @Override
	public boolean onContextItemSelected(MenuItem item) {
    	AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
    	switch(item.getItemId()) {
    	case MENU_DELETE:
	    	mQueryHandler.startDelete(0, null, Uri.withAppendedPath(CheeseContract.CONTENT_URI, ""+info.id), null, null);
    		break;
    	case MENU_MEMO:
    		try {
				getContentResolver().openFileDescriptor(Uri.parse("content://"+CheeseProvider.AUTHORITY+"/file/"+info.id), "r");
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
    		break;
    	}
		return super.onContextItemSelected(item);
    }
}