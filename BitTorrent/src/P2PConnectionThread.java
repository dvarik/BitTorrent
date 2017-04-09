import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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

	ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

	private volatile boolean shutdown = false;

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
		this.logger = LoggerUtility.getInstance(peerInfo.peerId);

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

		scheduler.execute(sendChoke);
		scheduler.execute(sendUnchoke);
		
		while(!shutdown){
			 
			byte[] message = new byte[5];
            
			try {
				MessageUtil.readMessage(in, message, 5);
				byte typeVal = message[4];
	            MessageType msgType = MessageType.getType(typeVal);
	            
	            switch(msgType) {
	            
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

	public void updatePeerBitFieldMsg(int pieceIndex) {
		int byteIndex = 7 - (pieceIndex % 8);
		byte[] peerBitfield = peerInfo.getBitfield();
		peerBitfield[pieceIndex / 8] |= (1 << byteIndex);
		peerInfo.setBitfield(peerBitfield);
	}

	public void sendHaveMsg(int pieceIndex) {
		byte[] message = MessageUtil.getMessage(MessageUtil.integerToByteArray(pieceIndex), MessageType.HAVE);
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
