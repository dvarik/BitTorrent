import java.util.Comparator;

public class DownloadComparator<T extends PeerConfig> implements Comparator<PeerConfig> {

	@Override
	public int compare(PeerConfig o1, PeerConfig o2) {

		return (int)(o1.downloadRate - o2.downloadRate); 
	}


}
