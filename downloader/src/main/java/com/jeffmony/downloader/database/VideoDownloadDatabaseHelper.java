package com.jeffmony.downloader.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.jeffmony.downloader.model.VideoTaskItem;
import com.jeffmony.downloader.utils.LogUtils;

public class VideoDownloadDatabaseHelper {

    private static final String TAG = "VideoDownloadDatabaseHelper";
    private VideoDownloadSQLiteHelper mSQLiteHelper;

    public VideoDownloadDatabaseHelper(Context context) {
        mSQLiteHelper = new VideoDownloadSQLiteHelper(context);
        mSQLiteHelper.getWritableDatabase();
    }

    public void deleteAllDownloadInfos() {
        SQLiteDatabase db = mSQLiteHelper.getWritableDatabase();
        if (db == null) {
            return;
        }
        db.beginTransaction();
        try {
            db.delete(VideoDownloadSQLiteHelper.TABLE_VIDEO_DOWNLOAD_INFO, null, null);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            LogUtils.w(TAG, "deleteAllDownloadInfos failed, exception = " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

    public void deleteDownloadItemByUrl(VideoTaskItem item) {
        SQLiteDatabase db = mSQLiteHelper.getWritableDatabase();
        if (db == null) {
            return;
        }
        db.beginTransaction();
        try {
            String whereClause = VideoDownloadSQLiteHelper.Columns.VIDEO_URL + " = ? ";
            String whereArgs[] = {item.getUrl()};
            db.delete(VideoDownloadSQLiteHelper.TABLE_VIDEO_DOWNLOAD_INFO, whereClause, whereArgs);
            db.setTransactionSuccessful();
            LogUtils.i(TAG, "deleteDownloadItemByUrl url="+item.getUrl());
        } catch (Exception e) {
            LogUtils.w(TAG, "deleteDownloadItemByUrl failed, exception = " + e.getMessage());
        } finally {
            db.endTransaction();
        }
    }

}
