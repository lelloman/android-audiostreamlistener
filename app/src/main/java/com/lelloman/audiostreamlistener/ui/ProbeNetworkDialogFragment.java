package com.lelloman.audiostreamlistener.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.lelloman.audiostreamlistener.R;
import com.lelloman.audiostreamlistener.threading.NetworkPingBroadcaster;

import java.net.InetAddress;


public class ProbeNetworkDialogFragment extends DialogFragment implements NetworkPingBroadcaster.NetworkProbeListener {

	public interface ProbeNetworkDialogFragmentListener {
		void onServerFound(InetAddress serverAddress);
		void onNothingFound(Void nothing);
	}

	public static final String TAG = ProbeNetworkDialogFragment.class.getSimpleName();

	private ProgressBar mProgressBar;
	private TextView mTextView;
	private String mLastMsg = "";
	private NetworkPingBroadcaster mNetworkPingBroadcaster;
	private ProbeNetworkDialogFragmentListener mListener;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		mListener = (ProbeNetworkDialogFragmentListener) context;
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		mNetworkPingBroadcaster = new NetworkPingBroadcaster(this);
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {

		View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_probe_network,null);
		mTextView = (TextView) view.findViewById(R.id.text_view);
		mTextView.setText(mLastMsg);

		mProgressBar = (ProgressBar) view.findViewById(R.id.progress_bar);

		return new AlertDialog.Builder(getActivity())
				.setView(view)
				.setCancelable(false)
				.setPositiveButton(R.string.stop, null)
				.create();
	}

	@Override
	public void onDestroyView() {
		// >_> http://stackoverflow.com/a/12434038/1527232
		if (getDialog() != null && getRetainInstance()) {
			getDialog().setDismissMessage(null);
		}
		super.onDestroyView();
		mTextView = null;
		mProgressBar = null;
	}

	@Override
	public void onProgressUpdate(float progress) {
		if(mProgressBar != null)
			/*mProgressBar.post(() -> */mProgressBar.setProgress((int) (progress * mProgressBar.getMax()))/*)*/;
	}

	@Override
	public void onMessageUpdate(String msg){
		if(mTextView != null){
		//	mTextView.post(() -> {
				mLastMsg = msg;
				mTextView.setText(msg);
			//	});
		}
	}

	@Override
	public void onServerFound(InetAddress serverAddress) {
		try {
			printMessageAndDismissDelayed(String.format(getString(R.string.server_found), serverAddress.getHostAddress()), serverAddress);
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	@Override
	public void onNetworkProbeFailed() {
		try {
			printMessageAndDismissDelayed(getString(R.string.nope), null);
		}catch (Exception e){
			e.printStackTrace();
		}
	}

	private void printMessageAndDismissDelayed(String message, InetAddress address){

		if(!isResumed())
			return;

		onMessageUpdate(message);

		// so that the message can be read before dismissing the dialog
		new Handler().postDelayed(() ->{

			if(mListener != null){
				if(address == null){
					mListener.onNothingFound(null);
				}else{
					mListener.onServerFound(address);
				}
			}
			dismiss();
		}, 500);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(mNetworkPingBroadcaster != null){
			mNetworkPingBroadcaster.kill();
			mNetworkPingBroadcaster = null;
		}
	}


	private synchronized void log(String msg, Object...args){
		Log.d(TAG, String.format(msg, args));
	}
}
