import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

	static volatile HashMap<Integer, PeerConfig> unchokedPeers = null;

	static volatile PeerConfig optimisticallyUnchokedPeer = null;

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

		
		
		establishClientConnections();

		acceptConnections(myPeerInfo.getPort());
		
		
		
		/*ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);

		 ScheduledFuture<V> scheduledFuture =
		 scheduledExecutorService.scheduleAtFixedRate(findPreferredNeighbours,
		 preferredUnchokeInterval, TimeUnit.SECONDS);
		 
		 */

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

	public void findPreferredNeighbours(int k, int p) {

	}

	public void findOptimisticallyUnchokedNeighbour(int p) {

	}
	
	public void shutdownTorrent() {

	}

}
