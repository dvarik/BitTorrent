import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

public class ConfigurationReader {

	private HashMap<Integer, PeerConfig> peerProps = new HashMap<Integer, PeerConfig>();
	private HashMap<String, String> commonProps = new HashMap<String, String>();

	private final String peerInfoFileName = "PeerInfo.cfg";
	private final String commonFileName = "Common.cfg";

	private static ConfigurationReader instance = null;

	public static synchronized ConfigurationReader getInstance() {
		
		if (instance == null)
			instance = new ConfigurationReader();
		
		return instance;
	}

	private ConfigurationReader() {

		try {
			BufferedReader br = new BufferedReader(
					new InputStreamReader(getClass().getResourceAsStream(commonFileName)));

			String line = null;
		
			while ((line = br.readLine()) != null) {
				String[] split = line.split(" ");
				commonProps.put(split[0], split[1]);
			}

			br.close();

			br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(peerInfoFileName)));
			
			line = null;
			while ((line = br.readLine()) != null) {
			
				String[] split = line.split(" ");
				int peerId = Integer.parseInt(split[0]);
				PeerConfig peer = new PeerConfig(peerId, split[1], Integer.parseInt(split[2]),
						Integer.parseInt(split[3]));
				peerProps.put(peerId, peer);
			}
			
			br.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			// add logging
		} catch (IOException e) {
			e.printStackTrace();
			// add logging
		}
	}

	public HashMap<String, String> getCommonProps() {
		return this.commonProps;
	}

	public HashMap<Integer, PeerConfig> getPeerInfo() {
		return this.peerProps;
	}

}
