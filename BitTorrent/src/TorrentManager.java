import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author dvarik
 *
 */
public class TorrentManager extends Thread {

	final int myPeerId;

	final PeerConfig myPeerInfo;

	final LoggerUtility logger;

	final int optimisticUnchokeInterval;

	final int preferredUnchokeInterval;

	final int preferredNeighborCount;

	static byte[] mybitField;

	static byte[] fileData;

	static byte[] allRequestedBits;

	List<P2PConnectionThread> openTCPconnections = new ArrayList<P2PConnectionThread>();

	static volatile int optimisticallyUnchokedPeer = -1;

	static List<PeerConfig> unchokedList = Collections
			.synchronizedList(new ArrayList<PeerConfig>());

	static List<PeerConfig> chokedList = Collections
			.synchronizedList(new ArrayList<PeerConfig>());

	public static ConcurrentHashMap<Integer, PeerConfig> peersInterestedInMe = new ConcurrentHashMap<Integer, PeerConfig>();

	public static ConcurrentHashMap<Integer, OutputStream> messageStreams = new ConcurrentHashMap<Integer, OutputStream>();

	ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

	public TorrentManager(int peerId, int optimisticUnchoke,
			int preferredUnchoke, int preferredNeighbor) {

		this.myPeerId = peerId;

		this.myPeerInfo = ConfigurationReader.getInstance().getPeerInfo()
				.get(myPeerId);

		this.logger = LoggerUtility.getInstance(myPeerId);

		this.optimisticUnchokeInterval = optimisticUnchoke;

		this.preferredNeighborCount = preferredNeighbor;

		this.preferredUnchokeInterval = preferredUnchoke;

	}

	@Override
	public void run() {

		PeerConfig myPeerInfo = ConfigurationReader.getInstance().getPeerInfo()
				.get(myPeerId);

		setFileData();

		mybitField = myPeerInfo.bitfield;
		allRequestedBits = new byte[(int) Math.ceil(Integer
				.parseInt(ConfigurationReader.getInstance().getCommonProps()
						.get("numPieces")) / 8.0d)];
		Arrays.fill(allRequestedBits, (byte) 0);

		establishClientConnections();

		acceptConnections(myPeerInfo.getPort());

		scheduler.scheduleAtFixedRate(findPreferredNeighbour, 0,
				preferredUnchokeInterval, TimeUnit.SECONDS);

		scheduler.scheduleAtFixedRate(findOptimisticallyUnchokedNeighbour, 0,
				optimisticUnchokeInterval, TimeUnit.SECONDS);

		scheduler.scheduleAtFixedRate(shutdownTorrent, 3, 30, TimeUnit.SECONDS);
	}

	public void setFileData() {

		String fileName = ConfigurationReader.getInstance().getCommonProps()
				.get("FileName");
		Integer fileSize = Integer.parseInt(ConfigurationReader.getInstance()
				.getCommonProps().get("FileSize"));
		fileData = new byte[fileSize];

		File file = new File("peer_" + myPeerId + File.separator + fileName);

		if (file.exists()) {
			if (file.length() != fileSize) {
				System.out.println("File size discrepancy.");
				Thread.currentThread().interrupt();
			} else {
				try {
					FileInputStream fileInputStream = new FileInputStream(file);
					fileInputStream.read(fileData);
					fileInputStream.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}

	}

	public void establishClientConnections() {

		HashMap<Integer, PeerConfig> peerMap = ConfigurationReader
				.getInstance().getPeerInfo();

		for (Integer currPeerId : peerMap.keySet()) {

			if (currPeerId < myPeerId) {

				PeerConfig currPeerInfo = peerMap.get(currPeerId);

				try {
					// create a socket to connect to the peer
					P2PConnectionThread p = new P2PConnectionThread(myPeerInfo,
							currPeerInfo, new Socket(currPeerInfo.hostName,
									currPeerInfo.port), true);

					logger.log("Peer " + myPeerId
							+ " makes a connection to Peer " + currPeerId);

					System.out.println("Connected to " + currPeerInfo.hostName
							+ " on port number " + currPeerInfo.port);

					openTCPconnections.add(p);

					p.start();

				} catch (ConnectException e) {
					System.err
							.println("Connection refused. You need to initiate a server first.");

				} catch (UnknownHostException unknownHost) {
					System.err
							.println("You are trying to connect to an unknown host!");

				} catch (IOException ioException) {
					ioException.printStackTrace();

				} catch (Exception handshakeError) {
					System.out.println(handshakeError.getMessage());
				}

			}

		}
	}

	public void acceptConnections(int port) {

		HashMap<Integer, PeerConfig> peerMap = ConfigurationReader
				.getInstance().getPeerInfo();

		int expectedConnections = 0;
		for (Integer currPeerId : peerMap.keySet()) {
			if (currPeerId > myPeerId) {
				expectedConnections++;
			}
		}

		try {
			ServerSocket serverSocket = new ServerSocket(port);
			while (expectedConnections > 0) {
				Socket acceptedSocket = serverSocket.accept();
				if (acceptedSocket != null) {
					P2PConnectionThread peerThread = new P2PConnectionThread(
							myPeerInfo, null, acceptedSocket, false);
					openTCPconnections.add(peerThread);
					peerThread.start();
					logger.log("Peer " + myPeerId + " is connected from Peer "
							+ peerThread.getPeerInfo().peerId);
					expectedConnections--;
				}
			}
			serverSocket.close();
		} catch (Exception e) {
			e.printStackTrace();

		}

	}

	final Runnable findPreferredNeighbour = new Runnable() {

		@Override
		public void run() {

			if (!peersInterestedInMe.isEmpty()) {
				unchokedList.clear();
				chokedList.clear();
				int count = 0;

				List<PeerConfig> peers = new ArrayList<PeerConfig>(
						peersInterestedInMe.values());
				Collections.sort(peers, new DownloadComparator<PeerConfig>());

				String prefList = "";

				for (PeerConfig peer : peers) {
					if (count >= preferredNeighborCount) {
						if (peer.getPeerId() != optimisticallyUnchokedPeer) {
							chokedList.add(peer);
							if (!peer.isChoked) {
								sendChokeMessage(peer.peerId);
								peersInterestedInMe.get(peer.peerId).isChoked = true;
							}
						}

					} else {
						unchokedList.add(peer);
						prefList += "," + String.valueOf(peer.peerId);
						if (peer.isChoked) {
							sendUnchokeMessage(peer.peerId);
							peersInterestedInMe.get(peer.peerId).isChoked = false;
						}

					}
					count++;
				}

				if (!prefList.isEmpty()) {
					prefList = prefList.replaceFirst(",", "");
				}

				logger.log("Peer " + myPeerId + " has the preferred neighbors "
						+ prefList);
			}
		}
	};

	final Runnable findOptimisticallyUnchokedNeighbour = new Runnable() {

		@Override
		public void run() {

			if (!chokedList.isEmpty()) {
				int random = new Random().nextInt(chokedList.size());

				PeerConfig peer = chokedList.remove(random);
				unchokedList.add(peer);
				optimisticallyUnchokedPeer = peer.peerId;

				if (peersInterestedInMe.get(peer.peerId).isChoked) {
					sendUnchokeMessage(optimisticallyUnchokedPeer);
					peersInterestedInMe.get(peer.peerId).isChoked = false;
				}

			}
			if (optimisticallyUnchokedPeer != -1)
				logger.log("Peer " + myPeerId
						+ " has the optimistically unchoked neighbor "
						+ optimisticallyUnchokedPeer);

		}
	};

	public P2PConnectionThread getConnectionByPeerID(int id) {

		for (P2PConnectionThread thread : openTCPconnections) {
			if (thread.getPeerInfo().peerId == id)
				return thread;
		}
		return null;
	}

	final Runnable shutdownTorrent = new Runnable() {

		@Override
		public void run() {

			if (openTCPconnections.size() == ConfigurationReader.getInstance()
					.getPeerInfo().size() - 1) {

				boolean shutDown = true;

				for (P2PConnectionThread p : openTCPconnections) {
					byte[] pBitFieldMsg = p.getPeerInfo().getBitfield();
					if (Arrays.equals(pBitFieldMsg, PeerConfig.fullBitfield) == false) {
						shutDown = false;
						break;
					}
				}

				if (shutDown) {
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					for (P2PConnectionThread p : openTCPconnections) {
						p.shutDownCleanly();
						p.interrupt();
					}

					System.out.println("Scheduler shutdown called.");

					scheduler.shutdown();
					if (!scheduler.isShutdown()) {
						System.out.println("Did not shutdown torrent");
					}
					try {
						scheduler.awaitTermination(5, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

		}
	};

	private void sendChokeMessage(int peerId) {

		byte[] message = MessageUtil.getMessage(MessageType.CHOKE);
		OutputStream out = messageStreams.get(peerId);
		try {
			synchronized (out) {
				out.write(message);
				out.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void sendUnchokeMessage(int peerId) {

		byte[] message = MessageUtil.getMessage(MessageType.UNCHOKE);
		OutputStream out = messageStreams.get(peerId);
		try {
			synchronized (out) {
				out.write(message);
				out.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
