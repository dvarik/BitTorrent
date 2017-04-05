/**
 * @author dvarik
 *
 */
public class PeerConfig {

	int peerId;
	String hostName;
	int port;
	int hasFile;
	
	boolean isChoked = true;
	
	long downloadRate;

	public PeerConfig(int peerId, String hostName, int port, int hasFile) {
		super();
		
		this.peerId = peerId;
		this.hostName = hostName;
		this.port = port;
		this.hasFile = hasFile;
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

}
