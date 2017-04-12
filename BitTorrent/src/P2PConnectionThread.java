import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * @author dvarik
 *
 */

public class P2PConnectionThread extends Thread {

	private PeerConfig myInfo;

	private PeerConfig peerInfo;

	private final boolean isClientConnection;

	private final Socket socket;

	private OutputStream out;

	private InputStream in;

	private final LoggerUtility logger;

	private volatile boolean shutdown = false;

	private int requestedPieceNum;

	private long requestTime;

	public P2PConnectionThread(PeerConfig myPeerI, PeerConfig neighbourPeerI,
			Socket s, boolean isClient) throws Exception {
		super();

		this.isClientConnection = isClient;
		this.socket = s;
		this.requestedPieceNum = -1;

		try {
			out = new BufferedOutputStream(socket.getOutputStream());
			in = new BufferedInputStream(socket.getInputStream());
		} catch (IOException e) {
			System.out.println("socket exception!!!");
			e.printStackTrace();
		}

		this.myInfo = myPeerI;

		if (this.isClientConnection) {

			this.peerInfo = neighbourPeerI;
			sendHandshakeMessage();
			if (receiveHandshakeMessage() != this.peerInfo.peerId)
				throw new Exception("Error in handshake!");

		} else {
			int neighId = receiveHandshakeMessage();
			if (neighId != -1) {
				this.peerInfo = ConfigurationReader.getInstance().getPeerInfo()
						.get(neighId);
				sendHandshakeMessage();
			} else
				throw new Exception("Error in handshake!");

		}
		TorrentManager.messageStreams.put(peerInfo.peerId, out);
		this.logger = LoggerUtility.getInstance(myInfo.peerId);

	}

	public PeerConfig getPeerInfo() {
		return this.peerInfo;
	}

	private synchronized void sendHandshakeMessage() {

		byte[] messageHeader = MessageUtil.getMessageHeader(myInfo.getPeerId());

		try {
			out.write(messageHeader);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private synchronized int receiveHandshakeMessage() {

		try {
			byte[] b = new byte[32];

			in.read(b);
			byte[] sub = Arrays.copyOfRange(b, 28, 32);
			Integer peerId = MessageUtil.byteArrayToInteger(sub);
			System.out.println("The peer id is " + peerId);
			return peerId;

		} catch (IOException e) {
			e.printStackTrace();
		}

		return -1;

	}

	@Override
	public void run() {

		sendBitfieldMessage();
		readNeighbourBitfieldMessage();

		if (isInterested()) {
			System.out.println("Sending interested message to peer");
			sendInterestedMessage();
		} else {
			System.out.println("Sending not interested message to peer");
			sendNotInterestedMessage();
		}

		while (!shutdown) {

			try {
				byte[] message = new byte[5];
				in.read(message);
				byte typeVal = message[4];
				int msgLen = MessageUtil.byteArrayToInteger(Arrays.copyOf(
						message, 4));
				if (msgLen == 0) {
					System.out.println("Socket closed, shutdown inititated.");
					shutDownCleanly();
					break;
				}
				MessageType msgType = MessageType.getType(typeVal);
				// byte[] myBitfield = myInfo.getBitfield();
				byte[] pieceIndexData;
				byte byteData;
				int pieceNum;
				int nextPieceNum;
				switch (msgType) {

				case BITFIELD:
					break;
				case HAVE:

					pieceIndexData = new byte[4];
					in.read(pieceIndexData);
					pieceNum = MessageUtil.byteArrayToInteger(pieceIndexData);
					synchronized (TorrentManager.mybitField) {
						byteData = TorrentManager.mybitField[pieceNum / 8];
					}
					if ((byteData & (1 << (7 - (pieceNum % 8)))) == 0) {
						sendInterestedMessage();
					}
					updatePeerBitField(pieceNum);
					logger.log("Peer " + myInfo.getPeerId()
							+ " received the have message from "
							+ peerInfo.getPeerId() + " for the piece "
							+ pieceNum);
					break;
				case INTERESTED:

					if (!TorrentManager.peersInterestedInMe
							.containsKey(peerInfo.getPeerId())) {
						TorrentManager.peersInterestedInMe.put(
								peerInfo.getPeerId(), peerInfo);
					}
					logger.log("Peer " + myInfo.getPeerId()
							+ " received the interested message from "
							+ peerInfo.getPeerId());

					break;
				case NOT_INTERESTED:

					TorrentManager.peersInterestedInMe.remove(peerInfo
							.getPeerId());
					logger.log("Peer " + myInfo.getPeerId()
							+ " received the not interested message from "
							+ peerInfo.getPeerId());
					break;
				case PIECE:
					byte[] lenDataArray = new byte[4];
					for (int i = 0; i < 4; i++) {
						lenDataArray[i] = message[i];
					}
					int messageLen = MessageUtil
							.byteArrayToInteger(lenDataArray);
					pieceIndexData = new byte[4];
					in.read(pieceIndexData);
					pieceNum = MessageUtil.byteArrayToInteger(pieceIndexData);
					int pieceDataLen = messageLen - 5;
					byte[] pieceData = new byte[pieceDataLen];
					in.read(pieceData);
					byte[] temp = new byte[pieceDataLen];
					Arrays.fill(temp, (byte) 0);
					if (Arrays.equals(pieceData, temp)) {
						resetPieceIndexRequested(pieceNum / 8, pieceNum % 8);
						break;
					}

					Long downloadTime = System.nanoTime() - requestTime;
					peerInfo.setDownloadRate((long) (pieceDataLen / downloadTime));

					int stdPieceSize = Integer.parseInt(ConfigurationReader
							.getInstance().getCommonProps().get("PieceSize"));
					synchronized (TorrentManager.fileData) {
						for (int i = 0; i < pieceDataLen; i++) {
							TorrentManager.fileData[(pieceNum) * stdPieceSize
									+ i] = pieceData[i];
						}
					}
					logger.log("Peer " + myInfo.getPeerId()
							+ " has downloaded the piece " + pieceNum
							+ " from " + peerInfo.getPeerId());
					// send have message to rest of the peers
					setMyBitfield(pieceNum);

					// saving partial files
					File dir = getFileDir();
					File file = new File(dir.getPath() + File.separator
							+ "PartFile_" + Integer.toString(pieceNum + 1)
							+ ".part");
					try (FileOutputStream fileOutputStream = new FileOutputStream(
							file)) {
						fileOutputStream.write(pieceData);
						fileOutputStream.close();

					} catch (IOException e) {
						e.printStackTrace();
					}

					// sending have messages to all the peers
					HashMap<Integer, PeerConfig> peerList = ConfigurationReader
							.getInstance().getPeerInfo();
					peerList.remove(myInfo.peerId);

					nextPieceNum = getNextToBeRequestedPiece();

					if (nextPieceNum == -1
							&& Arrays.equals(myInfo.getBitfield(),
									PeerConfig.fullBitfield)) {
						// delete all partial files
						File folder = new File(dir.getPath());
						for (File f : folder.listFiles()) {
							if (f.getName().startsWith("PartFile_")) {
								f.delete();
							}
						}

						// write the complete file
						System.out.println("Writing complete file.");
						dir = getFileDir();
						file = new File(dir.getPath()
								+ File.separator
								+ ConfigurationReader.getInstance()
										.getCommonProps().get("FileName"));
						synchronized (TorrentManager.fileData) {

							try (FileOutputStream fileOutputStream = new FileOutputStream(
									file)) {
								fileOutputStream.write(TorrentManager.fileData);
								fileOutputStream.close();
								logger.log("Peer " + myInfo.getPeerId()
										+ " has downloaded the complete file");
							} catch (IOException e) {
								e.printStackTrace();
							}
						}

					}

					for (Integer peerId : peerList.keySet()) {
						sendHaveMessage(pieceNum, peerId);
					}

					if (nextPieceNum != -1) {
						sendRequestMessage(nextPieceNum);
					}

					/*
					 * if(nextPieceNum == -1 &&
					 * !(Arrays.equals(myInfo.getBitfield(),
					 * peerInfo.getBitfield()))) { sendInterestedMessage(); }
					 */

					if (nextPieceNum == -1
							&& Arrays.equals(myInfo.getBitfield(),
									PeerConfig.fullBitfield)) {
						sendNotInterestedMessage();
					}

					break;
				case REQUEST:
					byte[] data = new byte[4];
					in.read(data);
					pieceNum = MessageUtil.byteArrayToInteger(data);
					if (!peerInfo.getIsChoked()) {
						sendPieceMessage(pieceNum);
					}
					break;
				case CHOKE:
					synchronized (TorrentManager.mybitField) {
						byteData = TorrentManager.mybitField[requestedPieceNum / 8];
					}
					if ((byteData & (1 << (7 - (requestedPieceNum % 8)))) == 0) {
						// I don't have this piece
						resetPieceIndexRequested(requestedPieceNum / 8,
								requestedPieceNum % 8);
					}

					// TorrentManager.chokedList.add(peerInfo);
					logger.log("Peer " + myInfo.getPeerId() + " is choked by "
							+ peerInfo.getPeerId());
					break;
				case UNCHOKE:
					logger.log("Peer " + myInfo.getPeerId()
							+ " is unchoked by " + peerInfo.getPeerId());
					nextPieceNum = getNextToBeRequestedPiece();
					if (nextPieceNum != -1) {
						sendRequestMessage(nextPieceNum);
					}

					if (nextPieceNum == -1
							&& !(Arrays.equals(myInfo.getBitfield(),
									peerInfo.getBitfield()))) {
						sendInterestedMessage();
					}

					else if (nextPieceNum == -1) {
						sendNotInterestedMessage();
					}
					break;

				default:
					break;

				}

			} catch (SocketException s) {
				s.printStackTrace();
				shutDownCleanly();
				this.interrupt();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private File getFileDir() {
		File dir = new File("peer_" + myInfo.peerId);
		if (!dir.exists()) {
			try {
				dir.mkdir();

			} catch (SecurityException se) {
				System.out.println(se);
			}
		}
		return dir;
	}

	private synchronized void sendBitfieldMessage() {
		try {
			byte[] myBitfield = TorrentManager.mybitField;
			byte[] message = MessageUtil.getMessage(myBitfield,
					MessageType.BITFIELD);
			out.write(message);
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private synchronized void readNeighbourBitfieldMessage() {
		try {
			byte[] message = new byte[myInfo.bitfield.length + 5];
			in.read(message);
			// read msg type
			if (message[4] == MessageType.BITFIELD.value) {
				int tempDataLength = MessageUtil.byteArrayToInteger(Arrays
						.copyOf(message, 4));
				int dataLength = tempDataLength - 1;
				byte[] bitfield = new byte[dataLength];
				bitfield = Arrays.copyOfRange(message, 5, message.length);
				peerInfo.setBitfield(bitfield);
			} else {
				System.out.println("Wrong message type sent");
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private synchronized void setMyBitfield(int pieceNum) {

		int pieceIndex = pieceNum / 8;
		int bitIndex = (pieceNum) % 8;
		TorrentManager.mybitField[pieceIndex] |= 1 << (7 - bitIndex);

	}

	private void updatePeerBitField(int pieceNum) {

		int bitIndex = pieceNum % 8;
		byte[] peerBitfield = peerInfo.getBitfield();
		peerBitfield[pieceNum / 8] |= (1 << (7 - bitIndex));
		peerInfo.setBitfield(peerBitfield);
	}

	private void sendHaveMessage(int pieceNum, int peerId) {
		byte[] message = MessageUtil.getMessage(
				MessageUtil.integerToByteArray(pieceNum), MessageType.HAVE);
		if (!TorrentManager.messageStreams.contains(peerId))
			return;
		OutputStream o = TorrentManager.messageStreams.get(peerId);
		try {
			synchronized (o) {
				o.write(message);
				o.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendRequestMessage(int pieceNum) {

		if (pieceNum >= 0) {
			byte[] pieceNumByteArray = MessageUtil.integerToByteArray(pieceNum);

			byte[] message = MessageUtil.getMessage(pieceNumByteArray,
					MessageType.REQUEST);
			try {
				synchronized (out) {
					out.write(message);
					out.flush();
				}
				requestTime = System.nanoTime();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private synchronized void sendPieceMessage(int pieceNum) {

		int pieceSize = Integer.parseInt(ConfigurationReader.getInstance()
				.getCommonProps().get("PieceSize"));
		int startIndex = pieceSize * (pieceNum);
		int endIndex = startIndex + pieceSize - 1;
		if (endIndex >= TorrentManager.fileData.length) {
			endIndex = TorrentManager.fileData.length - 1;
		}

		int dataLen = 1 + 4 + endIndex - startIndex + 1;

		byte[] data = new byte[dataLen];
		byte[] pieceNumByteArray = MessageUtil.integerToByteArray(pieceNum);
		int i = 0;

		data[i] = (byte) MessageType.PIECE.getValue();
		i++;

		for (; i < 5; i++) {
			data[i] = pieceNumByteArray[i - 1];
		}

		for (; startIndex <= endIndex; i++) {
			data[i] = TorrentManager.fileData[startIndex++];
		}

		byte[] message = new byte[dataLen + 4];
		message = MessageUtil.integerToByteArray(dataLen);
		byte[] finalMessage = MessageUtil.concat(message, data);
		try {
			synchronized (out) {
				out.write(finalMessage);
				out.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private synchronized void resetPieceIndexRequested(int byteIndex,
			int bitIndex) {
		TorrentManager.allRequestedBits[byteIndex] &= ~(1 << (7 - bitIndex));
	}

	private synchronized int getNextToBeRequestedPiece() {

		byte[] myBitfield = TorrentManager.mybitField;
		byte[] myPeerBitfield = peerInfo.getBitfield();
		byte[] needToRequest = new byte[TorrentManager.allRequestedBits.length];
		byte[] requestedAndHave = new byte[TorrentManager.allRequestedBits.length];

		for (int i = 0; i < TorrentManager.allRequestedBits.length; i++) {
			requestedAndHave[i] = (byte) (myBitfield[i] | TorrentManager.allRequestedBits[i]);
			needToRequest[i] = (byte) ((requestedAndHave[i] ^ myPeerBitfield[i]) & ~requestedAndHave[i]);
		}

		ArrayList<Integer> needToRequestPieces = myInfo.needToRequestPieces;
		if (needToRequestPieces.isEmpty()) {
			requestedPieceNum = -1;
			return requestedPieceNum;
		}

		int nextPiece = needToRequestPieces.get(0);
		int byteIndex = nextPiece / 8;
		int bitIndex = nextPiece % 8;

		requestedPieceNum = nextPiece;
		TorrentManager.allRequestedBits[byteIndex] |= (1 << (7 - bitIndex));
		needToRequestPieces.remove(0);

		return nextPiece;

	}

	private synchronized boolean isInterested() {

		byte[] myBitfield = TorrentManager.mybitField;
		byte[] peerBitfield = peerInfo.getBitfield();

		for (int i = 0; i < myBitfield.length; i++) {
			byte temp = (byte) (myBitfield[i] ^ peerBitfield[i]);
			byte result = (byte) (temp & ~myBitfield[i]);
			if (result != 0)
				return true;
		}
		return false;

	}

	private void sendInterestedMessage() {

		byte[] message = MessageUtil.getMessage(MessageType.INTERESTED);

		try {
			synchronized (out) {
				out.write(message);
				out.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void sendNotInterestedMessage() {

		byte[] message = MessageUtil.getMessage(MessageType.NOT_INTERESTED);

		try {
			synchronized (out) {
				out.write(message);
				out.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void shutDownCleanly() {

		shutdown = true;

		try {
			if (!socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
