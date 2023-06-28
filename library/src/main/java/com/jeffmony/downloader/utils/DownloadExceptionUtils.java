package com.jeffmony.downloader.utils;

import com.jeffmony.downloader.VideoDownloadException;

import java.io.FileNotFoundException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public class DownloadExceptionUtils {

    /**
     * https://cdn7-video.hnqiyouquan.com:8081/20200223/XA0V108f/index.m3u8  下载慢
     * http://hao.zuida-youku.com/20170618/lQl8AJpD/index.m3u8               SocketTimeoutException
     * https://ae01.alicdn.com/kf/U04e25e8e7f7e46b7a7d98986ecf61205D.png?.m3u8  not M3U8?
     */
    private static final int UNKNOWN_ERROR = -1;
    private static final int SOCKET_TIMEOUT_ERROR = 5000; // http://hao.zuida-youku.com/20170704/v3xK5MLu/index.m3u8
    private static final int FILE_NOT_FOUND_ERROR = 5001; // https://hao.czybjz.com/20171023/GBGFCDHf/index.m3u8
    private static final int UNKNOWN_HOST_ERROR = 5002; // https://cn1.sw92.com/avid5e3b9a3acab6d/index.m3u8

    //Custom Exception
    private static final int FILE_LENGTH_FETCHED_ERROR = 5100; // http://cdn.videobanker.com/video.mp4?id=13297481&token=eafb2012f9d225b647fb36d556954e73&quality=480
    private static final int M3U8_FILE_CONTENT_ERROR = 5101; // http://video.dnsoy.com:8091/9720170813/RK3RLO562/550kb/hls/index.m3u8
    private static final int MIMETYPE_NULL_ERROR = 5102; // https://api.xiaomingming.org/cloud/h
    private static final int MIMETYPE_NOT_FOUND = 5103; // https://sina.com-h-sina.com/share/fb5ac34d9ac3cc3883230cb5b2b417bb
    public static final int LIVE_M3U8_ERROR = 5104;

    public static final String FILE_LENGTH_FETCHED_ERROR_STRING = "File Length Cannot be fetched";
    public static final String M3U8_FILE_CONTENT_ERROR_STRING = "M3U8 File content error";
    public static final String MIMETYPE_NULL_ERROR_STRING = "MimeType is null";
    public static final String MIMETYPE_NOT_FOUND_STRING = "MimeType not found";
    public static final String VIDEO_INFO_EMPTY = "Video info is null";
    public static final String URL_SCHEMA_ERROR = "Cannot parse the request resource's schema";
    public static final String CREATE_CONNECTION_ERROR = "Create connection failed";
    public static final String FINAL_URL_EMPTY = "FinalUrl is null";
    public static final String REMOTE_M3U8_EMPTY = "Cannot find remote.m3u8 file";
    public static final String PROTOCOL_UNEXPECTED_END_OF_STREAM = "unexpected end of stream";
    public static final String RETRY_COUNT_EXCEED_WITH_THREAD_CONTROL_STRING = "Retry count exceeding with thread control";
    public static final String VIDEO_REQUEST_FAILED = "Video request failed";

    public static int getErrorCode(Throwable e) {
        if (e instanceof SocketTimeoutException) {
            return SOCKET_TIMEOUT_ERROR;
        } else if (e instanceof FileNotFoundException) {
            return FILE_NOT_FOUND_ERROR;
        } else if (e instanceof VideoDownloadException) {
            if (((VideoDownloadException) e).getMsg().equals(FILE_LENGTH_FETCHED_ERROR_STRING)) {
                return FILE_LENGTH_FETCHED_ERROR;
            } else if (((VideoDownloadException) e).getMsg().equals(M3U8_FILE_CONTENT_ERROR_STRING)) {
                return M3U8_FILE_CONTENT_ERROR;
            } else if (((VideoDownloadException) e).getMsg().equals(MIMETYPE_NULL_ERROR_STRING)) {
                return MIMETYPE_NULL_ERROR;
            } else if (((VideoDownloadException) e).getMsg().equals(MIMETYPE_NOT_FOUND_STRING)) {
                return MIMETYPE_NOT_FOUND;
            }
        } else if (e instanceof UnknownHostException) {
            return UNKNOWN_HOST_ERROR;
        }
        return UNKNOWN_ERROR;
    }
}
