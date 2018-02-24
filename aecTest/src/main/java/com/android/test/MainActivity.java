package com.android.test;

import android.app.Activity;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;


import com.android.aec.api.AudioFrame;
import com.android.aec.api.AudioPlayer;
import com.android.aec.api.Frequency;
import com.android.aec.api.OnTSUpdateListener;
import com.android.aec.api.RecodeAudioDataListener;
import com.android.aec.util.AudioUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class MainActivity extends Activity implements RecodeAudioDataListener, OnTSUpdateListener {
	AudioPlayer audioPlayer;
	File mFilterFile, mRecodeFile;
	BufferedOutputStream mRecodeStream;
	Thread mPlayThread;
	Thread mSendThread;
	boolean isPlay;
	Frequency hz = Frequency.PCM_8K;
	int frameSize = 800;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		mFilterFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test_filter.pcm");
		mRecodeFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/test_recode.pcm");
	}

	public void onClick(View v) {
		if (isPlay) {
			return;
		}
		v.setEnabled(false);
		isPlay = true;
		mRecodeStream = openStreamByFile(mRecodeFile);
		
		audioPlayer = AudioPlayer.getInstance().disableSystemAEC().init(this, this,hz,frameSize);
		audioPlayer.setPtsUpdateListener(this);
		audioPlayer.start(true, true);
		mPlayThread = new Thread("PlayThread") {
			public void run() {
				BufferedInputStream in_far = null;
				try {
					in_far = new BufferedInputStream(getResources().getAssets().open(Frequency.PCM_8K==hz?"far_8k.pcm":"far_16k.pcm"));
					byte[] data = new byte[frameSize];
					int length = 0;
					AudioFrame frame = new AudioFrame();
					while (isPlay) {
						length = in_far.read(data, 0, data.length);
						if (length == data.length) {
//							Thread.sleep(70);
							frame.setAudioData(data, length);
							frame.setTimeStamp(System.currentTimeMillis());
							Log.i("TAG", "time:"+System.currentTimeMillis());
							while(!audioPlayer.putPlayData(frame)){
								Log.i("TAG", "putPlayData sleep");
								Thread.sleep(10);
							}
							length = 0;
						} else {
							Log.e("TAG", "in_far.close");
							in_far.close();
							Thread.sleep(5000);
							in_far = new BufferedInputStream(getResources().getAssets().open(Frequency.PCM_8K==hz?"far.pcm":"far2.pcm"));
						}
					}
				} catch (Throwable e) {
					e.printStackTrace();
				} finally {
					if (in_far != null) {
						try {
							in_far.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		};
		mPlayThread.start();
		mSendThread = new Thread("SendThread") {
			public void run() {
				BufferedOutputStream out = null;
				byte[] filterData = null;
				try {
					out = openStreamByFile(mFilterFile);
					while (isPlay) {
						filterData = audioPlayer.getFilterData();
						if (filterData != null && out != null) {
							Log.i("TAG", "filter Data size ="+filterData.length);
							out.write(filterData);
						} else {
							Log.i("TAG", "filter Data size = null");
							Thread.sleep(10);
						}

					}

				} catch (Throwable e) {
					e.printStackTrace();
				} finally {
					try {
						if (out != null) {
							out.flush();
							out.close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
		};
		mSendThread.start();
	}

	/**
	 * 停止回声消除
	 * 
	 * @param v
	 */
	public void stopAEC(View v) {
		isPlay = false;
		try {
			if (mPlayThread != null) {
				mPlayThread.interrupt();
				mPlayThread = null;
			}
			if (mSendThread != null) {
				mSendThread.interrupt();
				mSendThread = null;
			}
			if (mRecodeStream != null) {
				mRecodeStream.flush();
				mRecodeStream.close();
				mRecodeStream = null;
			}
			audioPlayer.stop();
			audioPlayer.release();
		} catch (IOException e) {
			e.printStackTrace();
		}
		findViewById(R.id.btn_strat).setEnabled(true);
	}

	/**
	 * 播放回声消除后的音频文件
	 * 
	 * @param v
	 */
	public void playFilterSound(View v) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					AudioTrack audioTrack = AudioUtils.createTracker(new int[1],hz.value(), hz.value()/10,false);
					if (mFilterFile.exists() && mFilterFile.length() > 0) {
						BufferedInputStream in = new BufferedInputStream(new FileInputStream(mFilterFile));
						byte[] data = new byte[hz.value()/10];
						int len = 0;
						while ((len = in.read(data)) > 0) {
							audioTrack.play();
							audioTrack.write(data, 0, len);
						}
						audioTrack.release();
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}

			}
		}).start();
	}
	/**
	 * 播放未做回声消除的音频文件
	 * @param v
	 */
	public void playRecodeSound(View v){
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					AudioTrack audioTrack = AudioUtils.createTracker(new int[1],hz.value(),hz.value()/10,false);
					if (mRecodeFile.exists() && mRecodeFile.length() > 0) {
						BufferedInputStream in = new BufferedInputStream(new FileInputStream(mRecodeFile));
						byte[] data = new byte[hz.value()/10];
						int len = 0;
						while ((len = in.read(data)) > 0) {
							audioTrack.play();
							audioTrack.write(data, 0, len);
						}
						audioTrack.release();
					}
				} catch (Throwable t) {
					t.printStackTrace();
				}

			}
		}).start();
	}
	/**
	 * 打开/关闭声音
	 * 
	 * @param v
	 */
	public void switchSound(View v) {
		if ("打开声音".equals(((Button) v).getText())) {
			audioPlayer.soundOn();
			((Button) v).setText("关闭声音");
		} else {
			audioPlayer.soundOff();
			((Button) v).setText("打开声音");
		}
	}

	/**
	 * 打开/关闭录音
	 * 
	 * @param v
	 */
	public void switchSpeak(View v) {
		if ("关闭录音".equals(((Button) v).getText())) {
			audioPlayer.pauseAudioRecord();
			((Button) v).setText("打开录音");
		} else {
			audioPlayer.resumeAudioRecord();
			((Button) v).setText("关闭录音");
		}
	}

	@Override
	protected void onDestroy() {
		if(audioPlayer!=null) {
			audioPlayer.stop();
		}
		super.onDestroy();
	}

	public static BufferedOutputStream openStreamByFile(File file) {
		BufferedOutputStream out = null;
		try {
			if (file.exists()) {
				file.delete();
			} else {
				file.createNewFile();
			}
			out = new BufferedOutputStream(new FileOutputStream(file, true));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return out;
	}

	// 录制的原声音频数据回调接口 runOnBackThread
	@Override
	public int onRecodeAudioData(byte[] data, int length, byte[] imfo) {
		if (mRecodeStream != null) {
			try {
				mRecodeStream.write(data, 0, length);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return 0;
	}

	@Override
	public void onUpdate(long pts) {
		Log.i("TAG", "pts:"+pts);
	}

}
