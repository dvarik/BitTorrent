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
	boolean isChoked = true;
	long downloadRate;

	public PeerConfig(int peerId, String hostName, int port, int hasFile, int numPieces) {
		super();

		this.peerId = peerId;
		this.hostName = hostName;
		this.port = port;
		this.hasFile = hasFile;

		bitfield = new byte[(int) Math.ceil(numPieces / 8)];

		if (this.hasFile == 1) {
			if (numPieces % 8 == 0) {
				Arrays.fill(bitfield, (byte) 255);
			} else {
				Arrays.fill(bitfield, (byte) 255);
				bitfield[bitfield.length - 1] = 0;
				int numLastByteSetBits = (int) numPieces % 8;
				while (numLastByteSetBits != 0) {
					bitfield[bitfield.length - 1] |= (1 << (8 - numLastByteSetBits));
					numLastByteSetBits--;
				}
			}
		} else {
			Arrays.fill(bitfield, (byte) 0);
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

	public void setBitfield(byte[] bf) {
		this.bitfield = bf;
	}

	public byte[] getBitfield() {
		return bitfield;
	}

}
