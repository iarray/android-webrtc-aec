package com.android.aec.util;

import com.android.aec.conf.Setting;

public class WebRtcUtil {
	static{
		if(Setting.loadLibrary()!=null){
			Setting.loadLibrary().loadLibrary();
		}else{
			System.loadLibrary("webrtc_android");
			System.loadLibrary("webrtc_jni");
		}
	}
	public static native int init(long rate);
	public static native void free();
	public static native int bufferFarendAndProcess(byte[] far ,byte[] near,byte[] out ,int processlength, int delay,int skew,float near_v,float far_v);
}
