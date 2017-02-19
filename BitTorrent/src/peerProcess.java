import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

public class peerProcess {

	Socket requestSocket; // socket connect to the server
	ObjectOutputStream out; // stream write to the socket
	ObjectInputStream in; // stream read from the socket
	String message; // message send to the server
	String serverMessage; // capitalized message read from the server
	
	public static LoggerUtility logger = null;

	public void establishClientConnection(int selfPeerId) {
		
		HashMap<Integer, PeerConfig> peerMap = ConfigurationReader.getInstance().getPeerInfo();
		
		for (Integer currPeerId : peerMap.keySet()) {
		
			if (currPeerId < selfPeerId) {
			
				PeerConfig currPeerInfo = peerMap.get(currPeerId);
				try {
					// create a socket to connect to the peer
					requestSocket = new Socket(currPeerInfo.hostName, currPeerInfo.port);
					
					//logger.getLogger().config(System.currentTimeMillis() + "[Time]: Peer " + selfPeerId + "
						// makes a connection to Peer " + currPeerId);

					System.out
							.println("Connected to " + currPeerInfo.hostName + " on port number " + currPeerInfo.port);

					// initialize inputStream and outputStream
					out = new ObjectOutputStream(requestSocket.getOutputStream());
					out.flush();
					in = new ObjectInputStream(requestSocket.getInputStream());

					while (true) {
						// test input message
						message = "This is a test.";
						// Send the message to the server
						sendMessage(message);
						// Receive the upperCase sentence from the server
						serverMessage = (String) in.readObject();
						// show the message to the user
						System.out.println("Receive message: " + serverMessage);
					}
				
				} catch (ConnectException e) {
					System.err.println("Connection refused. You need to initiate a server first.");
				} catch (ClassNotFoundException e) {
					System.err.println("Class not found");
				} catch (UnknownHostException unknownHost) {
					System.err.println("You are trying to connect to an unknown host!");
				} catch (IOException ioException) {
					ioException.printStackTrace();
				} finally {
				
					// Close connections
					try {
						in.close();
						out.close();
						requestSocket.close();
					} catch (IOException ioException) {

						ioException.printStackTrace();
					}
				}

			}

		}
	}

	public void acceptServerConnection(int selfPeerId) {

	}

	public void findPreferredNeighbours(int k, int p) {

	}

	public void findOptimisticallyUnchokedNeighbour(int p) {

	}

	public void sendMessage(String message) {

	}

	public static void main(String[] args) {

		int peerId = Integer.parseInt(args[0]);
		PeerConfig peerInfo = ConfigurationReader.getInstance().getPeerInfo().get(peerId);
		peerProcess peer = new peerProcess();
		peer.establishClientConnection(peerInfo.peerId);

	}
}
