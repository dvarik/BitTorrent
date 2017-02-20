import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class peerProcess {

	final int myPeerId;

	final LoggerUtility logger;

	// list of connected peers
	List<Peer> connectedPeers = new ArrayList<Peer>(); 


	public peerProcess(int selfPeerId) {
		
		this.myPeerId = selfPeerId;
		
		this.logger = new LoggerUtility(selfPeerId);
	}

	public void establishClientConnection(int selfPeerId) {

		HashMap<Integer, PeerConfig> peerMap = ConfigurationReader.getInstance().getPeerInfo();

		for (Integer currPeerId : peerMap.keySet()) {

			if (currPeerId < selfPeerId) {

				PeerConfig currPeerInfo = peerMap.get(currPeerId);

				try {
					// create a socket to connect to the peer
					Peer p = new Peer(currPeerId, new Socket(currPeerInfo.hostName, currPeerInfo.port));

					logger.log("Peer " + selfPeerId + " makes a connection to Peer " + currPeerId);

					System.out
							.println("Connected to " + currPeerInfo.hostName + " on port number " + currPeerInfo.port);

					p.sendHandshakeMsg();

					int m = p.receiveHandshakeMsg();

					connectedPeers.add(p);

				} catch (ConnectException e) {
					System.err.println("Connection refused. You need to initiate a server first.");
				} catch (UnknownHostException unknownHost) {
					System.err.println("You are trying to connect to an unknown host!");
				} catch (IOException ioException) {
					ioException.printStackTrace();
				}

			}

		}
	}

	public void acceptConnection(int peerId, int port) {

	}

	public void findPreferredNeighbours(int k, int p) {

	}

	public void findOptimisticallyUnchokedNeighbour(int p) {

	}


	public static void main(String[] args) {

		int peerId = Integer.parseInt(args[0]);
		
		PeerConfig peerInfo = ConfigurationReader.getInstance().getPeerInfo().get(peerId);
		
		peerProcess peer = new peerProcess(peerId);

		HashMap<String, String> comProp = ConfigurationReader.getInstance().getCommonProps();

		peer.establishClientConnection(peerInfo.peerId);

		peer.acceptConnection(peerId, peerInfo.getPort());

		int m = Integer.parseInt(comProp.get("OptimisticUnchokingInterval"));

		int k = Integer.parseInt(comProp.get("NumberOfPreferredNeighbors"));

		int p = Integer.parseInt(comProp.get("UnchokingInterval"));

		peer.findPreferredNeighbours(k, p);

		peer.findOptimisticallyUnchokedNeighbour(m);

	}

}
