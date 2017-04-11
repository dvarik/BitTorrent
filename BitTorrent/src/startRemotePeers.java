import java.io.IOException;
import java.util.HashMap;


public class startRemotePeers {

	public static void main(String[] args) throws IOException {


	HashMap<Integer,PeerConfig> map = ConfigurationReader.getInstance().getPeerInfo();
	String workingDir = System.getProperty("user.dir");
	String peerProcessName = new String("java peerProcess");

	for (Integer peerId : map.keySet())
	{
		String hostname = map.get(peerId).getHostName();
		String peerProcessArguments = Integer.toString(peerId);

		Runtime.getRuntime().exec("ssh " + hostname + " cd " + workingDir + " ; " +
				peerProcessName + " " + peerProcessArguments );
		}

	}

}
