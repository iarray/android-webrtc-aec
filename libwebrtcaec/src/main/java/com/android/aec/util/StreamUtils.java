package com.android.aec.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class StreamUtils {
	public static BufferedOutputStream openStreamByFile(String fileName) throws Throwable{
		File file = new File(fileName);
		if(file.exists()){
			file.delete();
		}else{
			file.createNewFile();
		}
		return new BufferedOutputStream(new FileOutputStream(file));
	}
}
