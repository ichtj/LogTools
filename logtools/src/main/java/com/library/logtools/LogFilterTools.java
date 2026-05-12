package com.library.logtools;

import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

class LogFilterTools {
    private static final String TAG = LogFilterTools.class.getSimpleName();
    private static final String FILTER_FILE_PREFIX = "filter_";
    private static final String FILTER_FILE_EXTENSION = ".txt";
    private static final SimpleDateFormat DATE_FILENAME_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final String KEY_LOG_FILTER_PATH = "log_filter_path";
    private static final String KEY_CONFIG_PATH = "key_log_filter_config_path";
    private static final String DEFAULT_CONFIG_FILE = "log_filter.config";

    private static volatile LogFilterTools instance;
    private final List<String> rulesKeywords = new ArrayList<>();
    private File configFile;
    private File logParentDirectory;
    private File currentLogFile;
    private static BufferedWriter writer;

    private static LogFilterTools getInstance() {
        if (instance == null) {
            synchronized (LogFilterTools.class) {
                if (instance == null) {
                    instance = new LogFilterTools();
                    instance.init();
                }
            }
        }
        return instance;
    }

    private void init() {
        File defaultDir = getDefaultFilterDir();
        logParentDirectory = new File(SPUtils.getString(KEY_LOG_FILTER_PATH, defaultDir.getAbsolutePath()));
        SPUtils.putString(KEY_LOG_FILTER_PATH, logParentDirectory.getAbsolutePath());
        configFile = new File(logParentDirectory, DEFAULT_CONFIG_FILE);
        SPUtils.putString(KEY_CONFIG_PATH, configFile.getAbsolutePath());
        rulesKeywords.addAll(readRulesToKeywords());
        resumeOrCreateLogFile();
    }

    private static File getDefaultFilterDir() {
        File logDir = new File(FaceLogTools.getLogDirectory());
        return new File(logDir, "filter");
    }

    private static synchronized void resumeOrCreateLogFile() {
        LogFilterTools tools = getInstance();
        closeWriter();
        if (!tools.logParentDirectory.exists() && !tools.logParentDirectory.mkdirs()) {
            Log.w(TAG, "Create filter log directory failed: " + tools.logParentDirectory.getAbsolutePath());
        }
        String today = DATE_FILENAME_FORMAT.format(new Date());
        int fileIndex = 1;

        File[] existingFiles = tools.logParentDirectory.listFiles((dir, name) ->
                name.startsWith(FILTER_FILE_PREFIX + today + "_") && name.endsWith(FILTER_FILE_EXTENSION));
        if (existingFiles != null && existingFiles.length > 0) {
            Arrays.sort(existingFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            File lastFile = existingFiles[existingFiles.length - 1];
            if (lastFile.length() < FaceLogTools.getMaxFileSize()) {
                tools.currentLogFile = lastFile;
            } else {
                fileIndex = FaceLogTools.getFileIndexFromName(lastFile.getName()) + 1;
                tools.currentLogFile = new File(tools.logParentDirectory, FILTER_FILE_PREFIX + today + "_" + fileIndex + FILTER_FILE_EXTENSION);
            }
        } else {
            tools.currentLogFile = new File(tools.logParentDirectory, FILTER_FILE_PREFIX + today + "_" + fileIndex + FILTER_FILE_EXTENSION);
        }

        try {
            writer = new BufferedWriter(new FileWriter(tools.currentLogFile, true));
        } catch (IOException e) {
            Log.e(TAG, "Open filter log writer failed", e);
        }
    }

    public static void addKeyword(String keyword) {
        LogFilterTools tools = getInstance();
        if (TextUtils.isEmpty(keyword) || tools.rulesKeywords.contains(keyword)) {
            return;
        }
        List<String> readRules = readRulesToKeywords();
        if (!readRules.contains(keyword)) {
            readRules.add(keyword);
        }
        tools.rulesKeywords.add(keyword);
        writeRules(readRules, tools.configFile);
    }

    private static void writeRules(List<String> rules, File configFile) {
        File parentFile = configFile.getParentFile();
        if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
            Log.w(TAG, "Create filter config directory failed: " + parentFile.getAbsolutePath());
            return;
        }
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(configFile, false))) {
            for (int i = 0; i < rules.size(); i++) {
                if (i > 0) {
                    bw.newLine();
                }
                bw.write(rules.get(i));
            }
        } catch (IOException e) {
            Log.e(TAG, "Write filter rules failed", e);
        }
    }

    public static void writeKeywordToFile(int currentLine, String content) {
        if (content == null) {
            return;
        }
        LogFilterTools tools = getInstance();
        if (tools.rulesKeywords.isEmpty()) {
            return;
        }
        String keywordStr = null;
        for (String keyword : tools.rulesKeywords) {
            if (!TextUtils.isEmpty(keyword) && content.contains(keyword)) {
                keywordStr = keyword;
                break;
            }
        }
        if (keywordStr == null) {
            return;
        }
        if (!tools.logParentDirectory.exists() && !tools.logParentDirectory.mkdirs()) {
            Log.w(TAG, "Create filter log directory failed: " + tools.logParentDirectory.getAbsolutePath());
            return;
        }
        if (tools.currentLogFile == null || !tools.currentLogFile.exists()
                || tools.currentLogFile.length() >= FaceLogTools.getMaxFileSize()) {
            resumeOrCreateLogFile();
        }
        try {
            writer.write("<" + currentLine + "> [" + keywordStr + "] " + content);
            writer.newLine();
        } catch (IOException e) {
            Log.e(TAG, "Write filter log failed", e);
        }
    }

    public static List<String> readRulesToKeywords() {
        String filePath = SPUtils.getString(KEY_CONFIG_PATH, new File(getDefaultFilterDir(), DEFAULT_CONFIG_FILE).getAbsolutePath());
        List<String> result = new ArrayList<>();
        File file = new File(filePath);
        if (!file.isFile() || !file.exists()) {
            return result;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String lineTxt;
            while ((lineTxt = br.readLine()) != null) {
                if (!TextUtils.isEmpty(lineTxt)) {
                    result.add(lineTxt.split("\\+")[0]);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Read filter rules failed", e);
        }
        return result;
    }

    private static void closeWriter() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "Close filter log writer failed", e);
            }
            writer = null;
        }
    }
}
