package com.library.logtools;

import android.os.Parcel;
import android.os.Parcelable;

// 必须实现 Parcelable
public enum Level implements Parcelable {
//    verbose, debug, info, warn, error, fatal, unknown;
    V, D, I, W, E, F, UNKNOWN;

    // ------------------ Parcelable 实现 ------------------

    // 写入Parcel
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // 使用 name() 方法将枚举名写入 Parcel
        dest.writeString(name());
    }

    // 从 Parcel 读取
    public static final Creator<Level> CREATOR = new Creator<Level>() {
        @Override
        public Level createFromParcel(Parcel in) {
            // 从 Parcel 中读取字符串，并使用 valueOf() 查找对应的枚举值
            return Level.valueOf(in.readString());
        }

        @Override
        public Level[] newArray(int size) {
            return new Level[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}