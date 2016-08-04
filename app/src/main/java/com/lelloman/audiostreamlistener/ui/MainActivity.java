package com.lelloman.audiostreamlistener.ui;

import android.app.ActivityManager;
import android.app.DialogFragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.lelloman.audiostreamlistener.AudioClientService;
import com.lelloman.audiostreamlistener.R;
import com.lelloman.audiostreamlistener.SharedPrefsUtil;
import com.lelloman.audiostreamlistener.threading.AudioClient;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, ProbeNetworkDialogFragment.ProbeNetworkDialogFragmentListener {

	public static final String TAG = MainActivity.class.getSimpleName();

	Button mButton;
	EditText mPortEditText;
	AutoCompleteTextView mAddressView;
	SeekBar mSkipFrameSeekBar;
	SeekBar mVolumeSeekBar;
	TextView mLogTextView;
	ScrollView mScrollView;

	ConnectionUpdateReceiver mConnectionUpdateReceiver;
	ArrayAdapter<String> mAddressesAdapter;
	List<String> mAddresses = new ArrayList<>();

	private boolean mServiceBound;
	private AudioClientService mBoundService;

	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mServiceBound = false;
			mBoundService = null;
			unbindService(mServiceConnection);
			log("onServiceDisconnected()");
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			AudioClientService.MyBinder myBinder = (AudioClientService.MyBinder) service;
			mBoundService = myBinder.getService();
			mServiceBound = true;

			log("onServiceConnected()");
		}
	};
	;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mButton = (Button) findViewById(R.id.button);
		mPortEditText = (EditText) findViewById(R.id.port_edit_text);
		mAddressView = (AutoCompleteTextView) findViewById(R.id.address_autocomplete_text_view);
		mSkipFrameSeekBar = (SeekBar) findViewById(R.id.skip_frame_seek_bar);
		mVolumeSeekBar = (SeekBar) findViewById(R.id.volume_seek_bar);
		mLogTextView = (TextView) findViewById(R.id.text_view_log);
		mScrollView = (ScrollView) findViewById(R.id.scroll_view);

		mButton.setOnClickListener(this);
		mPortEditText.setText(String.valueOf(SharedPrefsUtil.getLastPortSelected(this)));

		// defocus everything when done inputting stuff
		// damned AutoCompleteTextView it keeps open the drop down
		mPortEditText.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
			if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
				findViewById(R.id.container).postDelayed(() -> {
					findViewById(R.id.container).requestFocus();
					InputMethodManager inputManager = (InputMethodManager)
							getSystemService(Context.INPUT_METHOD_SERVICE);
					inputManager.toggleSoftInput(0, 0);
				}, 100);

				return true;
			}
			return false;
		});


		mAddressView.setText(SharedPrefsUtil.getLastAddressSelected(this));
		mAddresses = SharedPrefsUtil.getAddresses(this);
		mAddressesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, mAddresses);
		mAddressView.setAdapter(mAddressesAdapter);

		mSkipFrameSeekBar = (SeekBar) findViewById(R.id.skip_frame_seek_bar);
		mSkipFrameSeekBar.setProgress(SharedPrefsUtil.getLastSkipFrameEveryTot(this));
		mSkipFrameSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangedListener() {
			@Override
			public void onProgressChanged(float v) {
				int skipEveryTot = (int) (10 + 990 * v);
				SharedPrefsUtil.setLastSkipFrameEveryTot(MainActivity.this, skipEveryTot);

				if (isMyServiceRunning(AudioClientService.class))
					sendSkipFrameEveryTot(skipEveryTot);

				log("setSkipFrameEveryTot %s", skipEveryTot);
			}
		});

		mVolumeSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangedListener() {
			@Override
			public void onProgressChanged(float v) {

				AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
				if (audioManager == null)
					return;

				int volume = (int) (v * audioManager.getStreamMaxVolume(AudioClient.STREAM_TYPE));
				audioManager.setStreamVolume(AudioClient.STREAM_TYPE, volume, AudioManager.FLAG_PLAY_SOUND);
				SharedPrefsUtil.setVolume(MainActivity.this, v);

				log("setVolume() %.2f of %s", v, audioManager.getStreamMaxVolume(AudioClient.STREAM_TYPE));
			}
		});
		mVolumeSeekBar.setProgress((int) (SharedPrefsUtil.getVolume(this) * mVolumeSeekBar.getMax()));

		boolean serviceRunning = isMyServiceRunning(AudioClientService.class);
		setUiConnected(serviceRunning);
		mLogTextView.setText(SharedPrefsUtil.getLog(this));
		// lambdas <3 <3 <3
		mScrollView.post(() -> 	mScrollView.fullScroll(View.FOCUS_DOWN));

	}

	@Override
	public void onClick(View view) {

		boolean running = isMyServiceRunning(AudioClientService.class);

		if (running)
			stopService();
		else
			startService();

		setUiConnected(!running);
	}

	private void startService() {

		log("startService()");
		int port = getPort();
		mPortEditText.setText(String.valueOf(port));
		String address = mAddressView.getText().toString();

		SharedPrefsUtil.setLastPortSelected(this, port);
		SharedPrefsUtil.setLastAddressSelected(this, address);

		mAddresses.remove(address);
		mAddresses.add(address);
		SharedPrefsUtil.setAddresses(this, mAddresses);
		mAddressesAdapter.notifyDataSetChanged();

		Intent intent = AudioClientService.makeStartIntent(this, address, port);
		startService(intent);
		bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
	}

	private void stopService() {
		log("stopService()");
		if (mServiceBound) {
			unbindService(mServiceConnection);
			mServiceBound = false;
			mBoundService = null;
		}
		stopService(new Intent(this, AudioClientService.class));
	}

	/**
	 * read the value from the EditText and parse it as an int
	 */
	private int getPort() {
		String p = mPortEditText.getText().toString();
		p = p.replaceAll("\\D", "");

		int port = Math.abs(Integer.parseInt(p));
		if (port > 0xffff) port = 0xffff;

		return port;
	}

	/**
	 * 	a copy-paste, hope it's reliable
	 */
	private boolean isMyServiceRunning(Class<?> serviceClass) {
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(service.service.getClassName())) {
				return true;
			}
		}
		return false;
	}

	private void sendSkipFrameEveryTot(int i) {
		if (mServiceBound && mBoundService != null)
			mBoundService.setSkipFrameEveryTot(i);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mConnectionUpdateReceiver == null)
			mConnectionUpdateReceiver = new ConnectionUpdateReceiver();

		IntentFilter intentFilter = new IntentFilter(AudioClientService.EVENT_CONNECTION_CLOSED);
		intentFilter.addAction(AudioClientService.EVENT_UPDATE_LOG);
		registerReceiver(mConnectionUpdateReceiver, intentFilter);

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mConnectionUpdateReceiver != null) {
			unregisterReceiver(mConnectionUpdateReceiver);
			mConnectionUpdateReceiver = null;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mServiceBound) {
			unbindService(mServiceConnection);
			mServiceBound = false;
			mBoundService = null;
		}
	}

	private void setUiConnected(boolean connected) {

		if (mButton == null)
			return;

		mButton.setText(connected ? "STOP" : "START");
		mAddressView.setEnabled(!connected);
		mSkipFrameSeekBar.setEnabled(connected);
		mPortEditText.setEnabled(!connected);

		// prevent or close AutoCompleteTextView's drop down
		findViewById(R.id.container).requestFocus();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.action_probe_network:
				probeNetwork();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void probeNetwork() {
		DialogFragment dialogFragment = new ProbeNetworkDialogFragment();
		dialogFragment.show(getFragmentManager(), ProbeNetworkDialogFragment.TAG);
	}

	/**
	 * 	called from {@link ProbeNetworkDialogFragment} when a pong message is received
	 */
	@Override
	public void onServerFound(InetAddress serverAddress) {
		Toast.makeText(this, String.format(getString(R.string.server_found), serverAddress.getHostAddress()), Toast.LENGTH_SHORT).show();
		if (mAddressView != null) {
			mAddressView.setText(serverAddress.getHostAddress());
			mButton.performClick();
		}
	}

	@Override
	public void onNothingFound(Void nothing) {
		Toast.makeText(this, R.string.nope, Toast.LENGTH_SHORT).show();
	}

	/**
	 * 	{@link AudioClientService} can notify when the connection has been closed
	 * 	or when the log is updated
	 */
	private class ConnectionUpdateReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			switch (action) {
				case AudioClientService.EVENT_CONNECTION_CLOSED:
					setUiConnected(false);
					if (mServiceBound) {
						mServiceBound = false;
						unbindService(mServiceConnection);
						mBoundService = null;
					}
					break;
				case AudioClientService.EVENT_UPDATE_LOG:
					String log = intent.getStringExtra(AudioClientService.EXTRA_LOG_STRING);
					if (log != null && mLogTextView != null)
						mLogTextView.setText(log);
					break;
			}
		}
	}

	private void log(String msg, Object... args) {
		Log.d(TAG, String.format(msg, args));
	}

	private abstract static class OnSeekBarChangedListener implements SeekBar.OnSeekBarChangeListener {

		@Override
		public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			onProgressChanged(seekBar.getProgress() / (float) seekBar.getMax());
		}

		protected abstract void onProgressChanged(float progressPercent);
	}
}
