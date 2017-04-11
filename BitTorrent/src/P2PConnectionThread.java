import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

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

	//ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

	private volatile boolean shutdown = false;

	//private volatile Object signalChoke, signalUnchoke;

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
				System.out.println("Message read is " +Arrays.toString(message));
				System.out.println("Message type read is " + msgType);
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
					//MessageUtil.readMessage(in, pieceIndexData, 4);
					pieceNum = MessageUtil.byteArrayToInteger(pieceIndexData);
					byteData = myBitfield[pieceNum / 8];
					if ((byteData & (1 << (7 - (pieceNum % 8)))) == 0) {
						// Send an interested message since don't have this piece.
						sendInterestedMessage();
					}
					updatePeerBitField(pieceNum);
					logger.log("Peer " + myInfo.getPeerId()
							+ " received the have message from " + peerInfo.getPeerId());
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
					//MessageUtil.readMessage(in, pieceIndexData, 4);
					in.read(pieceIndexData);
					System.out.println(Arrays.toString(pieceIndexData));
					int pieceDataLen = messageLen - 5;
					byte[] pieceData = new byte[pieceDataLen];
					//MessageUtil.readMessage(in, pieceData, pieceDataLen);
					in.read(pieceData);
					Long downloadTime = System.nanoTime() - requestTime;
					peerInfo.setDownloadRate((long)(pieceDataLen/downloadTime));
					pieceNum = MessageUtil.byteArrayToInteger(pieceIndexData);
					System.out.println("piecenum:"+ pieceNum + ",pieceData:" + pieceData + ",pieceIndexData:" + pieceIndexData);
					int stdPieceSize = Integer.parseInt(ConfigurationReader.
							getInstance().getCommonProps().get("PieceSize"));
					for (int i = 0; i < pieceDataLen; i++) {
						fileData[(pieceNum) * stdPieceSize + i] = pieceData[i];
						//To - check if fileData is getting updated here
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
						System.out.println("Sending have message to peer:" + peerId);
						sendHaveMessage(pieceNum, peerId);
					}

					nextPieceNum = getNextToBeRequestedPiece();
					System.out.println("Next pieceNum:" + nextPieceNum);

					if (nextPieceNum != -1 ) {
						System.out.println("Inside send request loop");
						sendRequestMessage(nextPieceNum);
					}

					byte[] empty = new byte[myInfo.bitfield.length];
					
//					String fullBitField = Arrays.toString(empty);
//					fullBitField = fullBitField.replaceAll("0", "1");
					System.out.println("My bitfield: " + Arrays.toString(myInfo.getBitfield()));
					System.out.println("Peer bitfield: " + Arrays.toString(peerInfo.getBitfield()));
					if(nextPieceNum == -1 && !(Arrays.equals(myInfo.getBitfield(), peerInfo.getBitfield())))
					{
						sendInterestedMessage();
					}
					else if(nextPieceNum == -1 && Arrays.equals(myInfo.getBitfield(), PeerConfig.fullBitfield))
					{
						System.out.println("Inside file writing if loop");
						File file = new File(ConfigurationReader.getInstance().getCommonProps()
								.get("FileName"));
						try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
							fileOutputStream.write(fileData);
							fileOutputStream.close();
						} catch (IOException e) {
							e.printStackTrace();
						}

					}
					else if(nextPieceNum == -1){
						sendNotInterestedMessage();
					}

//					if(nextPieceNum == -1 && !(Arrays.equals(myInfo.getBitfield(), peerInfo.getBitfield())))
//					{
//						sendInterestedMessage();
//					}
//					else if(nextPieceNum == -1 && Arrays.toString(myInfo.getBitfield()).equals(fullBitField))
//					{
//						File file = new File(ConfigurationReader.getInstance().getCommonProps()
//								.get("FileName"));
//						try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
//							fileOutputStream.write(fileData);
//						} catch (IOException e) {
//							e.printStackTrace();
//						}
//
//					}
//					else if(nextPieceNum == -1){
//						sendNotInterestedMessage();
//					}


					break;
				case REQUEST:
					System.out.println("Received request message from peer id "
							+ peerInfo.getPeerId());
					byte[] data = new byte[4];
					in.read(data);
					pieceNum = MessageUtil.byteArrayToInteger(data);
					if (!peerInfo.getIsChoked()) {
						System.out.println("Requested piece message of piece number " + pieceNum);
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

			} catch (IOException e) {
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
			System.out.println("Message sent");
		} catch (IOException e) {
			System.out.println("Send bitfield message failed!");
			e.printStackTrace();
		}
	}

	private synchronized void readNeighbourBitfieldMessage() {
		try {
			byte[] message = new byte[myInfo.bitfield.length + 5];
			in.read(message);
			System.out.println("bitfield rcvd:" + Arrays.toString(message));
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
		int bitIndex = pieceNum % 8;
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
				out.write(message);
				out.flush();
				requestTime = System.nanoTime();
			} catch (IOException e) {
				System.out.println("io exception in reading " + e.getMessage());
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

		// special case
		// if pieceSize is greater than the entire file left

		byte[] data = new byte[1 + 4 + endIndex - startIndex + 1]; // 4 is for
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
		for (; startIndex <= endIndex; i++) {
			data[i] = fileData[startIndex++];
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

	private void resetPieceIndexRequested(int byteIndex, int bitIndex) {
		byte[] bitFieldRequested = myInfo.getAllRequestedBits();
		bitFieldRequested[byteIndex] &= ~(1 << (7 - bitIndex));
		myInfo.setAllRequestedBits(bitFieldRequested);
	}

	private int getNextToBeRequestedPiece() {
		
		System.out.println("start of get next piece");
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
		if (needToRequestPieces.isEmpty()) //Arrays.equals(needToRequest, zerosByteArray))
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
		
		System.out.println("end of get next piece");
		
		return nextPiece;

	}


	private boolean isInterested() {

		byte[] myBitfield = myInfo.getBitfield();
		byte[] peerBitfield = peerInfo.getBitfield();

		System.out.println("peerbitfield:" + peerBitfield.length);
		System.out.println("bitfield:" + myBitfield.length);

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

	/*final Runnable sendChoke = new Runnable() {

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

	};*/

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

		//scheduler.shutdown();

	}

}

