import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.TreeMap;

public class startRemotePeers {

	public static void main(String[] args) throws IOException {

		startRemotePeers st = new startRemotePeers();
		TreeMap<Integer, String> peers = st.getPeers();
		String workingDir = System.getProperty("user.dir");
		String peerProcessName = new String("java peerProcess");

		for (Integer peerId : peers.keySet()) {
			String hostname = peers.get(peerId);

			Runtime.getRuntime().exec("ssh " + hostname + " cd " + workingDir + " ; " + peerProcessName + " " + peerId);
		}

	}

	public TreeMap<Integer, String> getPeers() {

		TreeMap<Integer, String> ret = new TreeMap<Integer, String>();
		try {
			BufferedReader br = new BufferedReader(
					new InputStreamReader(getClass().getResourceAsStream("PeerInfo.cfg")));

			String line = null;

			while ((line = br.readLine()) != null) {

				String[] split = line.split(" ");
				int peerId = Integer.parseInt(split[0]);
				ret.put(peerId, split[1]);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return ret;

	}

}
