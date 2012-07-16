Android EasyContentProvider
===========================

If you want to add a uri to ContentProvider, You have to ...

1. Declare a integer code for the new uri path.
2. UriMatcher.addURI(authority, path, code)
3. add switch-case for code and content type in getType().
4. add switch-case for code and db statement in query().
5. add switch-case for code and db statement in insert().
6. add switch-case for code and db statement in update().
7. add switch-case for code and db statement in delete().

There should be an easier way. 
With `EasyContentProvider`, all you have to do is ...

1. add `new BaseUriOps(path, tableName)` in return array of `EasyContentProvider.onCreateUriOps()`.

In `EasyContentProvider`, there are also some useful apis to manage database version history and much more.

Feature 1 : Uri and Operations
--------------------------
- `EasyContentProvider.onCreateUriOps()` returns array of `UriOps` which has uri path and uri-specific operations.
- `EasyContentProvider.getAuthority()` returns the authority of this provider. So you shouldn't write authority in uri path.
- You can put any ContentProvider apis in your own `UriOps` with `EasyContentProvider.getUriOps()`. Look at the source and example.
- `UriOps.getType()` will automatically make android-style content-type for
  many items(ex. "vnd.android.cursor.dir/com.your.authority.cheeses") and
  one item(ex. "vnd.android.cursor.item/com.your.authority.cheeses") based on the uri path.
  If you have your own content-type(probably most of you don't), use `UriOps.setType()`.

Feature 2 : BaseUriOps
----------------------
- This class has default db operations(query, insert, bulkInsert, update, delete).
- 
- `BaseUriOps.bulkInsert()` reuses the same sql statement and notify only once to increase the performance.
- With `BaseUriOps.setUriSelection()`, you can handle uri path wild card(xxx/#, xxx/*) easily with `BaseUriOps.setUriSelection()`. Each argument(ex. _id=?) will be mapped to each wild card.
- With `BaseUriOps.setPermission()`, you can restrict other apps to read(query) and write(insert, update, delete) the uri. If both read and write are false, the uri is for private use only.
- If you have your own implementation, just override some functions or extend `UriOps` and implement `OpQuery/OpInsert/OpUpdate/OpDelete` 
  which is used in `EasyContentProvider.query()/insert()/bulkInsert()/update()/delete()`.

Feature 3 : Batch operation
---------------------------
- `EasyContentProvider.applyBatch()` handles many insert/update/delete in one transaction and notify only once to increase performance.

Feature 4 : Database version history
------------------------------------
- If your SQLiteOpenHelper has just sql statements, try `EasyContentProvider.onCreateDatabaseHistory()` which returns array of `EasyContentProvider.DatabaseHistory`.
  When your db needs modification(upgrade), add an item at the end of the array. The length of the history array is the version (This makes sense, doesn't it?)
  So you don't have to care about db version manually.
- db file name is the same as ContentProvider.
- As you know, history is read-only. So you must not change the old history, you can only add at the end.
- If you have your own SQLiteOpenHelper, return it in `EasyContentProvider.onCreateSQLiteOpenHelper()` without calling super.
  and give dummy implementation for `EasyContentProvider.onCreateDatabaseHistory()` which will not be called anyway.

Release Notes
-------------
- v0.1.0 : Initial Release

Source
------
https://github.com/EaseTheWorld/EasyContentProvider

Made by **EaseTheWorld**