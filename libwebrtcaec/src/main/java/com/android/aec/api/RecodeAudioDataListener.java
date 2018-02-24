package com.android.aec.api;

public interface RecodeAudioDataListener {
	public int onRecodeAudioData(byte[] data, int length, byte[] imfo);
}
