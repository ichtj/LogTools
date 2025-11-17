package com.library.logtools;

import android.os.Environment;
import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class LogFilterTools {
    private List<String> rulesKeywords = new ArrayList<>();
    private static volatile LogFilterTools instance;
    private static final String FILTER_FILE_PREFIX = "filter_";
    private static final String FILTER_FILE_EXTENSION = ".txt";
    private static final SimpleDateFormat DATE_FLIENAME_FORMAT = new SimpleDateFormat("yyyyMMdd");
    private static final String KEY_LOG_FILTER_PATH = "log_filter_path";
    private static final String KEY_CONFIG_PATH = "key_log_filter_config_path";
    private static final String DEFAULT_CONFIG_FILE= "log_filter.config";
    private static final String DEFAULT_FILETER_DIR= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath ()+"/filter/";
    private File configFile;
    private File logParentDirectory;
    private File currentLogFile;
    private static BufferedWriter writer;

    private static LogFilterTools getInstance() {
        if (instance == null) {
            synchronized (LogFilterTools.class) {
                if (instance == null) {
                    instance = new LogFilterTools();
                    init();
                }
            }
        }
        return instance;
    }

    private static void init() {
        getInstance ().rulesKeywords.addAll (readRulesToKeywords ());
        getInstance ().logParentDirectory= new File (SPUtils.getString (KEY_LOG_FILTER_PATH,DEFAULT_FILETER_DIR));
        SPUtils.putString (KEY_LOG_FILTER_PATH,getInstance ().logParentDirectory.getAbsolutePath ());
        getInstance ().configFile=new File (DEFAULT_FILETER_DIR,DEFAULT_CONFIG_FILE);
        SPUtils.putString (KEY_CONFIG_PATH,getInstance ().configFile.getAbsolutePath ());
        resumeOrCreateLogFile ();
    }

    private static synchronized void resumeOrCreateLogFile() {
        closeWriter();
        String today = DATE_FLIENAME_FORMAT.format(new Date ());
        int fileIndex = 1;

        // 获取当天所有日志文件
        File[] existingFiles = getInstance ().logParentDirectory.listFiles((dir, name) -> name.startsWith(FILTER_FILE_PREFIX + today + "_") && name.endsWith(FILTER_FILE_EXTENSION));
        if (existingFiles != null && existingFiles.length > 0) {
            Arrays.sort(existingFiles, (f1, f2) -> f1.getName().compareTo(f2.getName()));
            File lastFile = existingFiles[existingFiles.length - 1];
            if (lastFile.length() < FaceLogTools.getMaxFileSize ()) {
                getInstance ().currentLogFile = lastFile;
                fileIndex = FaceLogTools.getFileIndexFromName(lastFile.getName());
            } else {
                fileIndex = FaceLogTools.getFileIndexFromName(lastFile.getName()) + 1;
                getInstance ().currentLogFile = new File(getInstance ().logParentDirectory, FILTER_FILE_PREFIX + today + "_" + fileIndex + FILTER_FILE_EXTENSION);
            }
        } else {
            getInstance ().currentLogFile = new File(getInstance ().logParentDirectory, FILTER_FILE_PREFIX + today + "_" + fileIndex + FILTER_FILE_EXTENSION);
        }

        try {
            writer = new BufferedWriter (new FileWriter (getInstance ().currentLogFile, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addKeyword(String keyword) {
        if (TextUtils.isEmpty (keyword) && getInstance ( ).rulesKeywords.contains (keyword)) {
            return;
        }
        List<String> readRules = readRulesToKeywords ();
        if (!getInstance ( ).rulesKeywords.contains (keyword)) {
            getInstance ( ).rulesKeywords.add (keyword);
            readRules.add (keyword);
            StringBuffer stringBuffer = new StringBuffer ( );
            boolean isLast = readRules.size ( ) == 1;
            for (String item : readRules) {
                if (isLast) {
                    stringBuffer.append (item);
                } else {
                    stringBuffer.append (item).append ("\n");
                }
            }
            try {
                if (!getInstance ().configFile.getParentFile().exists ()){
                    getInstance ().configFile.getParentFile().mkdirs ();
                }
                if (!getInstance ().configFile.exists ()){
                    getInstance ().configFile.createNewFile ();
                }
                BufferedWriter bw = new BufferedWriter (new FileWriter (getInstance ().configFile,false));
                bw.write (stringBuffer.toString ());
                bw.close ();
            } catch (IOException e) {
                e.printStackTrace ( );
            }
        }
    }

    public static void writeToFile(int currentLine, String content) {
        if (getInstance ( ).rulesKeywords.size ( ) == 0) {
            return;
        }
        String keywordStr = "";
        boolean isMatch = false;
        for (String keyword : getInstance ( ).rulesKeywords) {
            if (content.contains (keyword)) {
                isMatch = true;
                keywordStr = keyword;
                break;
            }
        }
        if (isMatch) {
            if (!getInstance ( ).logParentDirectory.exists ( )) {
                getInstance ( ).logParentDirectory.mkdirs ( );
            }
            if (getInstance ( ).currentLogFile == null || !getInstance ( ).currentLogFile.exists ( )) {
                resumeOrCreateLogFile ( );
            } else {
                if (getInstance ( ).currentLogFile.length ( ) >= FaceLogTools.getMaxFileSize ( )) {
                    resumeOrCreateLogFile ( );
                }
            }
            try {
                writer.write ("<" + currentLine + "> [" + keywordStr + "] " + content);
                writer.newLine ( );
                writer.flush ( );
            } catch (IOException e) {
                e.printStackTrace ( );
            }
        }
    }


    /**
     * 读取文件中的每一行内容到集合中去
     *
     * @return 返回读取到的所有包名list集合
     */
    public static List<String> readRulesToKeywords() {
        String filePath=SPUtils.getString (KEY_LOG_FILTER_PATH,DEFAULT_FILETER_DIR);
        //将读出来的一行行数据使用Map存储
        List<String> bmdList = new ArrayList<String> ();
        try {
            File file = new File(filePath);
            if (file.isFile() && file.exists()) {  //文件存在的前提
                InputStreamReader isr = new InputStreamReader(new FileInputStream (file));
                BufferedReader br = new BufferedReader(isr);
                String lineTxt = null;
                while ((lineTxt = br.readLine()) != null) {  //
                    if (!"".equals(lineTxt)) {
                        String reds = lineTxt.split("\\+")[0];  //java 正则表达式
                        bmdList.add(reds);//依次放到集合中去
                    }
                }
                isr.close();
                br.close();
            } else {
                System.out.println("can not find file");//找不到文件情况下
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bmdList;
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
