package com.android.aec.core;

import com.android.aec.util.LogUtils;

public class RingBuffer {
	private int mCapacity;
	private volatile int mWriteIndex;
	private volatile int mReadIndex;
	private byte[] mBuffer;
	private byte[] mTaken;
	private int mTakeSize;
	private volatile boolean shouldCache;
	private volatile int cacheIndex;
	private final int CACHE_MAX = 20;

	public RingBuffer(int capacity, int takeSize) {
		this.mCapacity = capacity;
		this.mTakeSize = takeSize;
		this.mBuffer = new byte[capacity];
		this.mTaken = new byte[takeSize];
		this.mWriteIndex = 0;
		this.mReadIndex = 0;
	}

	public boolean putAll(byte[] data, int len) {
		int tmpIndex = this.mReadIndex;

		if (this.mWriteIndex >= tmpIndex) {
			if (this.mWriteIndex + len + 1 <= this.mCapacity) {
				System.arraycopy(data, 0, this.mBuffer, this.mWriteIndex, len);
				addCacheIndex();
				this.mWriteIndex += len;
				return true;
			}
			if ((this.mWriteIndex + len + 1) % this.mCapacity < tmpIndex) {
				System.arraycopy(data, 0, this.mBuffer, this.mWriteIndex, this.mCapacity - this.mWriteIndex);
				if (len - (this.mCapacity - this.mWriteIndex) > 0) {
					System.arraycopy(data, this.mCapacity - this.mWriteIndex, this.mBuffer, 0,
							len - (this.mCapacity - this.mWriteIndex));
				}
				addCacheIndex();
				this.mWriteIndex = ((this.mWriteIndex + len) % this.mCapacity);
				return true;
			}

		} else if (this.mWriteIndex + len < tmpIndex) {
			System.arraycopy(data, 0, this.mBuffer, this.mWriteIndex, len);
			addCacheIndex();
			this.mWriteIndex += len;
			return true;
		}

		return false;
	}

	public void release() {
		mBuffer = null;
		mTaken = null;
	}

	public boolean alignIndex() {
		if (this.mReadIndex == this.mWriteIndex) {
			return false;
		} else {
			this.mReadIndex = this.mWriteIndex;
			return true;
		}
	}

	private void addCacheIndex() {
		if (!shouldCache) {
			return;
		}
		cacheIndex = cacheIndex + 1;
		if (cacheIndex == CACHE_MAX) {
			shouldCache = false;
			cacheIndex = 0;
		}
	}

	public void shouldCache(boolean shouldCache) {
		LogUtils.i("TAG", "shouldCache");
		this.shouldCache = shouldCache;
	}

	public boolean isCaching() {
		return this.shouldCache;
	}

	public byte[] takeAll() {
		int tmpIndex = this.mWriteIndex;
		if (shouldCache) {
			return null;
		}
		if (this.mReadIndex == tmpIndex) {
			return null;
		}
		if ((this.mReadIndex < tmpIndex) && (this.mReadIndex + this.mTakeSize <= tmpIndex)) {
			System.arraycopy(this.mBuffer, this.mReadIndex, this.mTaken, 0, this.mTakeSize);
			this.mReadIndex += this.mTakeSize;
			return this.mTaken;
		}

		if (this.mReadIndex + this.mTakeSize <= this.mCapacity) {
			System.arraycopy(this.mBuffer, this.mReadIndex, this.mTaken, 0, this.mTakeSize);
			if (this.mReadIndex > tmpIndex) {
				this.mReadIndex = ((this.mReadIndex + this.mTakeSize) % this.mCapacity);
				return this.mTaken;
			}

		}
		if ((this.mReadIndex + this.mTakeSize) % this.mCapacity <= tmpIndex) {
			System.arraycopy(this.mBuffer, this.mReadIndex, this.mTaken, 0, this.mCapacity - this.mReadIndex);
			System.arraycopy(this.mBuffer, 0, this.mTaken, this.mCapacity - this.mReadIndex,
					this.mTakeSize - (this.mCapacity - this.mReadIndex));
			this.mReadIndex = ((this.mReadIndex + this.mTakeSize) % this.mCapacity);
			return this.mTaken;
		}

		return null;
	}

}