package com.library.logtools;

import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoManagerLogTools {
    private static final String TAG = AutoManagerLogTools.class.getSimpleName();
    private static long maxFolderSize = 500L * 1024L * 1024L; // 默认 500MB
    private static int maxFileCount = 10000;                  // 默认 1 万个文件
    private static double cleanTargetRatio = 0.8;             // 默认清理到 80% 阈值

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("AutoManagerLogTools-Worker");
        return t;
    });

    /** =============== 可修改参数的方法 =============== */

    private static boolean isValid(long value) {
        return value > 0;
    }

    private static boolean isValid(double value) {
        return value > 0.0 && value < 1.0;
    }

    /** 设置最大文件夹大小（单位：字节） */
    public static void setMaxFolderSize(long bytes) {
        if (isValid(bytes)) {
            maxFolderSize = bytes;
        }
    }

    /** 设置最大文件数量 */
    public static void setMaxFileCount(int count) {
        if (isValid(count)) {
            maxFileCount = count;
        }
    }

    /** 设置清理比例（0.0 ~ 1.0），清理到该比例以下 */
    public static void setCleanTargetRatio(double ratio) {
        if (isValid(ratio)) {
            cleanTargetRatio = ratio;
        }
    }

    /** 打印当前配置参数（可选调试） */
    public static void printCurrentConfig() {
        Log.d(TAG, "=== FolderCleaner Config ===");
        Log.d(TAG, "MaxFolderSize: " + maxFolderSize + " bytes");
        Log.d(TAG, "MaxFileCount : " + maxFileCount);
        Log.d(TAG, "CleanTarget  : " + (cleanTargetRatio * 100) + "%");
        Log.d(TAG, "=============================");
    }

    /** =============== 核心功能部分 =============== */

    /** 异步执行清理任务 */
    public static void checkAndCleanAsync(final File folder) {
        executor.execute(() -> cleanFolderIfNeeded(folder));
    }

   /** 实际清理逻辑 */
    private static void cleanFolderIfNeeded(File folder) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        long totalSize = 0L;
        for (File f : files) {
            totalSize += f.length();
        }

        if (totalSize > maxFolderSize || files.length > maxFileCount) {
            // 按修改时间升序（旧文件在前）
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    long diff = o1.lastModified() - o2.lastModified();
                    return diff < 0 ? -1 : (diff > 0 ? 1 : 0);
                }
            });

            long targetSize = (long) (maxFolderSize * cleanTargetRatio);
            long currentSize = totalSize;

            for (File f : files) {
                if (currentSize <= targetSize) break;

                if (f.isFile()) {
                    long len = f.length();
                    if (f.delete()) {
                        currentSize -= len;
                    } else {
                        Log.w(TAG, "Failed to delete file: " + f.getAbsolutePath());
                    }
                } else {
                    deleteRecursive(f);
                }
            }
        }
    }

    /** 递归删除子目录 */
    private static void deleteRecursive(File dir) {
        if (dir.isDirectory()) {
            File[] list = dir.listFiles();
            if (list != null) {
                for (File child : list) {
                    deleteRecursive(child);
                }
            }
        }
        if (!dir.delete()) {
            Log.w(TAG, "Failed to delete directory: " + dir.getAbsolutePath());
        }
    }
}