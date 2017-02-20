
public class MessageUtil {

	public static final byte[] HANDSHAKE_HEADER = "P2PFILESHARINGPROJ".getBytes();

	public static final byte[] ZERO_BITS = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	public enum MessageTypes {

		CHOKE((byte) 0), UNCHOKE((byte) 1), INTERESTED((byte) 2), NOT_INTERESTED((byte) 3), HAVE((byte) 4), BITFIELD(
				(byte) 5), REQUEST((byte) 6), PIECE((byte) 7);

		private byte value;

		private MessageTypes(byte id) {
			this.setValue(id);
		}

		public byte getValue() {
			return value;
		}

		public void setValue(byte value) {
			this.value = value;
		}

	}

	public static byte[] getMessageHeader(byte peerId) {

		byte[] result = new byte[HANDSHAKE_HEADER.length + ZERO_BITS.length + 1];

		System.arraycopy(HANDSHAKE_HEADER, 0, result, 0, HANDSHAKE_HEADER.length);
		System.arraycopy(ZERO_BITS, 0, result, HANDSHAKE_HEADER.length, ZERO_BITS.length);
		result[result.length - 1] = peerId;

		return result;
	}

}
