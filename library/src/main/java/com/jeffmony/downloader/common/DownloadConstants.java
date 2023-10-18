package com.jeffmony.downloader.common;

public class DownloadConstants {

    public static final String TAG = "video_downloader";
    public static final int READ_TIMEOUT = 2 * 60 * 1000;
    public static final int CONN_TIMEOUT = 2 * 60 * 1000;
    public static final int CONCURRENT = 1;

    public static final int MSG_DOWNLOAD_DEFAULT = 0;
    public static final int MSG_DOWNLOAD_PENDING = 1;
    public static final int MSG_DOWNLOAD_PREPARE = 2;
    public static final int MSG_DOWNLOAD_START = 3;
    public static final int MSG_DOWNLOAD_PROCESSING = 4;
    public static final int MSG_DOWNLOAD_PAUSE = 5;
    public static final int MSG_DOWNLOAD_SUCCESS = 6;
    public static final int MSG_DOWNLOAD_ERROR = 7;

    public static final int MSG_FETCH_DOWNLOAD_INFO = 100;
    public static final int MSG_DELETE_ALL_FILES = 101;

}
