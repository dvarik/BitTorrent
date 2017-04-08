import java.io.IOException;
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

	List<P2PConnectionThread> openTCPconnections = new ArrayList<P2PConnectionThread>();

	static volatile int optimisticallyUnchokedPeer = -1;

	static List<PeerConfig> unchokedList = Collections.synchronizedList(new ArrayList<PeerConfig>());

	static List<PeerConfig> chokedList = Collections.synchronizedList(new ArrayList<PeerConfig>());

	static ConcurrentHashMap<Integer, PeerConfig> peersInterestedInMe = new ConcurrentHashMap<Integer, PeerConfig>();

	public TorrentManager(int peerId, int optimisticUnchoke, int preferredUnchoke, int preferredNeighbor) {

		this.myPeerId = peerId;

		this.myPeerInfo = ConfigurationReader.getInstance().getPeerInfo().get(myPeerId);

		this.logger = new LoggerUtility(myPeerId);

		this.optimisticUnchokeInterval = optimisticUnchoke;

		this.preferredNeighborCount = preferredNeighbor;

		this.preferredUnchokeInterval = preferredUnchoke;

	}

	@Override
	public void run() {

		PeerConfig myPeerInfo = ConfigurationReader.getInstance().getPeerInfo().get(myPeerId);

		establishClientConnections();

		acceptConnections(myPeerInfo.getPort());

		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

		scheduler.scheduleAtFixedRate(findPreferredNeighbour, 0, preferredUnchokeInterval, TimeUnit.SECONDS);

		scheduler.scheduleAtFixedRate(findOptimisticallyUnchokedNeighbour, 0, optimisticUnchokeInterval,
				TimeUnit.SECONDS);

	}

	public void establishClientConnections() {

		HashMap<Integer, PeerConfig> peerMap = ConfigurationReader.getInstance().getPeerInfo();

		for (Integer currPeerId : peerMap.keySet()) {

			if (currPeerId < myPeerId) {

				PeerConfig currPeerInfo = peerMap.get(currPeerId);

				try {
					// create a socket to connect to the peer
					P2PConnectionThread p = new P2PConnectionThread(myPeerInfo, currPeerInfo,
							new Socket(currPeerInfo.hostName, currPeerInfo.port), true);

					logger.log("Peer " + myPeerId + " makes a connection to Peer " + currPeerId);

					System.out
							.println("Connected to " + currPeerInfo.hostName + " on port number " + currPeerInfo.port);

					openTCPconnections.add(p);

					p.start();

				} catch (ConnectException e) {
					System.err.println("Connection refused. You need to initiate a server first.");
					logger.log(e.getMessage());
				} catch (UnknownHostException unknownHost) {
					System.err.println("You are trying to connect to an unknown host!");
					logger.log(unknownHost.getMessage());
				} catch (IOException ioException) {
					ioException.printStackTrace();
					logger.log(ioException.getMessage());
				} catch (Exception handshakeError) {
					logger.log(handshakeError.getMessage());
				}

			}

		}
	}

	public void acceptConnections(int port) {

		HashMap<Integer, PeerConfig> peerMap = ConfigurationReader.getInstance().getPeerInfo();

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
					P2PConnectionThread peerThread = new P2PConnectionThread(myPeerInfo, null, acceptedSocket, false);
					openTCPconnections.add(peerThread);
					peerThread.start();
					expectedConnections--;
				}
			}
			serverSocket.close();
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(e.getMessage());
		}

	}

	/*
	 * below 3 methods need to be implemented as scheduled tasks
	 */

	final Runnable findPreferredNeighbour = new Runnable() {

		@Override
		public void run() {

			if (peersInterestedInMe != null) {

				unchokedList.clear();
				chokedList.clear();
				int count = 0;

				List<PeerConfig> peers = new ArrayList<PeerConfig>(peersInterestedInMe.values());
				Collections.sort(peers, new DownloadComparator<PeerConfig>());

				for (PeerConfig peer : peers) {
					if (count >= preferredNeighborCount) {
						if (peer.getPeerId() != optimisticallyUnchokedPeer) {
							chokedList.add(peer);
							if (!peer.isChoked) {
								getConnectionByPeerID(peer.peerId).getChokeSignal().notify();
							}
						}

					} else {
						unchokedList.add(peer);
						if (peer.isChoked) {
							getConnectionByPeerID(peer.peerId).getUnchokeSignal().notify();
						}

					}
					count++;
				}

			}

			System.out.println(Arrays.toString(unchokedList.toArray()));

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
					getConnectionByPeerID(optimisticallyUnchokedPeer).getUnchokeSignal().notify();
				}

			}
			System.out.println(optimisticallyUnchokedPeer);

		}
	};

	public P2PConnectionThread getConnectionByPeerID(int id) {

		for (P2PConnectionThread thread : openTCPconnections) {
			if (thread.getPeerInfo().peerId == id)
				return thread;
		}

		return null;

	}

	public void shutdownTorrent() {

	}

}
