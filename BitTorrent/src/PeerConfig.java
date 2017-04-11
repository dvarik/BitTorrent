import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author dvarik
 *
 */
public class PeerConfig {

	int peerId;
	String hostName;
	int port;
	int hasFile;
	byte[] bitfield;
	byte[] allRequestedBits;
	boolean isChoked;
	long downloadRate;
	ArrayList<Integer> needToRequestPieces;
	static byte[] fullBitfield;

	public PeerConfig(int peerId, String hostName, int port, int hasFile, int numPieces) {
		super();

		this.peerId = peerId;
		this.hostName = hostName;
		this.port = port;
		this.hasFile = hasFile;
		this.isChoked = true;
		this.needToRequestPieces = new ArrayList<Integer>();
		if (this.hasFile != 1)
		{
			for(int i=0;i<numPieces;i++)
			{
				needToRequestPieces.add(i+1);
			}
		}

		fullBitfield = new byte[(int) Math.ceil(numPieces / 8.0d)];
		initializeBitfield(numPieces, fullBitfield);
		bitfield = new byte[(int) Math.ceil(numPieces / 8.0d)];
		Arrays.fill(bitfield, (byte) 0);
		System.out.println("Bitfield length is " + bitfield.length);
		System.out.println("numpieces:" + numPieces);
		allRequestedBits = new byte[(int) Math.ceil(numPieces / 8.0d)];
		Arrays.fill(allRequestedBits, (byte) 0);

		if (this.hasFile == 1) {
			initializeBitfield(numPieces, bitfield);
		}
	}

	private void initializeBitfield(int numPieces, byte[] bf)
	{
		if (numPieces % 8 == 0) {
			Arrays.fill(bf, (byte) 255);
		} else {
			Arrays.fill(bf, (byte) 255);
			bf[bf.length - 1] = 0;

			int numLastByteSetBits = (int) numPieces % 8;
			while (numLastByteSetBits != 0) {
				bf[bf.length - 1] |= (1 << (8 - numLastByteSetBits));
				numLastByteSetBits--;
			}

		}
	}

	public int getPeerId() {
		return peerId;
	}

	public void setPeerId(int peerId) {
		this.peerId = peerId;
	}

	public String getHostName() {
		return hostName;
	}

	public void setHostName(String hostName) {
		this.hostName = hostName;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getHasFile() {
		return hasFile;
	}

	public void setHasFile(int hasFile) {
		this.hasFile = hasFile;
	}

	public byte[] getBitfield() {
		return bitfield;
	}

	public void setBitfield(byte[] bf) {
		this.bitfield = bf;
	}

	public byte[] getAllRequestedBits() {
		return this.allRequestedBits;
	}

	public void setAllRequestedBits(byte[] bf) {
		this.allRequestedBits = bf;
	}

	public boolean getIsChoked() {
		return isChoked;
	}

	public void setIsChoked(boolean flag) {
		this.isChoked = flag;
	}

	public long getDownloadRate() {
		return downloadRate;
	}

	public void setDownloadRate(long downloadRate) {
		this.downloadRate = downloadRate;
	}





}
