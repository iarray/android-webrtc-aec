package com.android.aec.core;

import com.android.aec.api.AudioFrame;
import com.android.aec.util.LogUtils;

public class FilterBuffer2 {
	private static int INFO_SIZE = AudioFrame.getInfoSize();
	private int mCapacity;
	private volatile int mWriteIndex;
	private volatile int mReadIndex;
	private byte[] mBuffer;
	private byte[] mTaken;
	private byte[] emptyInfo;
	private byte[] mData;
	private int mTakeSize;
	private boolean isNextLine;
	private boolean shouldCache;
	private int cacheIndex;
	private final int CACHE_MAX = 10;
	public FilterBuffer2(int capacity, int takeSize) {
		this.mCapacity = capacity;
		this.mTakeSize = takeSize+INFO_SIZE;
		this.mBuffer = new byte[this.mCapacity];
		this.mTaken = new byte[this.mTakeSize];
		this.mData = new byte[takeSize];
		this.mWriteIndex = 0;
		this.mReadIndex = 0;
	}
	public boolean putAll(byte[] info,int infosize,byte[] data,int len){
		int tmpIndex = this.mReadIndex;
		if (this.mWriteIndex >= tmpIndex) {
			if (this.mWriteIndex + len+infosize + 1 <= this.mCapacity) {
				System.arraycopy(info, 0, this.mBuffer, this.mWriteIndex, infosize);
				System.arraycopy(data, 0, this.mBuffer, this.mWriteIndex+infosize, len);
				addCacheIndex();
				this.mWriteIndex += len+infosize;
				return true;
			}
			if ((this.mWriteIndex + len +infosize+ 1) % this.mCapacity < tmpIndex) {
				int write_head_pos = 0;
				int write_data_pos = 0;
				System.arraycopy(info, 0, this.mBuffer, this.mWriteIndex, write_head_pos = this.mCapacity-this.mWriteIndex<= infosize?this.mCapacity-this.mWriteIndex: infosize);
				if(write_head_pos==infosize){
					System.arraycopy(data, 0, this.mBuffer, this.mWriteIndex+write_head_pos, write_data_pos = this.mCapacity-this.mWriteIndex-write_head_pos);
				}
				if (len+infosize - (this.mCapacity - this.mWriteIndex) > 0) {
					if(write_head_pos<infosize){
						System.arraycopy(info, write_head_pos, this.mBuffer, 0, infosize-write_head_pos);
						System.arraycopy(data, 0, this.mBuffer, infosize-write_head_pos, len);
					}else{
						System.arraycopy(data, write_data_pos, this.mBuffer, 0, len-write_data_pos);
					}
				}
				isNextLine = true;
				addCacheIndex();
				this.mWriteIndex = ((this.mWriteIndex + len+infosize) % this.mCapacity);
				return true;
			}

		} else if (this.mWriteIndex + len < tmpIndex) {
			System.arraycopy(info, 0, this.mBuffer, this.mWriteIndex, infosize);
			System.arraycopy(data, 0, this.mBuffer, this.mWriteIndex+infosize, len);
			addCacheIndex();
			this.mWriteIndex += len+infosize;
			return true;
		}
		return false;
	}
	public boolean putAll(byte[] data, int len) {
		if(emptyInfo==null){
			this.emptyInfo = new byte[INFO_SIZE];
		}
		return putAll(emptyInfo, emptyInfo.length, data, len);
	}
	private void addCacheIndex() {
		cacheIndex = cacheIndex+1;
		if(cacheIndex >= CACHE_MAX){
			shouldCache = false;
			cacheIndex = 0;
		}
	}
	public void release() {
		mBuffer = null;
		mTaken = null;
	}
	public void clear(){
		this.mWriteIndex = 0;
		this.mReadIndex = 0;
	}
	public boolean alignIndex() {
		if (this.mReadIndex == this.mWriteIndex) {
			return false;
		} else {
			this.mReadIndex = this.mWriteIndex;
			return true;
		}
	}


	public byte[] takeAll() {
		int tmpIndex = this.mWriteIndex;
		if(shouldCache){
			return null;
		}
		if (this.mReadIndex == tmpIndex) {
			LogUtils.i("TAG", "should Cache");
			shouldCache = true;
			return null;
		}
		if ((this.mReadIndex < tmpIndex) && (this.mReadIndex + this.mTakeSize <= tmpIndex)) {
			System.arraycopy(this.mBuffer, this.mReadIndex, this.mTaken, 0, this.mTakeSize);
			this.mReadIndex += this.mTakeSize;
			return this.mTaken;
		}

		if (this.mReadIndex + this.mTakeSize <= this.mCapacity&&isNextLine) {
			System.arraycopy(this.mBuffer, this.mReadIndex, this.mTaken, 0, this.mTakeSize);
			if (this.mReadIndex > tmpIndex) {
				this.mReadIndex = ((this.mReadIndex + this.mTakeSize) % this.mCapacity);
				if(mReadIndex<tmpIndex){
					isNextLine = false;
				}
				return this.mTaken;
			}

		}
		if ((this.mReadIndex + this.mTakeSize) % this.mCapacity <= tmpIndex) {
			System.arraycopy(this.mBuffer, this.mReadIndex, this.mTaken, 0, this.mCapacity - this.mReadIndex);
			System.arraycopy(this.mBuffer, 0, this.mTaken, this.mCapacity - this.mReadIndex,
					this.mTakeSize - (this.mCapacity - this.mReadIndex));
			this.mReadIndex = ((this.mReadIndex + this.mTakeSize) % this.mCapacity);
			if(mReadIndex<tmpIndex){
				isNextLine = false;
			}
			return this.mTaken;
		}

		return null;
	}
	public boolean putAll(AudioFrame frame){
		if(frame.getAudioData()==null){
			return false;
		}
		return putAll(frame.getInfoData(), AudioFrame.getInfoSize(), frame.getAudioData(), frame.dataLength());
	}
	public boolean getFrame(AudioFrame frame){
		byte[] data = takeAll();
		if(data==null){
			return false;
		}
		System.arraycopy(data, INFO_SIZE, this.mData, 0, this.mData.length);
		frame.setPos(0xFF & data[8] | (0xFF & data[9]) << 8 | (0xFF & data[10]) << 16
				| (0xFF & data[11]) << 24);
		frame.setTimeStamp(0xFF & data[12] | (0xFF & data[13]) << 8 | (0xFF & data[14]) << 16
				| (0xFF & data[15]) << 24);
		frame.setAudioData(this.mData, this.mData.length);
		return true;
	}
}