package com.example.h264FFmpegStreamer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import android.util.Log;

import com.android.myffmpegx264lib.exampleEncoder;

public class X264Encoder {

	// For logging
	private static final String TAG = "com.example.h264FFmpegStreamer.AvcEncoder";

	// Networking variables
	private int DATAGRAM_PORT = 4002;
	private int TCP_SERVER_PORT = DATAGRAM_PORT + 1;
	private static final int MAX_UDP_DATAGRAM_LEN = 1400;
	private InetAddress clientIp;
	private int clientPort;
	private static boolean isClientConnected = false;

	// FIFO queue
	private static final int MAX_BUFFER_QUEUE_SIZE = 1000000;
	private BufferQueue nwkQueue = new BufferQueue(MAX_BUFFER_QUEUE_SIZE);
	private byte[] sendData = new byte[MAX_UDP_DATAGRAM_LEN];
	private byte[] outBytes = new byte[MAX_BUFFER_QUEUE_SIZE];
	private int[] outFrameSize = new int[1];

	// Encoder
	private ArrayList<exampleEncoder> encoderInstances = new ArrayList<exampleEncoder>();
	private int noOfEncoderInstances;

	// constructor
	public X264Encoder() {
		Thread udpThread = new Thread() {

			private DatagramPacket datagramPacket;
			private DatagramSocket datagramSocket;

			@Override
			public void run() {
				try {
					datagramSocket = new DatagramSocket(DATAGRAM_PORT);
					datagramPacket = new DatagramPacket(sendData,
							sendData.length);
					datagramSocket.receive(datagramPacket);
					clientPort = datagramPacket.getPort();
					clientIp = datagramPacket.getAddress();
					datagramSocket.connect(datagramPacket.getAddress(),
							datagramPacket.getPort());
					Log.i(TAG, " Connected to: " + clientIp + ":" + clientPort);
					isClientConnected = true;

					while (isClientConnected) {

						if (MainStreamerActivity.getPreviewStatus()) {

							if (nwkQueue.getCount() > MAX_UDP_DATAGRAM_LEN) {
								nwkQueue.read(sendData, 0, MAX_UDP_DATAGRAM_LEN);
								DatagramPacket packet = new DatagramPacket(
										sendData, sendData.length, clientIp,
										clientPort);
								datagramSocket.send(packet);
							}
						}
					}
					datagramSocket.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};

		udpThread.start();

		Thread tcpThread = new Thread() {

			private ServerSocket acceptSocket;

			@Override
			public void run() {
				try {
					acceptSocket = new ServerSocket(TCP_SERVER_PORT);
					Socket connectionSocket = acceptSocket.accept();
					BufferedReader inFromClient = new BufferedReader(
							new InputStreamReader(
									connectionSocket.getInputStream()));
					DataOutputStream outToClient = new DataOutputStream(
							connectionSocket.getOutputStream());
					String clientSentence = inFromClient.readLine();
					Log.i(TAG, "Received: " + clientSentence);
					isClientConnected = true;

					while (nwkQueue.read(sendData, 0, MAX_UDP_DATAGRAM_LEN) != 1) {
						outToClient.write(sendData, 0, sendData.length);
					}
					connectionSocket.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};

		tcpThread.start();
	}

	// creating multiple encoding instances
	public void createEncoderInstances(int noOfEncoderInstances) {

		for (int i = 0; i < noOfEncoderInstances; i++) {
			exampleEncoder encoder = new exampleEncoder();
			encoderInstances.add(encoder);
		}
	}

	// initializer
	public void initFFmpegEncoder(int width, int height, int fps, int bitrate,
			int maxBFrames, int gopSize) {

		for (int i = 0; i < noOfEncoderInstances; i++) {

			encoderInstances.get(i).setBitrate(bitrate);
			encoderInstances.get(i).setFps(fps);
			encoderInstances.get(i).setGopSize(gopSize);
			encoderInstances.get(i).setHeight(height);
			encoderInstances.get(i).setWidth(width);
			encoderInstances.get(i).setMaxBframes(maxBFrames);
			encoderInstances.get(i).initialize();
		}
	}

	// called from Camera.setPreviewCallbackWithBuffer(...) in other class
	public void encodeFrame(byte[] inBytes, int counter) {

		if (isClientConnected) {

			for (int i = 0; i < getNoOfEncoderInstances(); i++) {
				if (counter % getNoOfEncoderInstances() == i) {
					encoderInstances.get(i).video_encode(inBytes,
							inBytes.length, counter, outBytes, outFrameSize);
					nwkQueue.append(outBytes, 0, outFrameSize[0]);
					break;
				}
			}
		}
	}

	public void close() {
		try {
			for (int i = 0; i < getNoOfEncoderInstances(); i++) {
				encoderInstances.get(i).close();
				encoderInstances.get(i).delete();
			}
			isClientConnected = false;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int getNoOfEncoderInstances() {
		return noOfEncoderInstances;
	}

	public void setNoOfEncoderInstances(int noOfEncoderInstances) {
		this.noOfEncoderInstances = noOfEncoderInstances;
	}

}