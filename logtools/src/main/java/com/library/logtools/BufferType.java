package com.library.logtools;

import android.os.Parcel;
import android.os.Parcelable;

// 必须实现 Parcelable
public enum BufferType implements Parcelable {
    MAIN,
    SYSTEM,
    RADIO,
    EVENTS,
    CRASH;

    // ------------------ Parcelable 实现 ------------------

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // 将枚举的名称（字符串）写入 Parcel
        dest.writeString(name());
    }

    public static final Creator<BufferType> CREATOR = new Creator<BufferType>() {
        @Override
        public BufferType createFromParcel(Parcel in) {
            // 从 Parcel 读取字符串，并使用 valueOf() 转换回枚举值
            return BufferType.valueOf(in.readString());
        }

        @Override
        public BufferType[] newArray(int size) {
            return new BufferType[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
}
