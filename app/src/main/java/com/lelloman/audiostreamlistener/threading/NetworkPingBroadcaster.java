package com.lelloman.audiostreamlistener.threading;

import android.os.AsyncTask;
import android.util.Log;

import com.lelloman.audiostreamlistener.Constants;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 	Makes a list of addresses based on the local one and
 * 	spam them with ping messages, at the same time listen
 * 	for a pong message and if it receives one it consider the
 * 	task successful
 */
public class NetworkPingBroadcaster extends AsyncTask<Void, Object, Void> {

	public interface NetworkProbeListener {
		void onProgressUpdate(float progress);
		void onMessageUpdate(String msg);
		void onServerFound(InetAddress serverAddress);
		void onNetworkProbeFailed();
	}

	public static final String TAG = NetworkPingBroadcaster.class.getSimpleName();

	// if a pong message is received
	// it will stop spamming
	private boolean mRunning = true;

	private WeakReference<NetworkProbeListener> mWeakListener;
	private InetAddress mServerAddress;

	public NetworkPingBroadcaster(NetworkProbeListener listener) {
		mWeakListener = new WeakReference<>(listener);
		execute();
	}

	@Override
	protected Void doInBackground(Void... voids) {

		NetworkPongReceiver networkPongReceiver = null;

		try {
			Thread.sleep(500);
			InetAddress localAddress = getLocalAddress();
			publishProgress(localAddress.getHostName() + "    " + Arrays.toString(localAddress.getAddress()));

			DatagramSocket socket = new DatagramSocket();

			// listen for a pong message
			// lambdas are so cool <_>
			networkPongReceiver = new NetworkPongReceiver(socket, (InetAddress address) -> {
				mServerAddress = address; // this will be used in onPostExecute
				mRunning = false;
			});
			networkPongReceiver.start();


			byte[] intBytes = new byte[Integer.SIZE / 8];
			DatagramPacket intPacket = new DatagramPacket(intBytes, intBytes.length, localAddress, 8383);
			ByteBuffer.wrap(intBytes).asIntBuffer().put(Constants.MSG_PING);

			List<InetAddress> addresses = getTargetAddresses(localAddress);

			// spam all the list 2 times and update the listener
			for (int i = 0, j = 0; i < 2; i++, j = 0) {
				publishProgress(String.format("broadcast round %s", i + 1));

				for (InetAddress address : addresses) {
					intPacket.setAddress(address);
					socket.send(intPacket);
					Thread.sleep(1); // the update looks weird without it
					publishProgress(j++ / (float) addresses.size());
					if(!mRunning)
						throw new InterruptedException();
				}
			}

		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if(networkPongReceiver != null)
			networkPongReceiver.kill();

		return null;
	}

	/**
	 * the update can be a float for percentage of
	 * completion or String for a message
	 * @param values
	 */
	@Override
	protected void onProgressUpdate(Object... values) {
		super.onProgressUpdate(values);

		NetworkProbeListener listener = mWeakListener.get();
		if(listener == null)
			return;

		Object o = values[0];
		if(o instanceof Float)
			listener.onProgressUpdate((Float) o);
		else if (o instanceof String)
			listener.onMessageUpdate((String) o);
	}

	/**
	 * if the listener is still there notify him
	 * with the outcome (the server address)
	 */
	@Override
	protected void onPostExecute(Void aVoid) {
		super.onPostExecute(aVoid);

		NetworkProbeListener listener = mWeakListener.get();
		if(listener == null)
			return;

		if(mServerAddress == null)
			listener.onNetworkProbeFailed();
		else
			listener.onServerFound(mServerAddress);
	}

	public void kill() {
		mRunning = false;
	}

	/**
	 * retrieve the first ipv4 InetAddress which is not a loopback
	 * among the available network interfaces
	 * which should be something like 192.168.0.100 and not 127.0.0.1
	 */
	public InetAddress getLocalAddress() {
		InetAddress localAddress = null;
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

			for (NetworkInterface networkInterface : Collections.list(interfaces)) {
				//log("interface <%s>", networkInterface.getDisplayName());

				for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
					//log("\taddress: %s", address.getHostAddress());
					if (address instanceof Inet4Address && !address.isLoopbackAddress())
						localAddress = address;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return localAddress;
	}

	/**
		create a list of target ip given the local address,
	 	it enumerate from 0 to 255 and create a list of ip
	 	like x.x.x.0-255 where x is the local address
	 */
	private List<InetAddress> getTargetAddresses(InetAddress localAddress) throws UnknownHostException {
		List<InetAddress> output = new ArrayList<>(256);
		byte[] b = localAddress.getAddress();

		for (int i = 0; i < 256; i++) {
			byte[] bytes = new byte[]{b[0], b[1], b[2], (byte) i};
			output.add(InetAddress.getByAddress(bytes));
		}

		return output;
	}

	private void log(String msg, Object... args) {
		Log.d(TAG, String.format(msg, args));
	}
}