package com.library.logtools;

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceLogTools {
    private static final String TAG=FaceLogTools.class.getSimpleName();
    private static FaceLogTools instance;
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String LOG_FILE_PREFIX = "log_";
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final SimpleDateFormat DATE_FLIENAME_FORMAT = new SimpleDateFormat("yyyyMMdd");
    private static final SimpleDateFormat DATE_LOG_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static boolean isShowLog = true;

    private static File logDirectory;
    private static File currentLogFile;
    private static BufferedWriter writer;
    private static ExecutorService executorService;
    private static Context mContext;
    private static final String KEY_LOG_DIR = "face_log_dir";
    private static final String KEY_MAX_FILE_SIZE = "max_file_size";


    public static Context getmContext() {
        return mContext;
    }

    private FaceLogTools() {
        // 私有构造函数，防止实例化
    }


    public static void initialize(Context context, boolean showLog) {
        if (instance == null) {
            synchronized (FaceLogTools.class) {
                if (instance == null) {
                    instance = new FaceLogTools();
                    mContext = context;
                    isShowLog=showLog;
                    executorService = Executors.newSingleThreadExecutor();
                    putLogDirectory (getLogDirectory ());
                    if (logDirectory != null && !logDirectory.exists()) {
                        logDirectory.mkdirs();
                    }
                    FileCleanWorker.start(context, logDirectory, 1);
                    resumeOrCreateLogFile();
                }
            }
        }
    }

    /**
     * 设置最大文件大小 单位：字节
     * @param size
     */
    public static void setMaxFileSize(long size){
        // 这里可以添加逻辑来设置最大文件大小
        SPUtils.putLong (KEY_MAX_FILE_SIZE,size);
    }

    /**
     * 获取最大文件大小 单位：字节
     */
    public static long getMaxFileSize(){
        // 这里可以添加逻辑来设置最大文件大小
        return SPUtils.getLong (KEY_MAX_FILE_SIZE,DEFAULT_MAX_FILE_SIZE);
    }

    public static String getLogDirectory() {
        return SPUtils.getString (KEY_LOG_DIR,Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath ());
    }

    public static void putLogDirectory(String directoryPath) {
        if (!TextUtils.isEmpty (directoryPath)){
            SPUtils.putString (KEY_LOG_DIR,directoryPath);
            logDirectory= new File (directoryPath);
        }
        if (logDirectory != null && !logDirectory.exists()) {
            logDirectory.mkdirs();
        }
        Log.d (TAG, "putLogDirectory: "+getLogDirectory ());
        FileCleanWorker.stop();
        FileCleanWorker.start(mContext, logDirectory, 1);
        resumeOrCreateLogFile();
    }

    // 优化后的日志文件创建与恢复逻辑
    private static synchronized void resumeOrCreateLogFile() {
        closeWriter();
        String today = DATE_FLIENAME_FORMAT.format(new Date());
        int fileIndex = 1;

        // 获取当天所有日志文件
        File[] existingFiles = logDirectory.listFiles((dir, name) -> name.startsWith(LOG_FILE_PREFIX + today + "_") && name.endsWith(LOG_FILE_EXTENSION));
        if (existingFiles != null && existingFiles.length > 0) {
            Arrays.sort(existingFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            File lastFile = existingFiles[existingFiles.length - 1];
            if (lastFile.length() < getMaxFileSize ()) {
                currentLogFile = lastFile;
                fileIndex = getFileIndexFromName(lastFile.getName());
            } else {
                fileIndex = getFileIndexFromName(lastFile.getName()) + 1;
                currentLogFile = new File(logDirectory, LOG_FILE_PREFIX + today + "_" + fileIndex + LOG_FILE_EXTENSION);
            }
        } else {
            currentLogFile = new File(logDirectory, LOG_FILE_PREFIX + today + "_" + fileIndex + LOG_FILE_EXTENSION);
        }

        try {
            writer = new BufferedWriter(new FileWriter(currentLogFile, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void V(String tag, String message){
        write(Level.V, BufferType.MAIN, android.os.Process.myPid(),tag, message, true, false);
    }
    public static void D(String tag, String message){
        write(Level.D, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, false);
    }
    public static void E(String tag, String message){
        write(Level.E, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, false);
    }
    public static void I(String tag, String message){
        write(Level.I, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, false);
    }
    public static void F(String tag, String message){
        write(Level.F, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, false);
    }
    public static void V(String tag, String message,boolean showStackTrace){
        write(Level.V, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, showStackTrace);
    }
    public static void D(String tag, String message,boolean showStackTrace){
        write(Level.D, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, showStackTrace);
    }
    public static void E(String tag, String message,boolean showStackTrace){
        write(Level.E, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, showStackTrace);
    }
    public static void I(String tag, String message,boolean showStackTrace){
        write(Level.I, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, showStackTrace);
    }
    public static void F(String tag, String message,boolean showStackTrace){
        write(Level.F, BufferType.MAIN,android.os.Process.myPid(), tag, message, true, showStackTrace);
    }

    private static void printLog(Level type, String tagStr, Object message,StackTraceElement stackTraceElement, boolean showStackTrace) {
        String msg;
        if (!isShowLog) {
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        String tag=null;
        if (showStackTrace){
            String className = stackTraceElement.getFileName();
            String methodName = stackTraceElement.getMethodName();
            int lineNumber = stackTraceElement.getLineNumber();
            tag = (tagStr == null ? className : tagStr);
            methodName = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
            stringBuilder.append("[ (").append(className).append(":").append(lineNumber).append(")#").append(methodName).append(" ] ");
        } else{
            tag=tagStr;
        }
        if (message == null) {
            msg = "Log with null Object";
        } else {
            msg = message.toString();
        }
        if (msg != null) {
            stringBuilder.append(msg);
        }
        String logStr = stringBuilder.toString();
        if (type == Level.V) {
            Log.v (tag, logStr);
        } else if (type == Level.D) {
            Log.d (tag, logStr);
        } else if (type == Level.I) {
            Log.i (tag, logStr);
        } else if (type == Level.W) {
            Log.w (tag, logStr);
        } else if (type == Level.E) {
            Log.e (tag, logStr);
        } else if (type == Level.F) {
            Log.wtf (tag, logStr);
        }
    }

    public static void addKeyword(String keyword){
        LogFilterTools.addKeyword (keyword);
    }

    public static void write(Level level,BufferType bufferType,int pid,String tag, String message, boolean writeToFile, boolean showStackTrace) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement callerStack=null;
        if (showStackTrace&&stackTrace.length > 2) {
            callerStack = stackTrace[3];
        }
        printLog (level, tag, message,callerStack,showStackTrace);
        if (executorService == null) {
            throw new IllegalStateException("FaceLogTools is not initialized. Call initialize() first.");
        }
        StackTraceElement finalCallerStack = callerStack;
        executorService.execute(() -> {
            try {
                StringBuilder logEntryBuilder = new StringBuilder();
                int nextLineNumber = LineNumberUtil.getNextLineNumber (currentLogFile.getAbsolutePath ());
                Log.d (TAG, "write: nextLineNumber>>"+nextLineNumber);
                logEntryBuilder.append ("<"+nextLineNumber+"> ");
                logEntryBuilder.append ("["+pid+"] ");
                logEntryBuilder.append ("["+bufferType.name ()+"] ");
                logEntryBuilder.append ("["+level.name ()+"] ");
                logEntryBuilder.append ("["+DATE_LOG_FORMAT.format(new Date())+"] ");
                if (showStackTrace && finalCallerStack != null){
                    String className = finalCallerStack.getFileName();
                    String methodName = finalCallerStack.getMethodName();
                    int lineNumber = finalCallerStack.getLineNumber();
                    methodName = methodName.substring(0, 1).toUpperCase() + methodName.substring(1);
                    logEntryBuilder.append("[ (").append(className).append(":").append(lineNumber).append(")#").append(methodName).append(" ] ");
                }else{
                    logEntryBuilder.append("["+tag+"] ");
                }
                logEntryBuilder.append (message);

                LogFilterTools.writeToFile (nextLineNumber,message);
                // 写入文件
                if (writeToFile) {
                    synchronized (FaceLogTools.class) {
                        // 检查日期是否变更
                        String today = DATE_FLIENAME_FORMAT.format(new Date());
                        if (!currentLogFile.getName().contains(today)) {
                            resumeOrCreateLogFile();
                        }
                        if (currentLogFile.length() >= getMaxFileSize ()) {
                            createNewLogFile();
                        }
                        // 检查文件是否被删除
                        if (!currentLogFile.exists()) {
                            resumeOrCreateLogFile();
                        }
                        try {
                            writer.write(logEntryBuilder.toString() + System.lineSeparator());
                            writer.flush();
                        } catch (IOException e) {
                            // 文件句柄失效时尝试恢复
                            resumeOrCreateLogFile();
                            try {
                                writer.write(logEntryBuilder.toString() + System.lineSeparator());
                                writer.flush();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
    }

    public static List<String> getLogFiles() {
        if (logDirectory == null) {
            throw new IllegalStateException("FaceLogTools is not initialized. Call initialize() first.");
        }
        List<String> fileList = new ArrayList<>();
        if (logDirectory.exists()) {
            File[] files = logDirectory.listFiles((dir, name) -> name.startsWith(LOG_FILE_PREFIX) && name.endsWith(LOG_FILE_EXTENSION));
            if (files != null) {
                for (File file : files) {
                    fileList.add(file.getName());
                }
            }
        }
        return fileList;
    }

    // 新建当天新的日志文件
    private static synchronized void createNewLogFile() {
        closeWriter();
        String today = DATE_FLIENAME_FORMAT.format(new Date());
        int fileIndex = 1;

        File[] existingFiles = logDirectory.listFiles((dir, name) -> name.startsWith(LOG_FILE_PREFIX + today + "_") && name.endsWith(LOG_FILE_EXTENSION));
        if (existingFiles != null && existingFiles.length > 0) {
            Arrays.sort(existingFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            File lastFile = existingFiles[existingFiles.length - 1];
            fileIndex = getFileIndexFromName(lastFile.getName()) + 1;
        }

        currentLogFile = new File(logDirectory, LOG_FILE_PREFIX + today + "_" + fileIndex + LOG_FILE_EXTENSION);
        try {
            writer = new BufferedWriter(new FileWriter(currentLogFile, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 获取文件名中的索引
    static int getFileIndexFromName(String fileName) {
        try {
            String[] parts = fileName.split("_");
            String indexPart = parts[2].replace(LOG_FILE_EXTENSION, "");
            return Integer.parseInt(indexPart);
        } catch (Exception e) {
            return 1;
        }
    }

    private static void closeWriter() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}