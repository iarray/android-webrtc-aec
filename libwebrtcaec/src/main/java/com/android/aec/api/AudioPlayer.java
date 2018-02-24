package com.android.aec.api;

import com.android.aec.conf.Setting;
import com.android.aec.core.AECCore;

import android.content.Context;

public class AudioPlayer {
	private AudioPlayer() {
	}
	private static AudioPlayer mSingleInstance;
	private boolean enableSystemAEC = true;
	private boolean mHasInit;
	private AECCore mPlayer;
	private Context mContext;

	public static AudioPlayer getInstance() {
		if (mSingleInstance == null) {
			synchronized (AudioPlayer.class) {
				if (mSingleInstance == null) {
					mSingleInstance = new AudioPlayer();
				}
			}
		}
		return mSingleInstance;
	}
	public void setLoadLibrary(InitLibrary lib){
		Setting.setLoadLibrary(lib);
	}
	public synchronized AudioPlayer init(Context context,RecodeAudioDataListener recodeListener,Frequency hz,int frameSize){
		if (mHasInit) {
			stop();
		}
		if (context == null) {
			throw new RuntimeException("context can not be null");
		}
		mHasInit = true;
		this.mContext = context.getApplicationContext();
		mPlayer = new AECCore(this.mContext, recodeListener,hz,frameSize,enableSystemAEC);
		return this;
	}
	public AudioPlayer init(Context context, RecodeAudioDataListener recodeListener,Frequency hz){
		return init(context, recodeListener,hz,hz.value()/10);
	} 
	public AudioPlayer init(Context context, RecodeAudioDataListener recodeListener) {
		return init(context, recodeListener,Frequency.PCM_8K);
	}

	public void start(boolean playSound, boolean recodeAudio) {
		if (!mHasInit) {
			throw new RuntimeException("must init before start");
		}
		mPlayer.start(playSound, recodeAudio);
	}

	public boolean putPlayData(byte[] data, int len) {
		if (mPlayer != null)
			return mPlayer.putData(data, len);
		else
			return false;
	}

	public boolean putPlayData(AudioFrame frame){
		if(mPlayer !=null){
			return mPlayer.putData(frame);
		}else{
			return false;
		}
	}
	
	public void setPtsUpdateListener(OnTSUpdateListener onTSUpdateListener){
		if(mPlayer!=null){
			mPlayer.setPtsListener(onTSUpdateListener);
		}
	}
	public byte[] getFilterData() {
		if (mPlayer != null)
			return mPlayer.getFilterData();
		else
			return null;
	}
	
	public void stop() {
//		mHasInit = false;
		if (mPlayer != null)
			mPlayer.stop();
	}
	public void release(){
		mHasInit = false;
		if(mPlayer != null){
			mPlayer.release();
		}
	}
	public void soundOn() {
		if (mPlayer != null)
			mPlayer.soundOn();
	}

	public void soundOff() {
		if (mPlayer != null)
			mPlayer.soundOff();
	}

	public boolean isSoundOn() {
		if (mPlayer != null)
			return mPlayer.isSoundOn();
		else
			return false;
	}

	public void resumeAudioRecord() {
		if (mPlayer != null)
			mPlayer.resumeAudioRecord();
	}

	public void pauseAudioRecord() {
		if (mPlayer != null)
			mPlayer.pauseAudioRecord();
	}

	public boolean isAudioRecord() {
		if (mPlayer != null)
			return mPlayer.isAudioRecord();
		else
			return false;
	}
	public AudioPlayer disableSystemAEC(){
		enableSystemAEC = false;
		return this;
	}
	public AudioPlayer enableSystemAEC(){
		enableSystemAEC = true;
		return this;
	}
	
}
