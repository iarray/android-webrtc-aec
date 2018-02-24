package com.android.aec.conf;

import com.android.aec.api.InitLibrary;

public class Setting {
	public static boolean getAECDebug(){
		return PluginConfig.AECDebug;
	}
	public static boolean getLogDebug(){
		return PluginConfig.LogDebug;
	}
	public static String getVersion(){
		return PluginConfig.VERSION;
	}
	public static InitLibrary loadLibrary(){
		return PluginConfig.loadLibrary;
	}
	public static void setLoadLibrary(InitLibrary lib){
		PluginConfig.loadLibrary = lib;
	}
}
