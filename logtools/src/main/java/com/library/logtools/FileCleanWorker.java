package com.library.logtools;

import android.content.Context;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 周期性后台任务，用于自动清理指定目录
 */
 class FileCleanWorker {

    private static ScheduledExecutorService scheduler;

    public static void start(final Context context, final File folder, final long intervalHours) {
        if (scheduler != null && !scheduler.isShutdown()) {
            return; // 已启动
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                AutoManagerLogTools.checkAndCleanAsync(folder);
            }
        }, 0, intervalHours, TimeUnit.HOURS); // 每 intervalHours 小时执行一次
    }

    public static void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}

