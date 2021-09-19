package com.shy.common;
import com.shy.main.MainActivity;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public class Logger {
    private  ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    public MainActivity activity;
    private  FileWriter fileWriter;
    private  String logFile;
    private  String name;
    private  Logger(String name){
        this.name=name;
    }
    private  void write(String level, String msg)
    {
        singleThreadExecutor.execute(new Runnable()
        {
            @Override
            public void run()
            {
                if (Logger.this.logFile!=Logger.this.getLogFilePath())
                {
                    Logger.this.logFile = Logger.this.getLogFilePath();
                    try {
                        if(Logger.this.fileWriter!=null)
                        {
                            Logger.this.fileWriter.flush();
                            Logger.this.fileWriter.close();
                            Logger.this.fileWriter=null;
                        }
                        Logger.this.fileWriter = new FileWriter(Logger.this.logFile, true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    Date now = new Date();
                    String nextLine = "\r\n";
                    String nowStr = now.toLocaleString();
                    String content = "[" + level + "]" + nowStr+" " + msg + nextLine;
                    Logger.this.fileWriter.append(content);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
    private  String getLogFilePath()
    {
        String LogDir = this.getLogDir();
        Date now = new Date();
        File LogDirFile = new File(LogDir);
        if (!LogDirFile.exists()) LogDirFile.mkdirs();
        String DatePath=(now.getYear()+1900) + "_" + (now.getMonth()+1);
        if(this.name=="operatelogs")
        {
            DatePath+="_"+(now.getDate());
        }
        String LogFile=LogDir+File.separator +DatePath  + ".log";
        return LogFile;
    }
    public    String getLogDir()
    {
        File dir= activity.getFilesDir();
        String LogDir = dir.getAbsolutePath() + File.separator + this.name;
        File LogDirFile = new File(LogDir);
        if (!LogDirFile.exists()) LogDirFile.mkdirs();
        return  LogDir;
    }
    public  void error(String errorMsg)
    {
       this.write("error",errorMsg);
    }
    public void e(String errorMsg) {
        this.write("error",errorMsg);
    }
    public void info(String errorMsg)
    {
        this.write("Info",errorMsg);
    }
    public void i(String errorMsg){
        this.write("Info",errorMsg);
    }
    public  static Logger  log=new Logger("logs");
    public  static Logger  operateLog=new Logger("operatelogs");
}
