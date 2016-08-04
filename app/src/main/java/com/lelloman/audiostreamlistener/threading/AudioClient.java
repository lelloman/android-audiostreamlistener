package com.lelloman.audiostreamlistener.threading;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import static com.lelloman.audiostreamlistener.Constants.DEFAULT_SKIP_FRAME;
import static com.lelloman.audiostreamlistener.Constants.MSG_START_STREAM;
import static com.lelloman.audiostreamlistener.Constants.MSG_STREAM_INFO;
import static com.lelloman.audiostreamlistener.Constants.SO_TIMEOUT;
import static com.lelloman.audiostreamlistener.Constants.MAX_SO_TIMEOUT_COUNT;

/**
	Request a {@link StreamConfig} to the given address and
 	on success it starts listening on a socket and write all
 	the packets to an {@link AudioTrack}
 */
public class AudioClient extends Thread {

	public interface AudioClientListener {
		void onConnectionClosed();
		void onStreamConfigReceived(StreamConfig streamConfig);
		void onStatsUpdate(long byteCount, long durationMs);
	}

	public static final String TAG = AudioClient.class.getSimpleName();

	// this is a wild guess
	public static final int STREAM_TYPE = AudioManager.STREAM_VOICE_CALL;

	// actually play the PCM stream
	private AudioTrack mAudioTrack;
	// used to communicate with the server
	private DatagramSocket mSocket;
	// audio stream configuration values
	private StreamConfig mStreamConfig;

	// target server info
	private final String mAddress;
	private InetAddress mInetAddress;
	private final int mPort;

	// holds data for the audio frame packet
	private byte[] mPcmFrame;
	// prevent over buffering the AudioTrack
	private int mSkipFrameEveryTot;
	// number of byte received
	private long byteCount;

	private final AudioClientListener mListener;

	public AudioClient(String address, int port, AudioClientListener listener) {
		super();
		mListener = listener;
		mAddress = address;
		mPort = port;
		mSkipFrameEveryTot = DEFAULT_SKIP_FRAME;
	}

	public void kill() {
		if (mSocket != null) {
			try {
				mSocket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * the main thing
	 */
	@Override
	public void run() {
		try {
			log("attempting connection to %s:%s",mAddress,mPort);
			// do this here otherwise the constructor throws an error
			mInetAddress = InetAddress.getByName(mAddress);

			// send a request to the server and try to get
			// the audio stream configuration
			mStreamConfig = getStreamConfig();
			if(mStreamConfig == null) {
				mListener.onConnectionClosed();
				return;
			}
			mListener.onStreamConfigReceived(mStreamConfig);

			mSocket = new DatagramSocket();
			mSocket.setSoTimeout(SO_TIMEOUT);
			mSocket.setReceiveBufferSize(mStreamConfig.bufferSize);

			// the server should be streaming by now but who knows
			sendRequestStartStream();

			initAudioTrack();
			DatagramPacket framePacket = new DatagramPacket(mPcmFrame, mPcmFrame.length);

			// this is use to skip a frame every tot
			int i = 0;

			// to send regular updates with stat
			// to the listener
			int byteCountCursor = 0;

			// tolerate errors every now and then
			int errorCount = 0;
			long lastErrorTime = 0;
			long startTime = System.currentTimeMillis();

			// if the connection times out too may times in
			// a short period of time consider it closed
			while (errorCount < MAX_SO_TIMEOUT_COUNT) {
				try {
					// listen for incoming packets and
					// play them right away
					mSocket.receive(framePacket);
					if( i++ > mSkipFrameEveryTot){
						i = 0;
					}else {
						mAudioTrack.setPlaybackHeadPosition(0); // might be pointless
						mAudioTrack.write(ByteBuffer.wrap(mPcmFrame), mPcmFrame.length, AudioTrack.WRITE_BLOCKING);
					}

					// update stats
					byteCount += mPcmFrame.length;
					if(++byteCountCursor >= 100){
						mListener.onStatsUpdate(byteCount, System.currentTimeMillis() - startTime);
						byteCountCursor = 0;
					}

				}catch (SocketTimeoutException e){
					long now = System.currentTimeMillis();
					if(now - lastErrorTime > 1000){
						errorCount = 1;
						log("error reset");
					}else {
						errorCount++;
					}
					lastErrorTime = now;
					log("mSocket timeout n %s", errorCount);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			try {
				mSocket.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		mListener.onConnectionClosed();
	}

	/**
	 * 	initialize {@link AudioTrack} with the parameters
	 * 	of the {@link StreamConfig} received by the server
	 */
	private void initAudioTrack(){
		log("initAudioTrack()");

		int bitDepth = mStreamConfig.bitDepth == 1 ? AudioFormat.ENCODING_PCM_8BIT : AudioFormat.ENCODING_PCM_16BIT;

		mAudioTrack = new AudioTrack(STREAM_TYPE, mStreamConfig.sampleRate,
				AudioFormat.CHANNEL_OUT_MONO,
				bitDepth, mStreamConfig.bufferSize,
				AudioTrack.MODE_STREAM);

		mAudioTrack.setVolume(AudioTrack.getMaxVolume());
		mPcmFrame = new byte[mStreamConfig.bufferSize];
		mAudioTrack.play();
	}

	/**
	 *	send a 4 bytes message to the server, 3 times
	 */
	private void sendRequestStartStream() throws IOException {
		log("sendRequestStartStream()");

		for(int i=0;i<3;i++) {
			DatagramPacket msgPacket = new DatagramPacket(new byte[Integer.SIZE / 8], Integer.SIZE / 8, mInetAddress, mPort);
			ByteBuffer.wrap(msgPacket.getData()).asIntBuffer().put(MSG_START_STREAM);
			mSocket.send(msgPacket);
		}
	}

	/**
	 * Send a 4 bytes message to the server and read 3 * 4 bytes int
	 * as response, which are then packed in a {@link StreamConfig} object.
	 */
	private StreamConfig getStreamConfig() throws Exception {
		log("getStreamConfig() ...");

		mSocket = new DatagramSocket();
		byte[] intBytes = new byte[Integer.SIZE / 8];
		DatagramPacket intPacket = new DatagramPacket(intBytes,intBytes.length, mInetAddress, mPort);

		ByteBuffer.wrap(intBytes).asIntBuffer().put(MSG_STREAM_INFO);

		mSocket.send(intPacket);
		log("getStreamConfig() request stream info message sent");

		mSocket.setSoTimeout(1000);
		StreamConfig output = null;
		try {

			int sampleRate = readInt(intPacket);
			int bufferSize = readInt(intPacket);
			int bitDepth = readInt(intPacket);

			log("getStreamConfig() sampleRate = %s bitDepth = %s bufferSize = %s", sampleRate, bitDepth, bufferSize);
			output = new StreamConfig(sampleRate, bitDepth, bufferSize);

		}catch (Exception e){
			//e.printStackTrace();
			log("getStreamConfig() something went wrong");
		}

		return output;
	}

	public void setSkipFrameEveryTot(int i){
		mSkipFrameEveryTot = i;
	}

	private int readInt(DatagramPacket intPacket) throws IOException {
		mSocket.receive(intPacket);
		return ByteBuffer.wrap(intPacket.getData()).asIntBuffer().get();
	}

	private void log(String msg, Object...args){
		Log.d(TAG, String.format(msg, args));
	}

	public static class StreamConfig {
		public final int sampleRate, bitDepth, bufferSize;

		public StreamConfig(int sampleRate, int bitDepth, int bufferSize) {
			this.sampleRate = sampleRate;
			this.bitDepth = bitDepth;
			this.bufferSize = bufferSize;
		}
	}
}
