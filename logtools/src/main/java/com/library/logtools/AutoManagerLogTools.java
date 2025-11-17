package com.library.logtools;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class AutoManagerLogTools {

    private static final String TAG = "AutoManagerLogTools";

    // ====== 配置项 ======
    private static volatile long maxFolderSize = 500L * 1024 * 1024; // 500MB
    private static volatile int maxFileCount = 10000;               // 10k 文件
    private static volatile double cleanTargetRatio = 0.8;          // 清理到 80%

    private static final ExecutorService executor =
            Executors.newSingleThreadExecutor(new ThreadFactory () {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("AutoManagerLogTools-Worker");
                    return t;
                }
            });


    // ========= 配置方法 =========
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
        Log.d(TAG, "===== AutoManagerLogTools Config =====");
        Log.d(TAG, "maxFolderSize = " + maxFolderSize);
        Log.d(TAG, "maxFileCount  = " + maxFileCount);
        Log.d(TAG, "cleanTarget   = " + (cleanTargetRatio * 100) + "%");
    }


    // ========= 异步触发清理 =========
    public static void checkAndCleanAsync(final File folder) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                cleanFolder(folder);
            }
        });
    }


    // ========= 核心清理逻辑（兼容 Android 4.1.2） =========
    private static void cleanFolder(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) return;

        ArrayList<File> allFiles = new ArrayList<File>();
        Stack<File> dirStack = new Stack<File>();
        dirStack.push(folder);

        long totalSize = 0;
        int totalCount = 0;

        // -------- 手动栈遍历所有文件（避免递归） --------
        while (!dirStack.isEmpty()) {
            File dir = dirStack.pop();
            File[] items = dir.listFiles();
            if (items == null) continue;

            for (int i = 0; i < items.length; i++) {
                File f = items[i];
                if (f.isDirectory()) {
                    dirStack.push(f);
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

        // ------- 按时间排序（旧 → 新）--------
        Collections.sort(allFiles, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                long d = o1.lastModified() - o2.lastModified();
                return d < 0 ? -1 : (d > 0 ? 1 : 0);
            }
        });

        long targetSize = (long) (maxFolderSize * cleanTargetRatio);
        int targetCount = (int) (maxFileCount * cleanTargetRatio);

        long curSize = totalSize;
        int curCount = totalCount;

        // ------- 依次删除最旧文件 -------
        for (int i = 0; i < allFiles.size(); i++) {
            if (curSize <= targetSize && curCount <= targetCount) break;

            File f = allFiles.get(i);
            long len = f.length();

            if (f.delete()) {
                curSize -= len;
                curCount--;
            } else {
                Log.w(TAG, "Delete failed: " + f.getAbsolutePath());
            }
        }

        // -------- 最后清理所有空目录 --------
        deleteEmptyDirs(folder);
    }


    // ========= 删除空目录（兼容 Android 4.1.2） =========
    private static void deleteEmptyDirs(File root) {
        Stack<File> stack = new Stack<File>();
        stack.push(root);

        while (!stack.isEmpty()) {
            File dir = stack.pop();
            File[] children = dir.listFiles();
            if (children == null) continue;

            boolean hasDir = false;

            for (int i = 0; i < children.length; i++) {
                File f = children[i];
                if (f.isDirectory()) {
                    hasDir = true;
                    stack.push(f);
                }
            }

            children = dir.listFiles();
            if (!dir.equals(root) && children != null && children.length == 0) {
                if (!dir.delete()) {
                    Log.w(TAG, "Failed to delete empty dir: " + dir.getAbsolutePath());
                }
            }
        }
    }
}

