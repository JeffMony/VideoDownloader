package com.jeffmony.downloader.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class VideoDownloadSQLiteHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "video_download_info.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_VIDEO_DOWNLOAD_INFO = "video_download_info";
    public static class Columns {
        public static final String _ID = "_id";
        public static final String VIDEO_URL = "video_url";
        public static final String MIME_TYPE = "mime_type";
        public static final String DOWNLOAD_TIME = "download_time";
        public static final String PERCENT = "percent";
        public static final String TASK_STATE = "task_state";
        public static final String VIDEO_TYPE = "video_type";
        public static final String CACHED_LENGTH = "cached_length";
        public static final String TOTAL_LENGTH = "total_length";
        public static final String CACHED_TS = "cached_ts";
        public static final String TOTAL_TS = "total_ts";
        public static final String FILE_NAME = "file_name";
        public static final String FILE_PATH = "file_path";
    }

    public VideoDownloadSQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createVideoDownloadInfoTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    private void createVideoDownloadInfoTable(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VIDEO_DOWNLOAD_INFO);
        db.execSQL("CREATE TABLE " + TABLE_VIDEO_DOWNLOAD_INFO + "("
                + Columns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Columns.VIDEO_URL + " TEXT, "
                + Columns.MIME_TYPE + " TEXT, "
                + Columns.DOWNLOAD_TIME + " BIGINT, "
                + Columns.PERCENT + " REAL, "
                + Columns.TASK_STATE + " TEXT, "
                + Columns.VIDEO_TYPE + " TINYINT, "
                + Columns.CACHED_LENGTH + " BIGINT, "
                + Columns.TOTAL_LENGTH + " BIGINT, "
                + Columns.CACHED_TS + " INTEGER, "
                + Columns.TOTAL_TS + " INTEGER , "
                + Columns.FILE_NAME + " TEXT, "
                + Columns.FILE_PATH + " TEXT);");
    }
}
