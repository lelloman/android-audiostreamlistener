package com.lelloman.audiostreamlistener.threading;

import android.util.Log;

import com.lelloman.audiostreamlistener.Constants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * listen for a pong message on the given socket and
 * notify the listener when received
 */
public class NetworkPongReceiver extends Thread {

	public static final String TAG = NetworkPongReceiver.class.getSimpleName();

	public interface NetworkPongReceiverListener {
		void onPongReceived(InetAddress inetAddress);
	}

	private NetworkPongReceiverListener mListener;
	private DatagramSocket mSocket;
	private boolean mRunning;

	public NetworkPongReceiver(DatagramSocket socket, NetworkPongReceiverListener listener){
		mSocket = socket;
		mListener = listener;
	}

	@Override
	public void run() {
		super.run();
		mRunning = true;

		DatagramPacket packet = new DatagramPacket(new byte[4],4);

		while(mRunning) {
			try {
				Arrays.fill(packet.getData(), (byte) 0);
				mSocket.receive(packet);

				int msg = ByteBuffer.wrap(packet.getData()).asIntBuffer().get();
				Log.d(TAG,"msg = "+String.valueOf(msg));
				if(msg == Constants.MSG_PONG){
					Log.d(TAG,"PONG RECEIVED from "+packet.getAddress().getHostName());
					mRunning = false;
					mListener.onPongReceived(packet.getAddress());
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Log.d(TAG, "run() end");
	}

	public void kill(){
		Log.d(TAG, "kill()");
		mRunning = false;
	}
}
