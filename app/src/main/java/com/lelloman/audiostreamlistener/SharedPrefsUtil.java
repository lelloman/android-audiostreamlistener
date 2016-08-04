package com.lelloman.audiostreamlistener;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * somebody has to do it
 */
public class SharedPrefsUtil {

	public static final String SHARED_PREFS_NAME = "AudioStreamListener";
	public static final String SHARED_PREFS_PORT = "port";
	public static final String SHARED_PREFS_ADDRESS = "address";
	public static final String SHARED_PREFS_SKIP_FRAME = "skipFrame";
	public static final String SHARED_PREFS_ADDRESSES = "addresses";
	public static final String SHARED_PREFS_LOG = "log";
	public static final String SHARED_PREFS_VOLUME = "volume";

	private SharedPrefsUtil(){}

	public static void setVolume(Context context, float v){
		sharedPrefs(context).edit().putFloat(SHARED_PREFS_VOLUME, v).commit();
	}
	public static float getVolume(Context context){
		return sharedPrefs(context).getFloat(SHARED_PREFS_VOLUME, .5f);
	}

	public static void setLog(Context context, String log){
		sharedPrefs(context).edit().putString(SHARED_PREFS_LOG, log).commit();
	}
	public static String getLog(Context context){
		return sharedPrefs(context).getString(SHARED_PREFS_LOG, "");
	}

	public static void setAddresses(Context context, List<String> list){
		sharedPrefs(context).edit().putStringSet(SHARED_PREFS_ADDRESSES, new HashSet<>(list)).commit();
	}
	public static ArrayList<String> getAddresses(Context context){
		return new ArrayList<>(sharedPrefs(context).getStringSet(SHARED_PREFS_ADDRESSES,new HashSet<>()));
	}

	public static void setLastAddressSelected(Context context, String address) {
		sharedPrefs(context).edit().putString(SHARED_PREFS_ADDRESS, address).commit();
	}
	public static String getLastAddressSelected(Context context){
		return sharedPrefs(context).getString(SHARED_PREFS_ADDRESS,"");
	}

	public static void setLastSkipFrameEveryTot(Context context, int i) {
		sharedPrefs(context).edit().putInt(SHARED_PREFS_SKIP_FRAME, i).commit();
	}
	public static int getLastSkipFrameEveryTot(Context context) {
		return sharedPrefs(context).getInt(SHARED_PREFS_SKIP_FRAME, Constants.DEFAULT_SKIP_FRAME);
	}

	public static void setLastPortSelected(Context context,int port) {
		sharedPrefs(context).edit().putInt(SHARED_PREFS_PORT, port).commit();
	}
	public static int getLastPortSelected(Context context) {
		return sharedPrefs(context).getInt(SHARED_PREFS_PORT, Constants.DEFAULT_PORT);
	}

	private static SharedPreferences sharedPrefs(Context context) {
		return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
	}
}
