package com.lelloman.audiostreamlistener;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.lelloman.audiostreamlistener.threading.AudioClient;
import com.lelloman.audiostreamlistener.ui.MainActivity;

import java.text.SimpleDateFormat;


public class AudioClientService extends Service implements AudioClient.AudioClientListener {

	public static final String TAG = AudioClientService.class.getSimpleName();
	public static final int NOTIFICATION_ID = 0xb00b5;
	public static final int LOG_MAX_LENGTH = 2000;
	public static final SimpleDateFormat LOG_SDF = new SimpleDateFormat("HH:mm:ss");

	public static final String ARG_ADDRESS = "address";
	public static final String ARG_PORT = "port";
	public static final String EXTRA_LOG_STRING = "logString";
	public static final String EVENT_CONNECTION_CLOSED = "com.lelloman.audiostreamlistener.AudioClientService.EVENT_CONNECTION_CLOSED";
	public static final String EVENT_UPDATE_LOG = "com.lelloman.audiostreamlistener.AudioClientService.EVENT_UPDATE_LOG";

	public static Intent makeStartIntent(Context caller, String address, int port) {

		Intent intent = new Intent(caller, AudioClientService.class);

		intent.putExtra(ARG_ADDRESS, address);
		intent.putExtra(ARG_PORT, port);

		return intent;
	}

	// receive the stream and play it in
	// a separate thread
	private AudioClient mAudioClient;

	// needed to set skip frame from the activity
	private final MyBinder mBinder = new MyBinder();

	// a log stored in shared prefs and broadcast
	// by an Intent
	private String mLog = "";

	// server info
	private String mAddress;
	private int mPort;

	// stream stat displayed in the notification
	private long mByteCount;
	private long mDuration;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		Log.d(TAG, "onStartCommand()");
		synchronized (mLog) {
			mLog = SharedPrefsUtil.getLog(this);
		}
		updateLog("%s start", TAG);
		startServer(intent);

		return Service.START_NOT_STICKY;
	}

	private void startServer(Intent intent) {
		mAddress = intent.getStringExtra(ARG_ADDRESS);
		mPort = intent.getIntExtra(ARG_PORT, 0);

		makeNotification();

		if (mAudioClient == null) {
			mAudioClient = new AudioClient(mAddress, mPort, this);
			mAudioClient.start();
		}
	}

	private void makeNotification(){
		Intent notificationIntent = new Intent(this, MainActivity.class);
		notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_CLEAR_TASK);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		Notification notification = new NotificationCompat.Builder(this)
				.setContentTitle(String.format("%s:%s", mAddress, mPort))
				.setContentText(formatStat())
				.setSmallIcon(R.drawable.ic_hearing_black_24dp)
				.setContentIntent(pendingIntent)
				.setOngoing(true).build();

		startForeground(NOTIFICATION_ID, notification);
	}

	public void setSkipFrameEveryTot(int i){
		if(mAudioClient != null)
			mAudioClient.setSkipFrameEveryTot(i);
	}
	@Override
	public boolean onUnbind(Intent intent) {
		return true;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mAudioClient != null) {
			mAudioClient.kill();
			mAudioClient = null;
		}
	}

	@Override
	public void onConnectionClosed() {
		Log.d(TAG, "onConnectionClosed()");
		updateLog("onConnectionClosed()");
		stopSelf();
		sendBroadcast(new Intent(EVENT_CONNECTION_CLOSED));
	}

	/**
	 * {@link AudioClient} notify this and it will start playing
	 * the stream so just log a message here
	 */
	@Override
	public void onStreamConfigReceived(AudioClient.StreamConfig streamConfig) {
		updateLog("StreamConfig received - buffer size: %s bit depth: %s sample rate: %s", streamConfig.bufferSize, streamConfig.bitDepth, streamConfig.sampleRate);
	}

	@Override
	public void onStatsUpdate(long byteCount, long duration) {
		mByteCount = byteCount;
		mDuration = duration;
		makeNotification();
	}

	private String formatStat(){
		final double KB = 1000;
		final double MB = 1000000;
		String unit;
		String amount;

		if(mByteCount < KB){
			unit = "byte";
			amount = String.valueOf(mByteCount);
		}else if(mByteCount < MB){
			unit = "Kb";
			amount = String.format("%.1f", mByteCount / KB);
		}else{
			unit = "Mb";
			amount = String.format("%.1f", mByteCount / MB);
		}

		long s = mDuration / 1000;
		String duration = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
		return String.format("\n%s %s - %s", amount, unit, duration);
	}

	private void updateLog(String msg, Object...args){
		synchronized (mLog) {
			mLog += String.format("[%s] - %s\n", LOG_SDF.format(System.currentTimeMillis()), String.format(msg, args));

			while (mLog.length() > LOG_MAX_LENGTH) {
				int begin = mLog.indexOf('\n');
				if (begin > -1) {
					mLog = mLog.substring(begin + 1);
				}
			}
			SharedPrefsUtil.setLog(this, mLog);
			Intent intent = new Intent(EVENT_UPDATE_LOG);
			intent.putExtra(EXTRA_LOG_STRING, mLog);
			sendBroadcast(intent);
		}
	}

	public class MyBinder extends Binder {
		public AudioClientService getService() {
			return AudioClientService.this;
		}
	}

}
