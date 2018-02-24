package com.android.aec.api;

public class AudioFrame {
	private static final int INFO_SIZE = 16;
	private byte[] info;
	private byte[] data;
	private int length;
	public AudioFrame() {
		this.info = new byte[16];
	}
	public void setAudioData(byte[] data,int length){
		this.data = data;
		this.length = length;
	}
	public byte[] getAudioData(){
		return this.data;
	}
	public int dataLength(){
		return this.length;
	}
	public void setTimeStamp(long timeStamp) {
		this.info[12] = (byte) (int) timeStamp;
		this.info[13] = (byte) (int) (timeStamp >>> 8);
		this.info[14] = (byte) (int) (timeStamp >>> 16);
		this.info[15] = (byte) (int) (timeStamp >>> 24);
	}

	public long getTimeStamp() {
		return (0xFF & this.info[12] | (0xFF & this.info[13]) << 8 | (0xFF & this.info[14]) << 16
				| (0xFF & this.info[15]) << 24);
	}

	public void setPos(int pos) {
		this.info[8] = (byte) pos;
		this.info[9] = (byte) (pos >>> 8);
		this.info[10] = (byte) (pos >>> 16);
		this.info[11] = (byte) (pos >>> 24);
	}

	public long getPos() {
		return (0xFF & this.info[8] | (0xFF & this.info[9]) << 8 | (0xFF & this.info[10]) << 16
				| (0xFF & this.info[11]) << 24);
	}

	public byte[] getInfoData() {
		return this.info;
	}

	public static int getInfoSize() {
		return INFO_SIZE;
	}


}
