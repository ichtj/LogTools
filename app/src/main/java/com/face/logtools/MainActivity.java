package com.face.logtools;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.library.logtools.FaceLogTools;

import java.util.List;


public class MainActivity extends AppCompatActivity {
    private static final String TAG=MainActivity.class.getSimpleName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        setContentView (R.layout.activity_main);

        Button btnSetLogDirectory = findViewById(R.id.btnSetLogDirectory);

        btnSetLogDirectory.setOnClickListener(v -> {
            FilePickerDialog filePickerDialog=new FilePickerDialog(this, "选择日志目录", FaceLogTools.getLogDirectory ());
            filePickerDialog.setOnPathSelectedListener (new FilePickerDialog.OnPathSelectedListener ( ) {
                @Override
                public void onPathSelected(String path) {
                    FaceLogTools.putLogDirectory (path);
                    Toast.makeText(MainActivity.this, "日志目录已设置 : "+path, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCancel() {

                }
            });
            filePickerDialog.show ();
        });

        FaceLogTools.addKeyword ("ichtj");
    }
    int count =0;
    boolean isRunning = false;
    public void writeOnClick(View view){
        if (!isRunning){
            new Thread (  ){
                @Override
                public void run() {
                    super.run ( );
                    while (isRunning){
                        try {
                            Thread.sleep (100);
                        } catch (InterruptedException e) {
                            e.printStackTrace ( );
                        }
                        String content=count+" [测试日志内容] "+count;
                        if (count%20==0){
                            content+="ichtj";
                        }
                        FaceLogTools.D(TAG,content, true);
                        count++;
                    }
                }
            }.start ();
            isRunning=true;
        }else{
            isRunning=false;
        }

        Toast.makeText(this, "日志已写入", Toast.LENGTH_SHORT).show();
    }

    public void getLogFilesOnClick(View view){
        List<String> logFiles = FaceLogTools.getLogFiles();
        Toast.makeText(this, "日志文件: " + logFiles, Toast.LENGTH_LONG).show();
    }


}