import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.*;

public class Peer {

	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private final Socket socket;

	private final int myPeerId;

	private OutputStream out;

	private InputStream in;

	private boolean optimisticallyUnchoked = false;

	private boolean client = false;

	private List<Peer> interestedNeighbours = new ArrayList<Peer>();

	private Map<Integer, Peer> disinterestedNeighbours = new HashMap<Integer, Peer>();

	private Map<Integer, Peer> chokedBy = new HashMap<Integer, Peer>();

	private Map<Integer, Peer> unchokedBy = new HashMap<Integer, Peer>();

	private byte[] bitField;

	public Peer(int peerId, Socket s) {
		myPeerId = peerId;
		this.socket = s;
		try {

			out = new BufferedOutputStream(socket.getOutputStream());
			in = new BufferedInputStream(socket.getInputStream());

		} catch (IOException e) {
			logger.severe("socket exception!!!");
			e.printStackTrace();
		}
	}

	public boolean isOptimisticallyUnchoked() {
		return optimisticallyUnchoked;
	}

	public void setOptimisticallyUnchoked(boolean optimisticallyUnchoked) {
		this.optimisticallyUnchoked = optimisticallyUnchoked;
	}

	public boolean isClient() {
		return client;
	}

	public void setClient(boolean client) {
		this.client = client;
	}

	public List<Peer> getInterestedNeighbours() {
		return interestedNeighbours;
	}

	public void setInterestedNeighbours(List<Peer> interestedNeighbours) {
		this.interestedNeighbours = interestedNeighbours;
	}

	public Map<Integer, Peer> getDisinterestedNeighbours() {
		return disinterestedNeighbours;
	}

	public void setDisinterestedNeighbours(Map<Integer, Peer> disinterestedNeighbours) {
		this.disinterestedNeighbours = disinterestedNeighbours;
	}

	public Map<Integer, Peer> getChokedBy() {
		return chokedBy;
	}

	public void setChokedBy(Map<Integer, Peer> chokedBy) {
		this.chokedBy = chokedBy;
	}

	public Map<Integer, Peer> getUnchokedBy() {
		return unchokedBy;
	}

	public void setUnchokedBy(Map<Integer, Peer> unchokedBy) {
		this.unchokedBy = unchokedBy;
	}

	public byte[] getBitField() {
		return bitField;
	}

	public void sendHandshakeMsg() {

		byte[] messageHeader = MessageUtil.getMessageHeader((byte) myPeerId);

		try {
			out.write(messageHeader);
			out.flush();
		} catch (IOException e) {
			logger.severe("Send handshake failed !! " + e.getMessage());
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

	// Sending bitfield messages
	public void sendBitFieldMessage() {
		try {
			byte[] bitField = getBitField();
			byte[] message = MessageUtil.getMessage(bitField, MessageUtil.MessageType.BITFIELD);
			out.write(message);
			out.flush();
		} catch (IOException e) {
			System.out.println("Message sending with bitfield failed.");
			e.printStackTrace();
		}
	}

	// Sends 'interested' messages
	public void sendInterestedMsg() {
		System.out.println("Sending interested message ");
		byte[] actualMessage = MessageUtil.getMessage(MessageUtil.MessageType.INTERESTED);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("Exception during sending interested messages " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Sends 'not interested' messages
	public void sendNotInterestedMsg() {
		byte[] actualMessage = MessageUtil.getMessage(MessageUtil.MessageType.NOT_INTERESTED);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("Exception during sending uninterested messages" + e.getMessage());
			e.printStackTrace();
		}
	}

	// Sends 'have' messages
	public void sendHaveMsg(int pieceIndex) {
		byte[] actualMessage = MessageUtil.getMessage(MessageUtil.integerToByteArray(pieceIndex),
				MessageUtil.MessageType.HAVE);
		try {
			out.write(actualMessage);
			out.flush();

		} catch (IOException e) {
			System.out.println("Exception during sending have messages" + e.getMessage());
			e.printStackTrace();
		}
	}

	// Sends 'choke' messages
	public void sendChokeMsg() {
        byte[] actualMessage = MessageUtil
                .getMessage(MessageUtil.MessageType.CHOKE);
        try {
            out.write(actualMessage);
            out.flush();

        } catch (IOException e) {
            System.out.println("Exception during sending choke messages " + e.getMessage());
            e.printStackTrace();
        }
    }

	// Sends 'unchoke' messages
	public void sendUnChokeMsg() {
        byte[] actualMessage = MessageUtil
                .getMessage(MessageUtil.MessageType.UNCHOKE);
        try {
            out.write(actualMessage);
            out.flush();

        } catch (IOException e) {
            System.out.println("Exception during sending unchoke messages " + e.getMessage());
            e.printStackTrace();
        }
    }
	
	
	// Sends 'request' message
    public void sendRequestMsg(int pieceIndex) {
        if (pieceIndex >= 0) {
        	byte[] pieceIndexArr = MessageUtil.integerToByteArray(pieceIndex);

            byte[] actualMessage = MessageUtil.getMessage(
                    pieceIndexArr, MessageUtil.MessageType.REQUEST);
            
            try {
                out.write(actualMessage);
                out.flush();
                
                //---
                
            } catch (IOException e) {
                System.out.println("Exception during reading " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
	

	// Close all the connections
	public void close() {
		try {
			in.close();
			out.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Exception while closing peer socket");
		}
	}

}
