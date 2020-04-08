package com.jeffmony.downloader.utils;

import android.os.Process;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WorkerThreadHandler {

    private static final String TAG = "WorkerThreadHandler";

    private static final int CPU_COUNT =
            Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE = 1;
    private static final int QUEUE_SIZE = 2 ^ CPU_COUNT;

    private static class MediaWorkerThreadFactory implements ThreadFactory {
        public Thread newThread(Runnable r) { return new MediaWorkerThread(r); }
    }

    private static class MediaWorkerThread extends Thread {
        public MediaWorkerThread(Runnable r) {
            super(r, "vivo_media_worker_pool_thread");
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            long startTime = System.currentTimeMillis();
            super.run();
            long endTime = System.currentTimeMillis();
            LogUtils.i(TAG, "ProxyCacheThreadHandler execution time: " +
                    (endTime - startTime));
        }
    }

    private static final BlockingQueue<Runnable> sThreadPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(QUEUE_SIZE);
    private static final ExecutorService sThreadPoolExecutor =
            new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
                    TimeUnit.SECONDS, sThreadPoolWorkQueue,
                    new MediaWorkerThreadFactory(),
                    new ThreadPoolExecutor.DiscardOldestPolicy());

    public static Future submitCallbackTask(Callable task) {
        return sThreadPoolExecutor.submit(task);
    }

    public static Future submitRunnableTask(Runnable task) {
        return sThreadPoolExecutor.submit(task);
    }
}
