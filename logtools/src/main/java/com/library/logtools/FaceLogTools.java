package com.library.logtools;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 应用内日志写入工具。
 * <p>
 * 该类同时支持输出到 Android Logcat 和写入本地日志文件。文件写入通过单线程异步队列完成，
 * 并使用固定容量队列限制内存占用；当写入速度长期高于落盘速度时，新日志会被丢弃并在后续
 * 日志中记录丢弃数量，避免调用方在高频写日志场景下触发 OOM。
 * </p>
 * <p>
 * 使用前必须先调用 {@link #initialize(Context, boolean)}。默认日志目录优先使用应用私有
 * 外部目录 {@code getExternalFilesDir("logs")}，因此普通场景不需要申请公共存储权限。
 * </p>
 */
@SuppressLint("StaticFieldLeak")
public class FaceLogTools {
    private static final String TAG = FaceLogTools.class.getSimpleName();
    private static FaceLogTools instance;
    private static final long DEFAULT_MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String LOG_FILE_PREFIX = "log_";
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final SimpleDateFormat DATE_FILENAME_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final SimpleDateFormat DATE_LOG_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
    private static final int BUFFER_BYTE_SIZE = 3 * 1024;
    private static final int LOG_QUEUE_CAPACITY = 2048;
    private static final String KEY_LOG_DIR = "face_log_dir";
    private static final String KEY_MAX_FILE_SIZE = "max_file_size";

    private static boolean isShowLog = true;
    private static File logDirectory;
    private static File currentLogFile;
    private static File lineNumberFile;
    private static BufferedWriter writer;
    private static ExecutorService executorService;
    private static Context mContext;
    private static final StringBuilder logBuffer = new StringBuilder();
    private static int bufferBytes = 0;
    private static int nextLineNumber = 1;
    private static final AtomicLong droppedLogCount = new AtomicLong();

    /**
     * 获取初始化时保存的应用上下文。
     *
     * @return 应用级 {@link Context}，未初始化时可能为 {@code null}
     */
    public static Context getmContext() {
        return mContext;
    }

    private FaceLogTools() {
    }

    /**
     * 初始化日志工具。
     * <p>
     * 该方法只会在首次调用时生效。内部会保存 {@code context.getApplicationContext()}，
     * 创建日志写入线程，恢复或创建当天日志文件，并启动日志目录清理任务。
     * </p>
     *
     * @param context 用于获取存储目录和 SharedPreferences 的上下文，不能为 {@code null}
     * @param showLog 是否同时输出到 Android Logcat
     * @throws IllegalArgumentException 当 {@code context} 为 {@code null} 时抛出
     */
    public static void initialize(Context context, boolean showLog) {
        if (context == null) {
            throw new IllegalArgumentException("context == null");
        }
        if (instance == null) {
            synchronized (FaceLogTools.class) {
                if (instance == null) {
                    instance = new FaceLogTools();
                    mContext = context.getApplicationContext();
                    isShowLog = showLog;
                    executorService = createLogExecutor();
                    putLogDirectory(getLogDirectory());
                    FileCleanWorker.start(mContext, logDirectory, 1);
                    resumeOrCreateLogFile();
                }
            }
        }
    }

    /**
     * 创建日志写入线程池。
     * <p>
     * 线程池使用单线程保证日志写入顺序；队列容量固定，队列满时只统计丢弃数量，
     * 不再继续缓存待写任务，避免内存无限增长。
     * </p>
     *
     * @return 用于异步写日志的单线程执行器
     */
    private static ExecutorService createLogExecutor() {
        ThreadFactory threadFactory = r -> {
            Thread thread = new Thread(r, "FaceLogTools-Writer");
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(LOG_QUEUE_CAPACITY),
                threadFactory,
                (r, executor) -> droppedLogCount.incrementAndGet()
        );
    }

    /**
     * 设置单个日志文件的最大大小。
     *
     * @param size 文件最大字节数，小于等于 0 的值不会被额外拦截，调用方应传入有效值
     */
    public static void setMaxFileSize(long size) {
        SPUtils.putLong(KEY_MAX_FILE_SIZE, size);
    }

    /**
     * 获取单个日志文件的最大大小。
     *
     * @return 当前配置的最大文件字节数，默认 10MB
     */
    public static long getMaxFileSize() {
        return SPUtils.getLong(KEY_MAX_FILE_SIZE, DEFAULT_MAX_FILE_SIZE);
    }

    /**
     * 获取当前日志目录。
     *
     * @return 已配置的日志目录；未配置时返回默认日志目录
     */
    public static String getLogDirectory() {
        return SPUtils.getString(KEY_LOG_DIR, getDefaultLogDirectory());
    }

    /**
     * 获取默认日志目录。
     * <p>
     * 已初始化时优先使用应用私有外部目录，外部目录不可用时退回应用内部文件目录；
     * 未初始化时保留旧行为，退回公共 DCIM 目录。
     * </p>
     *
     * @return 默认日志目录绝对路径
     */
    private static String getDefaultLogDirectory() {
        if (mContext != null) {
            File externalFilesDir = mContext.getExternalFilesDir("logs");
            if (externalFilesDir != null) {
                return externalFilesDir.getAbsolutePath();
            }
            return new File(mContext.getFilesDir(), "logs").getAbsolutePath();
        }
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
    }

    /**
     * 设置日志保存目录。
     * <p>
     * 设置后会立即切换 writer 到新目录，并重启目录清理任务。
     * </p>
     *
     * @param directoryPath 日志目录绝对路径；为空时使用当前目录或默认目录
     */
    public static void putLogDirectory(String directoryPath) {
        if (!TextUtils.isEmpty(directoryPath)) {
            SPUtils.putString(KEY_LOG_DIR, directoryPath);
            logDirectory = new File(directoryPath);
        }
        if (logDirectory == null) {
            logDirectory = new File(getDefaultLogDirectory());
        }
        if (!logDirectory.exists() && !logDirectory.mkdirs()) {
            Log.w(TAG, "Create log directory failed: " + logDirectory.getAbsolutePath());
        }
        Log.d(TAG, "putLogDirectory: " + logDirectory.getAbsolutePath());
        FileCleanWorker.stop();
        FileCleanWorker.start(mContext, logDirectory, 1);
        resumeOrCreateLogFile();
    }

    /**
     * 恢复或创建当天日志文件。
     * <p>
     * 如果当天最后一个日志文件未超过最大大小，则继续追加；否则按文件序号创建新文件。
     * 行号会从当前文件最后一行恢复。
     * </p>
     */
    private static synchronized void resumeOrCreateLogFile() {
        closeWriter();
        ensureLogDirectory();
        String today = DATE_FILENAME_FORMAT.format(new Date());
        int fileIndex = 1;
        File[] existingFiles = logDirectory.listFiles((dir, name) ->
                name.startsWith(LOG_FILE_PREFIX + today + "_") && name.endsWith(LOG_FILE_EXTENSION));
        if (existingFiles != null && existingFiles.length > 0) {
            Arrays.sort(existingFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            File lastFile = existingFiles[existingFiles.length - 1];
            if (lastFile.length() < getMaxFileSize()) {
                currentLogFile = lastFile;
            } else {
                fileIndex = getFileIndexFromName(lastFile.getName()) + 1;
                currentLogFile = new File(logDirectory, LOG_FILE_PREFIX + today + "_" + fileIndex + LOG_FILE_EXTENSION);
            }
        } else {
            currentLogFile = new File(logDirectory, LOG_FILE_PREFIX + today + "_" + fileIndex + LOG_FILE_EXTENSION);
        }

        lineNumberFile = new File(logDirectory, "line_number.txt");
        nextLineNumber = currentLogFile.exists() ? FileKV.getLastLineNumber(currentLogFile) + 1 : 1;
        persistNextLineNumber();
        try {
            writer = new BufferedWriter(new FileWriter(currentLogFile, true));
        } catch (IOException e) {
            Log.e(TAG, "Open log writer failed", e);
        }
    }

    /**
     * 写入 Verbose 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void V(String tag, String message) {
        write(Level.V, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, false);
    }

    /**
     * 写入 Debug 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void D(String tag, String message) {
        write(Level.D, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, false);
    }

    /**
     * 写入 Error 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void E(String tag, String message) {
        write(Level.E, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, false);
    }

    /**
     * 写入 Info 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void I(String tag, String message) {
        write(Level.I, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, false);
    }

    /**
     * 写入 Fatal 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void F(String tag, String message) {
        write(Level.F, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, false);
    }

    /**
     * 写入 Verbose 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     * @param showStackTrace 是否在日志中附加调用位置
     */
    public static void V(String tag, String message, boolean showStackTrace) {
        write(Level.V, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, showStackTrace);
    }

    /**
     * 写入 Debug 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     * @param showStackTrace 是否在日志中附加调用位置
     */
    public static void D(String tag, String message, boolean showStackTrace) {
        write(Level.D, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, showStackTrace);
    }

    /**
     * 写入 Error 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     * @param showStackTrace 是否在日志中附加调用位置
     */
    public static void E(String tag, String message, boolean showStackTrace) {
        write(Level.E, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, showStackTrace);
    }

    /**
     * 写入 Info 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     * @param showStackTrace 是否在日志中附加调用位置
     */
    public static void I(String tag, String message, boolean showStackTrace) {
        write(Level.I, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, showStackTrace);
    }

    /**
     * 写入 Fatal 级别日志。
     *
     * @param tag 日志标签
     * @param message 日志内容
     * @param showStackTrace 是否在日志中附加调用位置
     */
    public static void F(String tag, String message, boolean showStackTrace) {
        write(Level.F, BufferType.MAIN, android.os.Process.myPid(), tag, message, true, showStackTrace);
    }

    /**
     * 输出日志到 Android Logcat。
     *
     * @param type 日志级别
     * @param tagStr 日志标签
     * @param message 日志内容
     * @param stackTraceElement 调用位置
     * @param showStackTrace 是否附加调用位置
     */
    private static void printLog(Level type, String tagStr, Object message, StackTraceElement stackTraceElement, boolean showStackTrace) {
        if (!isShowLog) {
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        String tag;
        if (showStackTrace && stackTraceElement != null) {
            String className = stackTraceElement.getFileName();
            String methodName = stackTraceElement.getMethodName();
            int lineNumber = stackTraceElement.getLineNumber();
            tag = tagStr == null ? className : tagStr;
            methodName = methodName.substring(0, 1).toUpperCase(Locale.ROOT) + methodName.substring(1);
            stringBuilder.append("[ (").append(className).append(":").append(lineNumber).append(")#").append(methodName).append(" ] ");
        } else {
            tag = tagStr;
        }
        stringBuilder.append(message == null ? "Log with null Object" : message);
        String logStr = stringBuilder.toString();
        if (type == Level.V) {
            Log.v(tag, logStr);
        } else if (type == Level.D) {
            Log.d(tag, logStr);
        } else if (type == Level.I) {
            Log.i(tag, logStr);
        } else if (type == Level.W) {
            Log.w(tag, logStr);
        } else if (type == Level.E) {
            Log.e(tag, logStr);
        } else if (type == Level.F) {
            Log.wtf(tag, logStr);
        }
    }

    /**
     * 添加过滤关键字。
     * <p>
     * 后续写入的日志内容包含该关键字时，会同步写入过滤日志文件。
     * </p>
     *
     * @param keyword 过滤关键字，空字符串会被忽略
     */
    public static void addKeyword(String keyword) {
        LogFilterTools.addKeyword(keyword);
    }

    /**
     * 读取已配置的过滤关键字。
     *
     * @return 过滤关键字列表
     */
    public static List<String> readKeywords() {
        return LogFilterTools.readRulesToKeywords();
    }

    /**
     * 写入一条日志。
     * <p>
     * 该方法会先按配置输出到 Logcat，再根据 {@code writeToFile} 决定是否异步写入文件。
     * 文件写入由内部单线程顺序执行。
     * </p>
     *
     * @param level 日志级别
     * @param bufferType 日志缓冲区类型
     * @param pid 进程 ID
     * @param tag 日志标签
     * @param message 日志内容
     * @param writeToFile 是否写入本地日志文件
     * @param showStackTrace 是否在日志中附加调用位置
     * @throws IllegalStateException 未调用 {@link #initialize(Context, boolean)} 时抛出
     */
    public static void write(Level level, BufferType bufferType, int pid, String tag, String message, boolean writeToFile, boolean showStackTrace) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement callerStack = null;
        if (showStackTrace && stackTrace.length > 3) {
            callerStack = stackTrace[3];
        }
        printLog(level, tag, message, callerStack, showStackTrace);
        if (!writeToFile) {
            return;
        }
        if (executorService == null) {
            throw new IllegalStateException("FaceLogTools is not initialized. Call initialize() first.");
        }
        StackTraceElement finalCallerStack = callerStack;
        executorService.execute(() -> {
            try {
                String logEntry = buildLogEntry(level, bufferType, pid, tag, message, finalCallerStack, showStackTrace);
                synchronized (FaceLogTools.class) {
                    ensureWriterReady();
                    logBuffer.append(logEntry).append(System.lineSeparator());
                    bufferBytes += logEntry.getBytes(StandardCharsets.UTF_8).length + System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;

                    String today = DATE_FILENAME_FORMAT.format(new Date());
                    if (!currentLogFile.getName().contains(today) || !currentLogFile.exists()) {
                        flushBufferToDisk();
                        resumeOrCreateLogFile();
                    } else if (currentLogFile.length() + bufferBytes >= getMaxFileSize()) {
                        flushBufferToDisk();
                        createNewLogFile();
                    } else if (bufferBytes >= BUFFER_BYTE_SIZE) {
                        flushBufferToDisk();
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, "Write log failed", e);
            }
        });
    }

    /**
     * 构造本地日志文件中的单行文本。
     *
     * @param level 日志级别
     * @param bufferType 日志缓冲区类型
     * @param pid 进程 ID
     * @param tag 日志标签
     * @param message 日志内容
     * @param callerStack 调用位置
     * @param showStackTrace 是否附加调用位置
     * @return 已格式化的日志文本
     */
    private static String buildLogEntry(Level level, BufferType bufferType, int pid, String tag, String message,
                                        StackTraceElement callerStack, boolean showStackTrace) {
        int currentLineNumber = nextLineNumber++;
        StringBuilder logEntryBuilder = new StringBuilder();
        long droppedCount = droppedLogCount.getAndSet(0);
        if (droppedCount > 0) {
            logEntryBuilder.append("<").append(currentLineNumber).append("> ");
            logEntryBuilder.append("[drop] Dropped ").append(droppedCount).append(" logs because the write queue was full");
            logEntryBuilder.append(System.lineSeparator());
            currentLineNumber = nextLineNumber++;
        }

        logEntryBuilder.append("<").append(currentLineNumber).append("> ");
        logEntryBuilder.append("[").append(pid).append("] ");
        logEntryBuilder.append("[").append(bufferType.name()).append("] ");
        logEntryBuilder.append("[").append(level.name()).append("] ");
        logEntryBuilder.append("[").append(DATE_LOG_FORMAT.format(new Date())).append("] ");
        if (showStackTrace && callerStack != null) {
            String className = callerStack.getFileName();
            String methodName = callerStack.getMethodName();
            int lineNumber = callerStack.getLineNumber();
            methodName = methodName.substring(0, 1).toUpperCase(Locale.ROOT) + methodName.substring(1);
            logEntryBuilder.append("[ (").append(className).append(":").append(lineNumber).append(")#").append(methodName).append(" ] ");
        } else {
            logEntryBuilder.append("[").append(tag).append("] ");
        }
        logEntryBuilder.append(message);

        LogFilterTools.writeKeywordToFile(currentLineNumber, message);
        return logEntryBuilder.toString();
    }

    /**
     * 将内存缓冲区中的日志批量写入磁盘。
     * <p>
     * 写入成功后会持久化下一行行号，并清空内存缓冲区。
     * </p>
     */
    private static void flushBufferToDisk() {
        if (logBuffer.length() == 0) {
            return;
        }
        ensureWriterReady();
        try {
            writer.write(logBuffer.toString());
            writer.flush();
            persistNextLineNumber();
        } catch (IOException e) {
            Log.e(TAG, "Flush log buffer failed", e);
            resumeOrCreateLogFile();
            try {
                writer.write(logBuffer.toString());
                writer.flush();
                persistNextLineNumber();
            } catch (IOException ex) {
                Log.e(TAG, "Retry flush log buffer failed", ex);
            }
        } finally {
            logBuffer.setLength(0);
            bufferBytes = 0;
        }
    }

    /**
     * 获取当前日志目录下的日志文件名列表。
     *
     * @return 文件名列表，不包含完整路径
     * @throws IllegalStateException 未调用 {@link #initialize(Context, boolean)} 时抛出
     */
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

    /**
     * 创建当天新的日志文件。
     * <p>
     * 文件名序号会在当天已有日志文件的最大序号上递增，新文件行号从 1 开始。
     * </p>
     */
    private static synchronized void createNewLogFile() {
        closeWriter();
        ensureLogDirectory();
        String today = DATE_FILENAME_FORMAT.format(new Date());
        int fileIndex = 1;
        File[] existingFiles = logDirectory.listFiles((dir, name) ->
                name.startsWith(LOG_FILE_PREFIX + today + "_") && name.endsWith(LOG_FILE_EXTENSION));
        if (existingFiles != null && existingFiles.length > 0) {
            Arrays.sort(existingFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            File lastFile = existingFiles[existingFiles.length - 1];
            fileIndex = getFileIndexFromName(lastFile.getName()) + 1;
        }
        currentLogFile = new File(logDirectory, LOG_FILE_PREFIX + today + "_" + fileIndex + LOG_FILE_EXTENSION);
        lineNumberFile = new File(logDirectory, "line_number.txt");
        nextLineNumber = 1;
        persistNextLineNumber();
        try {
            writer = new BufferedWriter(new FileWriter(currentLogFile, true));
        } catch (IOException e) {
            Log.e(TAG, "Create log writer failed", e);
        }
    }

    /**
     * 从日志文件名中解析序号。
     *
     * @param fileName 形如 {@code log_yyyyMMdd_1.txt} 的文件名
     * @return 文件序号；解析失败时返回 1
     */
    static int getFileIndexFromName(String fileName) {
        try {
            String[] parts = fileName.split("_");
            String indexPart = parts[2].replace(LOG_FILE_EXTENSION, "");
            return Integer.parseInt(indexPart);
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * 关闭当前日志 writer，并在关闭前持久化下一行行号。
     */
    private static void closeWriter() {
        persistNextLineNumber();
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "Close log writer failed", e);
            }
            writer = null;
        }
    }

    /**
     * 确保日志 writer 可用。
     */
    private static void ensureWriterReady() {
        if (writer == null) {
            resumeOrCreateLogFile();
        }
    }

    /**
     * 确保日志目录已初始化并存在。
     */
    private static void ensureLogDirectory() {
        if (logDirectory == null) {
            logDirectory = new File(getDefaultLogDirectory());
        }
        if (!logDirectory.exists() && !logDirectory.mkdirs()) {
            Log.w(TAG, "Create log directory failed: " + logDirectory.getAbsolutePath());
        }
    }

    /**
     * 持久化下一条日志应使用的行号。
     */
    private static void persistNextLineNumber() {
        if (lineNumberFile != null) {
            FileKV.putValue(lineNumberFile.getAbsolutePath(), nextLineNumber);
        }
    }
}
