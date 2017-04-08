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
import java.util.concurrent.TimeUnit;

/**
 * @author dvarik
 *
 */
public class P2PConnectionThread extends Thread {

	private final PeerConfig myInfo;

	private PeerConfig peerInfo;

	private final boolean isClientConnection;

	private final Socket socket;

	private OutputStream out;

	private InputStream in;

	private final LoggerUtility logger;

	private volatile Object signalChoke;

	private volatile Object signalUnchoke;

	public P2PConnectionThread(PeerConfig myPeerI, PeerConfig neighbourPeerI, Socket s, boolean isClient)
			throws Exception {
		super();

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
		this.peerInfo = neighbourPeerI;
		if (this.isClientConnection) {

			sendHandshakeMsg();
			if (receiveHandshakeMsg() != this.peerInfo.peerId)
				throw new Exception("Error in handshake!");

		} else {
			int neighId = receiveHandshakeMsg();
			if (neighId != -1) {
				this.peerInfo = ConfigurationReader.getInstance().getPeerInfo().get(neighId);
				sendHandshakeMsg();
			} else
				throw new Exception("Error in handshake!");

		}
		this.logger = new LoggerUtility(peerInfo.peerId);

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

	public void sendHandshakeMsg() {

		byte[] messageHeader = MessageUtil.getMessageHeader(myInfo.getPeerId());

		try {
			out.write(messageHeader);
			out.flush();
		} catch (IOException e) {
			logger.log("Send handshake failed !! " + e.getMessage());
			e.printStackTrace();
		}

	}

	public int receiveHandshakeMsg() {

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

		// send receive messages

		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

		scheduler.execute(sendChoke);
		scheduler.execute(sendUnchoke);

	}

	public void sendBitFieldMessage() {
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

	public void readNeighbourBitfieldMessage() {
		try {
			byte[] bitfield = null;
			byte[] msgLenArr = new byte[4];
			int msgLen = in.read(msgLenArr);
			if (msgLen != 4) {
				System.out.println("Message length is incorrect!");
			}
			int tempDataLength = MessageUtil.byteArrayToInt(msgLenArr);
			// read msg type
			byte[] msgType = new byte[1];
			in.read(msgType);
			if (msgType[0] == MessageType.BITFIELD.value) {
				int dataLength = tempDataLength - 1;
				bitfield = new byte[dataLength];
				bitfield = MessageUtil.readBytes(in, bitfield, dataLength);
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

	public void updatePeerBitFieldMsg(int pieceIndex) {
		int byteIndex = 7 - (pieceIndex % 8);
		byte[] peerBitfield = peerInfo.getBitfield();
		peerBitfield[pieceIndex / 8] |= (1 << byteIndex);
		peerInfo.setBitfield(peerBitfield);
	}

	public void sendHaveMsg(int pieceIndex) {
		byte[] message = MessageUtil.getMessage(MessageUtil.intToByteArray(pieceIndex), MessageType.HAVE);
		try {
			out.write(message);
			out.flush();

		} catch (IOException e) {
			System.out.println("Send have message failed! " + e.getMessage());
			e.printStackTrace();
		}
	}

	public void isInterested() {

	}

	public void sendInterestedMessage() {

		byte[] message = MessageUtil.getMessage(MessageType.INTERESTED);

		try {
			out.write(message);
			out.flush();
		} catch (IOException e) {
			logger.log("Send interested failed! " + e.getMessage());
			e.printStackTrace();
		}

	}

	public void sendNotInterestedMessage() {

		byte[] message = MessageUtil.getMessage(MessageType.NOT_INTERESTED);

		try {
			out.write(message);
			out.flush();
		} catch (IOException e) {
			logger.log("Send not interested failed !! " + e.getMessage());
			e.printStackTrace();
		}

	}

	public void sendChokeMessage() {

		byte[] message = MessageUtil.getMessage(MessageType.CHOKE);

		try {
			out.write(message);
			out.flush();
		} catch (IOException e) {
			logger.log("Send choke failed !! " + e.getMessage());
			e.printStackTrace();
		}

	}

	public void sendUnchokeMessage() {

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
					sendChokeMessage();
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
					sendUnchokeMessage();
				}
			}
		}

	};

}
