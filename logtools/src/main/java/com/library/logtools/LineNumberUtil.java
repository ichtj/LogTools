package com.library.logtools;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class LineNumberUtil {
    private static final String TAG=LineNumberUtil.class.getSimpleName();

    /**
     * 获取文件下一行行号（取最后一行第一个方括号中的数字）
     */
    public static int getNextLineNumber(String filePath) {
        int lastLineNumber = 0;
        try (RandomAccessFile raf = new RandomAccessFile(filePath, "r")) {
            String line;
            while ((line = raf.readLine()) != null) {
                int start = line.indexOf('<');
                int end = line.indexOf('>');
                if (start != -1 && end != -1 && end > start + 1) {
                    String numStr = line.substring(start + 1, end);
                    try {
                        lastLineNumber = Integer.parseInt(numStr);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "读取文件失败: " + e.getMessage());
        }
        return lastLineNumber + 1;
    }
}

