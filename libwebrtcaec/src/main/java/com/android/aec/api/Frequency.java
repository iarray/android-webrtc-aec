package com.android.aec.api;

public enum Frequency {
	PCM_8K(8000), PCM_16K(16000);
	private int value;

	Frequency(int value) {
		this.value = value;
	}

	public int value() {
		return value;
	}
}
