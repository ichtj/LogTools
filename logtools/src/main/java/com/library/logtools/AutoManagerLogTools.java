package com.library.logtools;

import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoManagerLogTools {

    private static final String TAG = "AutoManagerLogTools";

    // ======== 配置项（线程安全） ========
    private static volatile long maxFolderSize = 500L * 1024 * 1024;  // 500MB
    private static volatile int maxFileCount = 10000;
    private static volatile double cleanTargetRatio = 0.8;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("AutoManagerLogTools-Worker");
        return t;
    });


    // ======== 配置方法 ========
    public static void setMaxFolderSize(long bytes) {
        if (bytes > 0) maxFolderSize = bytes;
    }

    public static void setMaxFileCount(int count) {
        if (count > 0) maxFileCount = count;
    }

    public static void setCleanTargetRatio(double ratio) {
        if (ratio > 0 && ratio < 1) cleanTargetRatio = ratio;
    }

    public static void printCurrentConfig() {
        Log.d(TAG, "=== AutoManagerLogTools Config ===");
        Log.d(TAG, "MaxFolderSize : " + maxFolderSize);
        Log.d(TAG, "MaxFileCount  : " + maxFileCount);
        Log.d(TAG, "CleanTarget   : " + (cleanTargetRatio * 100) + "%");
        Log.d(TAG, "===================================");
    }


    // ======== 异步清理入口 ========
    public static void checkAndCleanAsync(final File folder) {
        executor.execute(() -> cleanFolder(folder));
    }


    // ======== 核心清理逻辑（无递归 + 单次排序 + 高性能） ========
    private static void cleanFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) return;

        List<File> allFiles = new ArrayList<>();
        Deque<File> stack = new ArrayDeque<>();
        stack.push(folder);

        long totalSize = 0;
        int totalCount = 0;

        // --- 使用手动栈遍历所有文件（无递归） ---
        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] fs = dir.listFiles();
            if (fs == null) continue;

            for (File f : fs) {
                if (f.isDirectory()) {
                    stack.push(f);
                } else {
                    long len = f.length();
                    totalSize += len;
                    totalCount++;
                    allFiles.add(f);
                }
            }
        }

        if (totalSize <= maxFolderSize && totalCount <= maxFileCount) {
            return; // 不需要清理
        }

        // --- 按文件最后修改时间排序（只排序一次） ---
        allFiles.sort(Comparator.comparingLong(File::lastModified));

        long targetSize = (long) (maxFolderSize * cleanTargetRatio);
        int targetCount = (int) (maxFileCount * cleanTargetRatio);

        long curSize = totalSize;
        int curCount = totalCount;

        for (File f : allFiles) {
            if (curSize <= targetSize && curCount <= targetCount) break;

            long len = f.length();
            if (f.delete()) {
                curSize -= len;
                curCount--;
            } else {
                Log.w(TAG, "Delete failed: " + f.getAbsolutePath());
            }
        }

        // --- 删除空目录 ---
        deleteEmptyDirs(folder);
    }


    // ======== 清理空目录 ========
    private static void deleteEmptyDirs(File root) {
        Deque<File> stack = new ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] fs = dir.listFiles();
            if (fs == null) continue;

            boolean hasChild = false;
            for (File f : fs) {
                if (f.isDirectory()) {
                    stack.push(f);
                    hasChild = true;
                }
            }

            // 目录无子目录 & 无文件 -> 删除
            fs = dir.listFiles();
            if (fs != null && fs.length == 0 && !dir.equals(root)) {
                if (!dir.delete()) {
                    Log.w(TAG, "Failed delete empty dir: " + dir.getAbsolutePath());
                }
            }
        }
    }
}
