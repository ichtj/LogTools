package com.face.logtools;

import android.app.Application;

import com.library.logtools.FaceLogTools;


public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate ( );
        FaceLogTools.initialize (this, true);
    }
}
