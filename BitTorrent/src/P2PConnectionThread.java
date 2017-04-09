import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author dvarik
 *
 */
public class P2PConnectionThread extends Thread {

	private final PeerConfig myInfo;

	private final PeerConfig peerInfo;

	private final boolean isClientConnection;

	private final Socket socket;

	private OutputStream out;

	private InputStream in;

	private final LoggerUtility logger;

	ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

	private volatile boolean shutdown = false;

	private volatile Object signalChoke, signalUnchoke;

	private final byte[] fileData;

	public P2PConnectionThread(PeerConfig myPeerI, PeerConfig neighbourPeerI, Socket s, boolean isClient, byte[] data)
			throws Exception {
		super();

		this.fileData = data;
		this.isClientConnection = isClient;
		this.socket = s;

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
				this.peerInfo = ConfigurationReader.getInstance().getPeerInfo().get(neighId);
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

	public Object getChokeSignal() {
		return this.signalChoke;
	}

	public Object getUnchokeSignal() {
		return this.signalUnchoke;
	}

	private void sendHandshakeMessage() {

		byte[] messageHeader = MessageUtil.getMessageHeader(myInfo.getPeerId());

		try {
			out.write(messageHeader);
			out.flush();
		} catch (IOException e) {
			logger.log("Send handshake failed !! " + e.getMessage());
			e.printStackTrace();
		}

	}

	private int receiveHandshakeMessage() {

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

		scheduler.execute(sendChoke);
		scheduler.execute(sendUnchoke);

		while (!shutdown) {

			byte[] message = new byte[5];

			try {
				MessageUtil.readMessage(in, message, 5);
				byte typeVal = message[4];
				MessageType msgType = MessageType.getType(typeVal);

				switch (msgType) {

				case BITFIELD:
					break;
				case HAVE:
					break;
				case INTERESTED:
					break;
				case NOT_INTERESTED:
					break;
				case PIECE:
					break;
				case REQUEST:
					break;
				case UNCHOKE:
					break;
				default:
					break;

				}

			} catch (IOException e) {
				e.printStackTrace();
			}

		}

	}

	private void sendBitfieldMessage() {
		try {
			byte[] myBitfield = myInfo.getBitfield();
			byte[] message = MessageUtil.getMessage(myBitfield, MessageType.BITFIELD);
			out.write(message);
			out.flush();
		} catch (IOException e) {
			System.out.println("Send bitfield message failed!");
			e.printStackTrace();
		}
	}

	private void readNeighbourBitfieldMessage() {
		try {
			byte[] bitfield = null;
			byte[] msgLenArr = new byte[4];
			int msgLen = in.read(msgLenArr);
			if (msgLen != 4) {
				System.out.println("Message length is incorrect!");
			}
			int tempDataLength = MessageUtil.byteArrayToInteger(msgLenArr);
			// read msg type
			byte[] msgType = new byte[1];
			in.read(msgType);
			if (msgType[0] == MessageType.BITFIELD.value) {
				int dataLength = tempDataLength - 1;
				bitfield = new byte[dataLength];
				MessageUtil.readMessage(in, bitfield, dataLength);
				peerInfo.setBitfield(bitfield);
			} else {
				System.out.println("Wrong message type sent");
			}

		} catch (IOException e) {
			System.out.println("Could not read length of actual message");
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void setMyBitfield(int pieceNum) {

		byte[] bitfield = myInfo.getBitfield();
		int pieceIndex = pieceNum / 8;
		int bitIndex = pieceNum % 8;
		bitfield[pieceIndex] |= 1 << (7 - bitIndex);
		myInfo.setBitfield(bitfield);

	}

	private void updatePeerBitField(int pieceNum) {

		int byteIndex = 7 - (pieceNum % 8);
		byte[] peerBitfield = peerInfo.getBitfield();
		peerBitfield[pieceNum / 8] |= (1 << byteIndex);
		peerInfo.setBitfield(peerBitfield);

	}

	public synchronized void sendHaveMessage(int pieceNum) {
		byte[] message = MessageUtil.getMessage(MessageUtil.integerToByteArray(pieceNum), MessageType.HAVE);
		try {
			out.write(message);
			out.flush();

		} catch (IOException e) {
			System.out.println("Send have message failed! " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void sendRequestMessage(int pieceNum) {
		if (pieceNum >= 0) {
			byte[] pieceNumByteArray = MessageUtil.integerToByteArray(pieceNum);

			byte[] message = MessageUtil.getMessage(pieceNumByteArray, MessageType.REQUEST);
			try {
				out.write(message);
				out.flush();
			} catch (IOException e) {
				System.out.println("io exception in reading " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void sendPieceMessage(int pieceNum) {

		int pieceSize = Integer.parseInt(ConfigurationReader.getInstance().getCommonProps().get("PieceSize"));
		int startIndex = pieceSize * pieceNum;
		int endIndex = startIndex + pieceSize - 1;
		if (endIndex >= fileData.length) {
			endIndex = fileData.length - 1;
		}

		// special case
		// if pieceSize is greater than the entire file left

		byte[] data = new byte[1 + 4 + endIndex - startIndex]; // 4 is for
																// pieceIndex

		// write the piece index
		byte[] pieceIndexByteArray = MessageUtil.integerToByteArray(pieceNum);
		int i;
		for (i = 0; i < 4; i++) {
			data[i] = pieceIndexByteArray[i];
		}

		// write the message type
		data[i] = (byte) MessageType.PIECE.getValue();
		i++;

		// write the piece data
		for (; i <= endIndex; i++) {
			data[i] = fileData[i];
		}

		try {
			System.out.println("Message length is: " + data.length);
			out.write(data);
			out.flush();
		} catch (IOException e) {
			System.out.println("IO exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void resetPieceIndexRequested(int index, int indexFromLeft) {
		byte[] bitFieldRequested = myInfo.getAllRequestedBits();
		bitFieldRequested[index] &= ~(1 << (7 - indexFromLeft));
		myInfo.setAllRequestedBits(bitFieldRequested);
	}

	private int getNextToBeRequestedPiece() {
		byte[] allRequestedBits = myInfo.getAllRequestedBits();
		byte[] myBitfield = myInfo.getBitfield();
		byte[] myPeerBitfield = peerInfo.getBitfield();
		byte[] needToRequest = new byte[allRequestedBits.length];

		for (int i = 0; i < allRequestedBits.length; i++) {
			byte requestedAndHave = (byte) (myBitfield[i] | allRequestedBits[i]);
			needToRequest[i] = (byte) ((requestedAndHave ^ myPeerBitfield[i]) & ~requestedAndHave);
		}

		int numPieces = Integer.parseInt(ConfigurationReader.getInstance().getCommonProps().get("numPieces"));

		Random rand = new Random();
		int randNum = rand.nextInt(numPieces - 1) + 1;
		int byteIndex = randNum / 8;
		int bitIndex = randNum % 8;

		byte temp = needToRequest[byteIndex];
		while (temp == 0 || (temp & (1 << bitIndex)) == 0) {
			randNum = rand.nextInt(numPieces - 1) + 1;
			byteIndex = randNum / 8;
			bitIndex = randNum % 8;
			temp = needToRequest[byteIndex];
		}

		allRequestedBits[byteIndex] |= (1 << bitIndex);
		myInfo.setAllRequestedBits(allRequestedBits);
		return randNum;

	}

	private boolean isInterested() {

		byte[] myBitfield = myInfo.getBitfield();
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
			out.write(message);
			out.flush();
		} catch (IOException e) {
			logger.log("Send interested failed! " + e.getMessage());
			e.printStackTrace();
		}

	}

	private void sendNotInterestedMessage() {

		byte[] message = MessageUtil.getMessage(MessageType.NOT_INTERESTED);

		try {
			out.write(message);
			out.flush();
		} catch (IOException e) {
			logger.log("Send not interested failed !! " + e.getMessage());
			e.printStackTrace();
		}

	}

	private void sendChokeMessage() {

		byte[] message = MessageUtil.getMessage(MessageType.CHOKE);

		try {
			out.write(message);
			out.flush();
		} catch (IOException e) {
			logger.log("Send choke failed !! " + e.getMessage());
			e.printStackTrace();
		}

	}

	private void sendUnchokeMessage() {

		byte[] message = MessageUtil.getMessage(MessageType.UNCHOKE);

		try {
			out.write(message);
			out.flush();
		} catch (IOException e) {
			logger.log("Send unchoke failed !! " + e.getMessage());
			e.printStackTrace();
		}

	}

	final Runnable sendChoke = new Runnable() {

		@Override
		public void run() {

			while (true) {
				try {
					signalChoke.wait();
				} catch (InterruptedException e) {
					if (!shutdown)
						sendChokeMessage();
					else
						break;
				}
			}
		}

	};

	final Runnable sendUnchoke = new Runnable() {

		@Override
		public void run() {

			while (true) {
				try {
					signalUnchoke.wait();
				} catch (InterruptedException e) {
					if (!shutdown)
						sendUnchokeMessage();
					else
						break;
				}
			}
		}

	};

	public void shutDownCleanly() {

		shutdown = true;

		try {
			if (!socket.isClosed()) {
				socket.close();
			}
		} catch (IOException e) {
			System.out.println("Cannot close socket for peer " + myInfo.peerId);
			e.printStackTrace();
		}

		scheduler.shutdown();

	}

}
