import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;

/**
 * @author dvarik
 *
 */
public class P2PConnectionThread extends Thread {

	// do handshake stuff to establish connection

	// send different msgs to peer depending on torrent manager's pref neighbs
	// and opt unchoked maps

	// send recieve data from this peer accordingly

	private final int peerId;

	private final boolean isClientConnection;

	private final Socket socket;

	private OutputStream out;

	private InputStream in;

	private final LoggerUtility logger;

	public P2PConnectionThread(int peerId, Socket s, boolean isClient) throws Exception {
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

		if (this.isClientConnection) {

			this.peerId = peerId;

			sendHandshakeMsg();
			if (receiveHandshakeMsg() != this.peerId)
				throw new Exception("Error in handshake!");

		} else {

			this.peerId = receiveHandshakeMsg();
			if (this.peerId == -1)
				throw new Exception("Error in handshake!");
			sendHandshakeMsg();
		}
		this.logger = new LoggerUtility(peerId);

	}

	public void sendHandshakeMsg() {

		byte[] messageHeader = MessageUtil.getMessageHeader((byte) peerId);

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

			String peer = new String(sub);
			Integer peerId = Integer.parseInt(peer);

			System.out.println("The peer id is " + peerId);

			return peerId;

		} catch (IOException e) {
			e.printStackTrace();
		}

		return -1;

	}

	
}
