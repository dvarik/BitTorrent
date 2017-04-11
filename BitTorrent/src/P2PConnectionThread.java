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

	private final byte[] fileData;

	private int requestedPieceNum;

	private long requestTime;
	

	public P2PConnectionThread(PeerConfig myPeerI, PeerConfig neighbourPeerI, Socket s, boolean isClient, byte[] data)
			throws Exception {
		super();

		this.fileData = data;
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

	/*public Object getChokeSignal() {
		return this.signalChoke;
	}

	public Object getUnchokeSignal() {
		return this.signalUnchoke;
	}*/

	private synchronized void sendHandshakeMessage() {

		byte[] messageHeader = MessageUtil.getMessageHeader(myInfo.getPeerId());

		try {
			out.write(messageHeader);
			out.flush();
		} catch (IOException e) {
			logger.log("Send handshake failed !! " + e.getMessage());
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

		if (isInterested())
		{
			System.out.println("Sending interested message to peer");
			sendInterestedMessage();
		}
		else
		{
			System.out.println("Sending not interested message to peer");
			sendNotInterestedMessage();
		}
/*		scheduler.execute(sendChoke);
		scheduler.execute(sendUnchoke);*/

		while(!shutdown){

			try {
				byte[] message = new byte[5];
				in.read(message);
				byte typeVal = message[4];
				MessageType msgType = MessageType.getType(typeVal);
				byte[] myBitfield = myInfo.getBitfield();
				byte[] pieceIndexData;
				byte byteData;
				int pieceNum;
				int nextPieceNum;
				switch(msgType) {

				case BITFIELD:
					break;
				case HAVE:
					System.out.println("Received have message from peer id " + peerInfo.getPeerId());
					pieceIndexData = new byte[4];
					in.read(pieceIndexData);
					pieceNum = MessageUtil.byteArrayToInteger(pieceIndexData);
					byteData = myBitfield[pieceNum / 8];
					if ((byteData & (1 << (7 - (pieceNum % 8)))) == 0) {
						sendInterestedMessage();
					}
					updatePeerBitField(pieceNum);
					logger.log("Peer " + myInfo.getPeerId()
							+ " received the have message from " + peerInfo.getPeerId()
							+ "for the piece " + pieceNum);
					break;
				case INTERESTED:
					System.out.println("Received an interested message from peer id " + peerInfo.getPeerId());
					if(!TorrentManager.peersInterestedInMe.containsKey(peerInfo.getPeerId()))
					{
						TorrentManager.peersInterestedInMe.put(peerInfo.getPeerId(), peerInfo);
					}
					logger.log("Peer " + myInfo.getPeerId()
							+ " received the interested message from "
							+ peerInfo.getPeerId());

					break;
				case NOT_INTERESTED:
					System.out.println("Received not interested message from peer id " + peerInfo.getPeerId());
					TorrentManager.peersInterestedInMe.remove(peerInfo.getPeerId());
					//peerInfo.setIsChoked(true);
					logger.log("Peer " + myInfo.getPeerId()
							+ " received the not interested message from "
							+ peerInfo.getPeerId());
					break;
				case PIECE:
					System.out.println("Received piece message from peer id "
							+ peerInfo.getPeerId());
					byte[] lenDataArray = new byte[4];
					for (int i = 0; i < 4; i++) {
						lenDataArray[i] = message[i];
					}
					int messageLen = MessageUtil.byteArrayToInteger(lenDataArray);
					pieceIndexData = new byte[4];
					in.read(pieceIndexData);
					int pieceDataLen = messageLen - 5;
					byte[] pieceData = new byte[pieceDataLen];
					in.read(pieceData);
					Long downloadTime = System.nanoTime() - requestTime;
					peerInfo.setDownloadRate((long)(pieceDataLen/downloadTime));
					pieceNum = MessageUtil.byteArrayToInteger(pieceIndexData);
					int stdPieceSize = Integer.parseInt(ConfigurationReader.
							getInstance().getCommonProps().get("PieceSize"));
					for (int i = 0; i < pieceDataLen; i++) {
						fileData[(pieceNum) * stdPieceSize + i] = pieceData[i];
					}
					logger.log("Peer " + myInfo.getPeerId()
							+ " has downloaded the piece " + pieceNum + " from "
							+ peerInfo.getPeerId());

					// send have message to rest of the peers
					setMyBitfield(pieceNum);

					HashMap<Integer, PeerConfig> peerList = ConfigurationReader.getInstance()
							.getPeerInfo();
					peerList.remove(myInfo.peerId);

					for (Integer peerId : peerList.keySet()) {
						sendHaveMessage(pieceNum, peerId);
					}

					nextPieceNum = getNextToBeRequestedPiece();

					if (nextPieceNum != -1 ) {
						sendRequestMessage(nextPieceNum);
					}

					if(nextPieceNum == -1 && !(Arrays.equals(myInfo.getBitfield(), peerInfo.getBitfield())))
					{
						sendInterestedMessage();
					}
					else if(nextPieceNum == -1 && Arrays.equals(myInfo.getBitfield(), PeerConfig.fullBitfield))
					{
						System.out.println("Writing complete file.");
						File file = new File(ConfigurationReader.getInstance().getCommonProps()
								.get("FileName"));
						try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
							fileOutputStream.write(fileData);
							fileOutputStream.close();
							logger.log("Peer " + myInfo.getPeerId() + "has downloaded "
									+ "the complete file");
						} catch (IOException e) {
							e.printStackTrace();
						}

					}
					else if(nextPieceNum == -1){
						sendNotInterestedMessage();
					}

					break;
				case REQUEST:
					System.out.println("Received request message from peer id "
							+ peerInfo.getPeerId());
					byte[] data = new byte[4];
					in.read(data);
					pieceNum = MessageUtil.byteArrayToInteger(data);
					if (!peerInfo.getIsChoked()) {
						sendPieceMessage(pieceNum);
					}
					break;
				case CHOKE:
					System.out.println("Received choke message from peer id " + peerInfo.getPeerId());

					byteData = myBitfield[requestedPieceNum / 8];

					if ((byteData & (1 << (7 - (requestedPieceNum % 8)))) == 0) {
						// I don't have this piece
						resetPieceIndexRequested(requestedPieceNum / 8,
								requestedPieceNum % 8);
					}

					//TorrentManager.chokedList.add(peerInfo);
					logger.log("Peer " + myInfo.getPeerId() + " is choked by "
							+ peerInfo.getPeerId());
					break;
				case UNCHOKE:
					System.out.println("Received unchoke message from peer id " + peerInfo.getPeerId());
					//TorrentManager.chokedList.remove(peerInfo.getPeerId());
					logger.log("Peer " + myInfo.getPeerId() + " is unchoked by "
							+ peerInfo.getPeerId());
					nextPieceNum = getNextToBeRequestedPiece();
					if (nextPieceNum != -1) {
						sendRequestMessage(nextPieceNum);
					}

					if(nextPieceNum == -1 && !(Arrays.equals(myInfo.getBitfield(), peerInfo.getBitfield())))
					{
						sendInterestedMessage();
					}

					else if(nextPieceNum == -1){
						sendNotInterestedMessage();
					}
					break;

				default:
					break;

				}

			}catch (SocketException s) {
				s.printStackTrace();
				shutDownCleanly();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private synchronized void sendBitfieldMessage() {
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

	private synchronized void readNeighbourBitfieldMessage() {
		try {
			byte[] message = new byte[myInfo.bitfield.length + 5];
			in.read(message);
			// read msg type
			if (message[4] == MessageType.BITFIELD.value) {
				int tempDataLength = MessageUtil.byteArrayToInteger(Arrays.copyOf(message, 4));
				int dataLength = tempDataLength - 1;
				byte[] bitfield = new byte[dataLength];
				bitfield = Arrays.copyOfRange(message, 5, message.length);
				peerInfo.setBitfield(bitfield);
			} else {
				System.out.println("Wrong message type sent");
			}

		} catch (IOException e) {
			System.out.println("Could not read length of actual message");
			e.printStackTrace();
		}

	}

	private void setMyBitfield(int pieceNum) {

		byte[] bitfield = myInfo.getBitfield();
		int pieceIndex = pieceNum / 8;
		int bitIndex = (pieceNum) % 8;
		bitfield[pieceIndex] |= 1 << (7 - bitIndex);
		myInfo.setBitfield(bitfield);

	}

	private void updatePeerBitField(int pieceNum) {

		int bitIndex = pieceNum % 8;
		byte[] peerBitfield = peerInfo.getBitfield();
		peerBitfield[pieceNum / 8] |= (1 << (7 - bitIndex));
		peerInfo.setBitfield(peerBitfield);
	}

	private void sendHaveMessage(int pieceNum, int peerId) {
		byte[] message = MessageUtil.getMessage(MessageUtil.integerToByteArray(pieceNum), MessageType.HAVE);
		OutputStream o = TorrentManager.messageStreams.get(peerId);
		try {
			synchronized (o) {
				o.write(message);
				o.flush();
			}
		} catch (IOException e) {
			System.out.println("Send have message failed! " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void sendRequestMessage(int pieceNum) {

		System.out.println("Sending request msg");
		if (pieceNum >= 0) {
			byte[] pieceNumByteArray = MessageUtil.integerToByteArray(pieceNum);

			byte[] message = MessageUtil.getMessage(pieceNumByteArray, MessageType.REQUEST);
			try {
				synchronized(out){
				out.write(message);
				out.flush();
				}
				requestTime = System.nanoTime();
			} catch (IOException e) {
				System.out.println("IO exception in reading request" + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void sendPieceMessage(int pieceNum) {

		int pieceSize = Integer.parseInt(ConfigurationReader.getInstance().getCommonProps().get("PieceSize"));
		int startIndex = pieceSize * (pieceNum);
		int endIndex = startIndex + pieceSize - 1;
		if (endIndex >= fileData.length) {
			endIndex = fileData.length - 1;
		}

		int dataLen = 1 + 4 + endIndex - startIndex + 1;

		byte[] data = new byte[dataLen]; 
		byte[] pieceNumByteArray = MessageUtil.integerToByteArray(pieceNum);
		int i=0;

		data[i] = (byte) MessageType.PIECE.getValue();
		i++;

		for (; i < 5; i++) {
			data[i] = pieceNumByteArray[i-1];
		}

		for (; startIndex <= endIndex; i++) {
			data[i] = fileData[startIndex++];
		}

		byte[] message = new byte[dataLen+4];
		message = MessageUtil.integerToByteArray(dataLen);
		byte[] finalMessage = MessageUtil.concat(message, data);

		try {
			synchronized(out){
			out.write(finalMessage);
			out.flush();
			}
		} catch (IOException e) {
			System.out.println("IO exception in reading " + e.getMessage());
			e.printStackTrace();
		}
	}

	private void resetPieceIndexRequested(int byteIndex, int bitIndex) {
		byte[] bitFieldRequested = myInfo.getAllRequestedBits();
		bitFieldRequested[byteIndex] &= ~(1 << (7 - bitIndex));
		myInfo.setAllRequestedBits(bitFieldRequested);
	}

	private synchronized int getNextToBeRequestedPiece() {

		byte[] allRequestedBits = myInfo.getAllRequestedBits();
		byte[] myBitfield = myInfo.getBitfield();
		byte[] myPeerBitfield = peerInfo.getBitfield();
		byte[] needToRequest = new byte[allRequestedBits.length];
		byte[] requestedAndHave = new byte[allRequestedBits.length];

		for (int i = 0; i < allRequestedBits.length; i++) {
			requestedAndHave[i] = (byte) (myBitfield[i] | allRequestedBits[i]);
			needToRequest[i] = (byte) ((requestedAndHave[i] ^ myPeerBitfield[i]) & ~requestedAndHave[i]);
		}

		ArrayList<Integer> needToRequestPieces = myInfo.needToRequestPieces;
		if (needToRequestPieces.isEmpty()) 
		{
			requestedPieceNum = -1;
			return requestedPieceNum;
		}

		int nextPiece = needToRequestPieces.get(0);
		int byteIndex = nextPiece / 8;
		int bitIndex = nextPiece % 8;


/*		Random rand = new Random();
		int randNum = rand.nextInt(needToRequestPieces.size() - 1) + 1;
		int nextPiece = needToRequestPieces.get(randNum);
		int byteIndex = nextPiece / 8;
		int bitIndex = nextPiece % 8;

		byte temp = needToRequest[byteIndex];
		//Arrays.fill(zerosByteArray, (byte) 0);

		System.out.println("Need to req array:" + Arrays.toString(needToRequest));

		while (temp == 0 || (temp & (1 << (8 - bitIndex))) == 0) {
			System.out.println("print in while "+ temp);
			randNum = rand.nextInt(needToRequestPieces.size() - 1) + 1;
			nextPiece = needToRequestPieces.get(randNum);
			byteIndex = nextPiece / 8;
			bitIndex = nextPiece % 8;
			temp = needToRequest[byteIndex];
		}
*/
		requestedPieceNum = nextPiece;
		allRequestedBits[byteIndex] |= (1 << (7-bitIndex));
		myInfo.setAllRequestedBits(allRequestedBits);
		needToRequestPieces.remove(0);

		return nextPiece;

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
			synchronized(out){
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
			synchronized(out){
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
			System.out.println("Cannot close socket for peer " + myInfo.peerId);
			e.printStackTrace();
		}

	}

}

