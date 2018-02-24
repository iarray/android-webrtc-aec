package com.android.aec.core;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import com.android.aec.api.AudioFrame;
import com.android.aec.api.Frequency;
import com.android.aec.api.OnTSUpdateListener;
import com.android.aec.api.RecodeAudioDataListener;
import com.android.aec.conf.Setting;
import com.android.aec.util.AudioUtils;
import com.android.aec.util.LogUtils;
import com.android.aec.util.StreamUtils;
import com.android.aec.util.WebRtcUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioTrack.OnPlaybackPositionUpdateListener;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class AECCore {
	private static final boolean isDebug = Setting.getAECDebug();
	private static final String TAG = "AudioPlayer";
	private int msInSndCardBuf = 60; // ms
	private int AUDIO_RECORD_SAMPLE_RATE = 8000; // 8KHZ
	private Thread mRecordThread;
	private Thread mPlayThread;
	private boolean mIsPlayThreadAlive;
	private boolean mIsPlayAudio;
	private boolean mIsRecordAudio;
	private boolean mIsWorking;
	private boolean mIsRecodeWorking = true;
	private FilterBuffer2 buffer;
	private FilterBuffer playBuffer;
	private FilterBuffer filterBuffer;
	private int mFramesize;
	private boolean audioRecodeStart;
	private boolean isFirst = true;
	private AudioTrack audioPlay = null;
	private AudioRecord audioRecode = null;
	private boolean isClearing;
	private volatile int playCount;
	private volatile int playPosition;
	private volatile boolean shouldClear;
	private RecodeAudioDataListener listener;
	private volatile int volume;
	private float near_v = 1.0f, far_v = 1.0f;
	private int maxVolume;
	private AudioManager audioManager;
	private Context context;
	private MyVolumeReceiver mVolumeReceiver;
	private WeakReference<OnTSUpdateListener> mTsListener;
	private boolean isHeadSet;// 是否连接耳机
	private boolean isSupportPeriod = false;
	private boolean enableSystemAEC = true;
	public AECCore(Context context, RecodeAudioDataListener receiver, Frequency hz, int frameSize, boolean systemAEC) {
		AUDIO_RECORD_SAMPLE_RATE = hz.value();
		// msInSndCardBuf = Frequency.PCM_16K==hz?210:60;
		enableSystemAEC = systemAEC;
		msInSndCardBuf = 160;
		WebRtcUtil.init(AUDIO_RECORD_SAMPLE_RATE);
		AudioUtils.chooseAudioMode(context,AudioManager.MODE_IN_COMMUNICATION,enableSystemAEC);
		this.context = context;
		this.listener = receiver;
		this.mFramesize = frameSize;
		this.buffer = new FilterBuffer2(hz.value() * 4, this.mFramesize);
		this.playBuffer = new FilterBuffer(hz.value() * 4, this.mFramesize);
		this.filterBuffer = new FilterBuffer(hz.value() * 2, this.mFramesize);
		audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		isHeadSet = audioManager.isWiredHeadsetOn();
		maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
		onVolumeChanged();
		mVolumeReceiver = new MyVolumeReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction("android.media.VOLUME_CHANGED_ACTION");
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		context.registerReceiver(mVolumeReceiver, filter);
	}

	private void onVolumeChanged() {
		try{
		int currVolume = audioManager.getStreamVolume(enableSystemAEC?AudioManager.STREAM_VOICE_CALL:AudioManager.STREAM_MUSIC);
		volume = maxVolume / 2 - currVolume;
		volume = volume / (maxVolume / 5);
		if (volume == 1 || volume == -1) {
			volume = 0;
		}
		int m = 1;
		for (int i = 0; i < (int) Math.log10(volume); i++) {
			m *= 10;
		}
		volume = volume / m;
		if (volume > 0) {
			far_v = (float) (1 / (Math.pow(2, volume)));
			near_v = 1.0f;
		} else if (volume < 0) {
			far_v = -volume;
			near_v = 1.0f;
		} else {
			far_v = 1.0f;
			near_v = 1.0f;
		}
		LogUtils.i(TAG, "maxVolume = " + maxVolume + "currVolume = " + currVolume);
		LogUtils.i(TAG, "volume = " + volume + "farv = " + far_v + "near_v = " + near_v);
		}catch(Throwable t){
			LogUtils.d(TAG, "error :"+t.getMessage());
		}
	}

	private class MyVolumeReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")) {
				onVolumeChanged();
			} else if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
				if (intent.hasExtra("state")) {
					if (intent.getIntExtra("state", 0) == 0) {
						isHeadSet = false;
					} else if (intent.getIntExtra("state", 0) == 1) {
						isHeadSet = true;
					}
				}
			}
		}
	}
	public void setPtsListener(OnTSUpdateListener l){
		this.mTsListener = new WeakReference<OnTSUpdateListener>(l);
	}
	private void resetStatus() {
		this.mIsPlayThreadAlive = false;
		this.mIsWorking = false;
		this.mIsRecodeWorking = true;
		this.audioRecodeStart = false;
		this.isFirst = true;
		this.isClearing = false;
		this.playCount = 0;
		this.playPosition = 0;
	}

	public synchronized void stop() {
		this.mIsPlayThreadAlive = false;
		if (this.mRecordThread != null) {
			this.mRecordThread.interrupt();
			try {
				this.mRecordThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			this.mRecordThread = null;
		}

		if (this.mPlayThread != null) {
			this.mPlayThread.interrupt();
			try {
				this.mPlayThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			this.mPlayThread = null;
		}
		this.mIsWorking = false;
		if (buffer != null) {
			buffer.clear();
		}
		if (playBuffer != null) {
			playBuffer.clear();
		}
		if (filterBuffer != null) {
			filterBuffer.clear();
		}
		resetStatus();
		// readBuffer.release();
	}

	public synchronized void release() {
		if (buffer != null){
			buffer.release();
			buffer = null;
		}
		if (playBuffer != null){
			playBuffer.release();
			playBuffer = null;
		}
		if (filterBuffer != null){
			filterBuffer.release();
			filterBuffer =null;
		}
		if(mTsListener!=null){
			mTsListener.clear();
		}
		WebRtcUtil.free();
		if(context!=null){
			AudioUtils.chooseAudioMode(context,AudioManager.MODE_NORMAL,enableSystemAEC);
		}
		LogUtils.e(TAG, "audio play stop");
	}

	public void soundOn() {
		this.mIsPlayAudio = true;
	}

	public void soundOff() {
		this.mIsPlayAudio = false;
	}

	public boolean isSoundOn() {
		return this.mIsPlayAudio;
	}

	public void resumeAudioRecord() {

		this.mIsRecordAudio = true;
	}

	public void pauseAudioRecord() {

		this.mIsRecordAudio = false;
	}

	public boolean isAudioRecord() {
		return this.mIsRecordAudio;
	}

	public synchronized boolean putData(byte[] data, int len) {
		if(buffer==null) return false;
		return buffer.putAll(data, len);
	}
	
	public synchronized boolean putData(AudioFrame frame){
		if(buffer==null) return false;
		return buffer.putAll(frame);
	}
	public synchronized byte[] getFilterData() {
		if(filterBuffer==null) return null;
		return filterBuffer.takeAll();
	}
    long startRecordTime;
    long shouldstartRecordTime;
	public synchronized void start(boolean playSound, boolean recodeAudio) {
		if (this.mIsWorking)
			return;
		resetStatus();
		this.mIsPlayAudio = playSound;
		this.mIsRecordAudio = recodeAudio;
		this.mIsWorking = true;
		this.mIsPlayThreadAlive = true;
		if (this.mPlayThread == null) {
			this.mPlayThread = new Thread(new Runnable() {
				public void run() {
					try {
						int[] minbuffs = new int[1];
						audioPlay = AudioUtils.createTracker(minbuffs,AUDIO_RECORD_SAMPLE_RATE, mFramesize,enableSystemAEC);
						if (audioPlay == null) {
							LogUtils.e(TAG, "create audio play return null");
							return;
						}
						int delay_time = minbuffs[0]/(AUDIO_RECORD_SAMPLE_RATE*2/1000);
						LogUtils.i(TAG, "delay_time:"+delay_time);
						//TODO
						int period = audioPlay.setPositionNotificationPeriod(mFramesize / 2);
						LogUtils.i(TAG, "setPositionNotificationPeriod :"+period);
						isSupportPeriod = period==AudioTrack.SUCCESS;
						if(isSupportPeriod)
						audioPlay.setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener() {
							long prePeriodic;
							@Override
							public void onPeriodicNotification(AudioTrack track) {
								// TODO
								LogUtils.i(TAG, "receive period "+Looper.myLooper());
								AECCore.this.playPosition = AECCore.this.playPosition + 1;
								if(AECCore.this.playPosition==7){
									shouldstartRecordTime = (System.currentTimeMillis()-prePeriodic)/2+prePeriodic+25;
								}
								if(AECCore.this.playPosition==6){
									prePeriodic = System.currentTimeMillis();
								}
								if (AECCore.this.playPosition > 10
										&& AECCore.this.playCount == AECCore.this.playPosition) {
									LogUtils.e(TAG, "shouldCache = true");
									shouldClear = true;
								}
								if (AECCore.this.playCount > 1000) {
									AECCore.this.playCount = AECCore.this.playCount - 100;
									AECCore.this.playPosition = AECCore.this.playPosition - 100;
								}
							}

							@Override
							public void onMarkerReached(AudioTrack track) {

							}
						});
						AudioFrame frame = new AudioFrame();
						audioPlay.play();
						byte[] emptyData = new byte[mFramesize];
						do {
							if (AECCore.this.mIsPlayAudio) {
								if(!buffer.getFrame(frame)){
									frame.setAudioData(emptyData, emptyData.length);
									frame.setTimeStamp(0);
									frame.setPos(0);
								}
							} else {
								buffer.clear();
								frame.setAudioData(emptyData, emptyData.length);
								frame.setTimeStamp(0);
								frame.setPos(0);
							}
							if (audioPlay.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
								audioPlay.play();
							}
							if (isFirst && mIsRecodeWorking) {
								synchronized (AECCore.class) {
									while ((!audioRecodeStart) && mIsRecodeWorking) {
										AECCore.class.wait();
									}
								}
								isFirst = false;
							}
							if (isClearing || shouldClear) {
								synchronized (AECCore.class) {
									while ((isClearing || shouldClear) && mIsRecodeWorking) {
										AECCore.class.wait();
									}
								}
								audioPlay.play();
							}
							LogUtils.i(TAG, "PlayBuffer is Empty data = " + (frame.getAudioData() == emptyData) + ";playPos ="
									+ playPosition + ";playPool = " + playCount);
							playBuffer.putAll(frame.getAudioData(), frame.dataLength());
							audioPlay.write(frame.getAudioData(), 0, frame.dataLength());
							OnTSUpdateListener listener = null;
							if(mTsListener!=null){
								 listener = mTsListener.get();
							}
							if(listener!=null){
								listener.onUpdate((frame.getTimeStamp()==0?delay_time:frame.getTimeStamp())-delay_time);
							}
							playCount = playCount + 1;
						} while (AECCore.this.mIsPlayThreadAlive);
					} catch (Throwable e) {
						e.printStackTrace();
						LogUtils.e(TAG, "audioTrack err:" + e.getLocalizedMessage());
					} finally {
						if (audioPlay != null && audioPlay.getPlayState() == 3) {
							audioPlay.pause();
							audioPlay.flush();
							audioPlay.release();
							audioPlay = null;
							LogUtils.e("AudioPlayer", "release audio play");
						}
						LogUtils.i(TAG, "audioPlay Thread stop");
					}
				}
			}, "AudioPlay Thread");
			this.mPlayThread.setPriority(6);
			this.mPlayThread.start();
		}

		if (this.mRecordThread == null) {
			this.mRecordThread = new Thread(new Runnable() {
				public void run() {
					byte[] recodedata = new byte[mFramesize];
					BufferedOutputStream out = null;
					BufferedOutputStream farout = null;
					BufferedOutputStream nearout = null;
					try {
						if (isDebug) {
							/* �洢 */
							final String outPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
							out = StreamUtils.openStreamByFile(outPath + "debug_out.pcm");
							farout = StreamUtils.openStreamByFile(outPath + "debug_far.pcm");
							nearout = StreamUtils.openStreamByFile(outPath + "debug_near.pcm");
							new Handler(Looper.getMainLooper()).post(new Runnable() {
								@Override
								public void run() {
									Toast.makeText(context, "AECfile path:" + outPath + "debug_out.pcm",
											Toast.LENGTH_LONG).show();
								}
							});
						}
						byte[] te = null;
						byte[] filterBuff = new byte[mFramesize];

						// TODO
						synchronized (AECCore.class) {
							audioRecodeStart = true;
							// audioRecode.startRecording();
							AECCore.class.notify();
						}
						
						while ((isSupportPeriod?playPosition:playCount) <= 5) {
							Thread.sleep(10);
						}
						while ((isSupportPeriod?playPosition:playCount) <= 6) {
							Thread.sleep(1);
						}
						int count = 0;
						while (++count <= playPosition) {
							byte[] t = playBuffer.takeAll();
							LogUtils.i(TAG, "remove byte = "+t);
						}
						int[] minBuffSize = new int[1];
						audioRecode = AudioUtils.creatAudioRecord(minBuffSize, AUDIO_RECORD_SAMPLE_RATE, mFramesize,enableSystemAEC);
						if (audioRecode == null) {
							throw new NullPointerException("audio recode is null");
						}
//						audioRecode.setPositionNotificationPeriod(mFramesize/2);
//						audioRecode.setRecordPositionUpdateListener(new OnRecordPositionUpdateListener() {
//							
//							int position = 0;
//							@Override
//							public void onPeriodicNotification(AudioRecord recorder) {
//								if(position==10){
//									return ;
//								}
//								LogUtils.i(TAG, "audiorecord notify");
//								position = position+1;
//								if(position==10){
//									int msInSndCardBuf = (int) ((System.currentTimeMillis()-shouldstartRecordTime)-(position*((mFramesize*1000)/(AUDIO_RECORD_SAMPLE_RATE*2))));
//									LogUtils.i(TAG, "delay time = "+msInSndCardBuf);
//								}
//							}
//							@Override
//							public void onMarkerReached(AudioRecord recorder) { }
//						});
						startRecordTime = System.currentTimeMillis();
						LogUtils.i(TAG, "audioRecode start time  =" + startRecordTime);

						audioRecode.startRecording();

						msInSndCardBuf = 60;
						long starttime = 0, endTime = 0;
						int index;
						byte[] tempdata = new byte[minBuffSize[0]];
						while (AECCore.this.mIsPlayThreadAlive) {
							te = playBuffer.takeAll();
							if (te == null) {
								isClearing = true;
								synchronized (AECCore.class) {
									te = new byte[mFramesize];
									for (int i = 0; i < (msInSndCardBuf / 50) + 3; i++) {
										audioRecode.read(recodedata, 0, mFramesize);
										WebRtcUtil.bufferFarendAndProcess(te, recodedata, filterBuff, mFramesize,
												msInSndCardBuf, 0, near_v, far_v);
										if (mIsRecordAudio) {
											filterBuffer.putAll(filterBuff, filterBuff.length);
										}
										if (isDebug) {
											nearout.write(recodedata);
											Arrays.fill(te, (byte) 100);
											farout.write(te);
											Arrays.fill(te, (byte) 0);
											out.write(filterBuff);
										}
									}
									starttime = endTime = 0;
									while (starttime - endTime < 10) {
										endTime = System.currentTimeMillis();
										index = audioRecode.read(tempdata, 0, tempdata.length);
										starttime = System.currentTimeMillis();
										LogUtils.e(TAG, "clear audioRecode buffer = " + index);
										if (isDebug) {
											byte[] temp = new byte[index];
											nearout.write(tempdata);
											out.write(tempdata, 0, index);
											Arrays.fill(temp, (byte) 100);
											farout.write(temp);
										}
										if (mIsRecordAudio) {
											filterBuffer.putAll(tempdata, index);
										}
										if (listener != null) {
											listener.onRecodeAudioData(tempdata, index, null);
										}
									}
									if (audioPlay != null && audioPlay.getState() == 1) {
										audioRecode.stop();
										audioRecode.startRecording();
										if (AECCore.this.playPosition > 0) {
											while (playBuffer.takeAll() != null) {
												// semp.release();
											}
											audioPlay.pause();
											audioPlay.flush();
											AECCore.this.playPosition = 0;
											AECCore.this.playCount = 0;
										}
									}

									isClearing = false;
									if (shouldClear) {
										shouldClear = false;
									}
									LogUtils.e(TAG, "clean end" + System.currentTimeMillis());
									AECCore.class.notify();
								}
								Thread.sleep(100);
								continue;
							}
							audioRecode.read(recodedata, 0, mFramesize);
							if (isDebug)
								endTime = System.currentTimeMillis();

							if (listener != null) {
								listener.onRecodeAudioData(recodedata, mFramesize, null);
							}
							if(mIsPlayAudio){
								WebRtcUtil.bufferFarendAndProcess(te, recodedata, filterBuff, mFramesize, msInSndCardBuf, 0,
									near_v, far_v);
							}else{
								System.arraycopy(recodedata, 0, filterBuff, 0, filterBuff.length);
							}
							
							if (isDebug) {
								starttime = System.currentTimeMillis();
								LogUtils.i(TAG, "AEC Processing Time = " + (starttime - endTime));
							}
							if (mIsRecordAudio) {
								filterBuffer.putAll(filterBuff, filterBuff.length);
							}
							if (isDebug) {
								nearout.write(recodedata);
								farout.write(te);
								out.write(filterBuff);
							}

						}

					} catch (Throwable e) {
						e.printStackTrace();
						LogUtils.e(TAG, "audiorecode err:" + e.getLocalizedMessage());
					} finally {
						AECCore.this.mIsRecodeWorking = false;
						if (audioRecode != null && audioRecode.getState() == audioRecode.STATE_INITIALIZED) {
							audioRecode.release();
						}
						audioRecode = null;
						if (nearout != null) {
							try {
								nearout.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						if (farout != null) {
							try {
								farout.close();
							} catch (Throwable e) {
								e.printStackTrace();
							}
						}
						if (out != null) {
							try {
								out.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						new Thread() {
							public void run() {
								try {
									Thread.sleep(100);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								synchronized (AECCore.class) {
									AECCore.class.notifyAll();
								}

							};
						}.start();
						LogUtils.e(TAG, "recode Thread stop");
					}
				}
			}, "RecodeThread");
			this.mRecordThread.setPriority(6);
			this.mRecordThread.start();
		}
	}
}