package com.library.logtools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

public class FileKV {

    /**
     * 获取文件下一行行号（取最后一行第一个方括号中的数字）
     */
    public static int getLastLineNumber(File file) {
        int lastLineNumber = 0;
        if (file != null && file.exists() && file.length() > 0) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long length = raf.length();
                long pos = length - 1;
                StringBuilder sb = new StringBuilder();
                while (pos > 0) {
                    raf.seek(pos--);
                    char c = (char) raf.read();
                    if (c == '\n' && sb.length() > 0) break;
                    sb.insert(0, c);
                }
                String lastLine = sb.toString().trim();
                if (lastLine.startsWith("<")) {
                    int end = lastLine.indexOf(">");
                    lastLineNumber = Integer.parseInt(lastLine.substring(1, end));
                }
            } catch (Exception e) {
                lastLineNumber = 0;
            }
        }
        return lastLineNumber;
    }

    // 写入单个整型值
    public static void putValue(String fileName, int value) {
        File file = new File(fileName);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file, false); // 每次覆盖
            String strValue = String.valueOf(value);
            fos.write(strValue.getBytes("UTF-8"));
            fos.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                }
            }
        }
    }

    // 读取整型值
    public static int getValue(String fileName, int defValue) {
        File file = new File(fileName);
        if (!file.exists()) return defValue;

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            String strValue = new String(bytes, "UTF-8");
            return Integer.parseInt(strValue.trim());
        } catch (Exception e) {
            e.printStackTrace();
            return defValue;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                }
            }
        }
    }
}
