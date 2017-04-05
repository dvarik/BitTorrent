import java.util.HashMap;

/**
 * @author dvarik
 *
 */
public class peerProcess {


	public static void main(String[] args) {

		int myPeerId = Integer.parseInt(args[0]);

		HashMap<String, String> comProp = ConfigurationReader.getInstance().getCommonProps();

		int m = Integer.parseInt(comProp.get("OptimisticUnchokingInterval"));

		int k = Integer.parseInt(comProp.get("NumberOfPreferredNeighbors"));

		int p = Integer.parseInt(comProp.get("UnchokingInterval"));
		
		TorrentManager manager = new TorrentManager(myPeerId, m, p,k);

		manager.run();
		
	}

}
