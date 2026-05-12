package com.face.logtools;

import java.io.DataOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SystemTimeHelper {

    /**
     * 设置系统时间（需要设备已 root）
     * @param timestampMillis 时间戳，单位毫秒
     * @return true 表示命令执行成功，false 表示失败
     */
    public static boolean setSystemTime(long timestampMillis) {
        // 1. 转换成 date 命令需要的格式：YYYYMMDD.HHMMSS
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd.HHmmss", Locale.getDefault());
        String dateStr = sdf.format(new Date(timestampMillis));

        // 2. 执行 shell 命令
        DataOutputStream os = null;
        Process suProcess = null;
        try {
            suProcess = Runtime.getRuntime().exec("su"); // 需要 root
            os = new DataOutputStream(suProcess.getOutputStream());

            // 设置时间
            os.writeBytes("date -s " + dateStr + "\n");

            // 同步硬件时钟
            os.writeBytes("clock -w\n"); // 部分设备需要，写入 RTC

            os.writeBytes("exit\n");
            os.flush();

            int exitValue = suProcess.waitFor();
            return exitValue == 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (suProcess != null) suProcess.destroy();
            } catch (Exception ignored) {}
        }
    }
}

