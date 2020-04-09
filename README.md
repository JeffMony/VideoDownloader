# VideoDownloader
专注视频下载，hls/mp4等等视频文件下载；


视频下载SDK功能
> * 下载整视频，如mp4/mkv/mov/3gp等视频
> * 下载HLS，即M3U8视频
> * M3U8 视频下载完成，会生成一个本地的local.m3u8文件
> * 视频下载完成，可以点击播放视频文件

视频下载SDK接入<br>
1.应用启动的时候注册download config
```
File file = VideoDownloadUtils.getVideoCacheDir(this);
if (!file.exists()) {
    file.mkdir();
}
VideoDownloadConfig config = new VideoDownloadManager.Build(this)
    .setCacheRoot(file)
    .setUrlRedirect(true)
    .setTimeOut(VideoDownloadManager.READ_TIMEOUT, VideoDownloadManager.CONN_TIMEOUT)
    .setConcurrentCount(VideoDownloadManager.CONCURRENT)
    .setIgnoreCertErrors(true)
    .buildConfig();
VideoDownloadManager.getInstance().initConfig(config);
```
2.注册download listener回调，这个回调只要注册一次就行了，是全局回调
```
VideoDownloadManager.getInstance().setGlobalDownloadListener(mListener);

private DownloadListener mListener = new DownloadListener() {

    @Override
    public void onDownloadDefault(VideoTaskItem item) {}

    @Override
    public void onDownloadPending(VideoTaskItem item) {}

    @Override
    public void onDownloadPrepare(VideoTaskItem item) {}

    @Override
    public void onDownloadStart(VideoTaskItem item) {}

    @Override
    public void onDownloadProgress(VideoTaskItem item) {}

    @Override
    public void onDownloadSpeed(VideoTaskItem item) {}

    @Override
    public void onDownloadPause(VideoTaskItem item) {}

    @Override
    public void onDownloadError(VideoTaskItem item) {}

    @Override
    public void onDownloadSuccess(VideoTaskItem item) {}
};
```
VideoTaskItem中信息介绍
```
private String mUrl;            //下载视频的url
private int mTaskState;         //当前任务的状态
private String mMimeType;       // 视频url的mime type
private int mErrorCode;         //当前任务下载错误码
private int mVideoType;         //当前文件类型
private M3U8 mM3U8;             //M3U8结构,如果非M3U8,则为null
private float mSpeed;           //当前下载速度, getSpeedString 函数可以将速度格式化
private float mPercent;         //当前下载百分比, 0 ~ 100,是浮点数
private long mDownloadSize;     //已下载大小, getDownloadSizeString 函数可以将大小格式化
private long mTotalSize;        //文件总大小, M3U8文件无法准确获知
```

VideoTaskState下载状态信息介绍
```
public class VideoTaskState {
    public static final int DEFAULT = 0;//默认状态
    public static final int PENDING = -1;//下载排队
    public static final int PREPARE = 1;//下载准备中
    public static final int START = 2;  //开始下载
    public static final int DOWNLOADING = 3;//下载中
    public static final int PROXYREADY = 4; //视频可以边下边播
    public static final int SUCCESS = 5;//下载完成
    public static final int ERROR = 6;//下载出错
    public static final int PAUSE = 7;//下载暂停
    public static final int ENOSPC = 8;//空间不足
}
```
3.启动下载
```
VideoDownloadManager.getInstance().startDownload(item);
```
4.暂停下载
```
VideoDownloadManager.getInstance().pauseDownloadTask(item.getUrl());
```
5.恢复下载
```
VideoDownloadManager.getInstance().pauseDownloadTask(item.getUrl());
```


欢迎关注我的公众号JeffMony，我会持续为你带来音视频---算法---Android---python 方面的知识分享
![](./files/JeffMony.jpg)