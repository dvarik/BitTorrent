
public class MessageUtil {

	public static final byte[] HANDSHAKE_HEADER = "P2PFILESHARINGPROJ".getBytes();

	public static final byte[] ZERO_BITS = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	public enum MessageType {

		CHOKE((byte) 0), UNCHOKE((byte) 1), INTERESTED((byte) 2), NOT_INTERESTED((byte) 3), HAVE((byte) 4), BITFIELD(
				(byte) 5), REQUEST((byte) 6), PIECE((byte) 7);

		private byte value;

		private MessageType(byte id) {
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

		byte[] temp = concat(HANDSHAKE_HEADER, ZERO_BITS);
		byte[] result = concat(temp, peerId);

		return result;
	}

	public static byte[] getMessage(MessageType mType) {
		byte[] msgLength = integerToByteArray(1);
		return concat(msgLength, mType.value);
	}

	public static byte[] getMessage(byte[] payload, MessageType mType) {
		byte[] msgLength = integerToByteArray(payload.length);
		byte[] temp = concat(msgLength, mType.value);
		return concat(temp, payload);
	}

	public static byte[] integerToByteArray(int intVal) {

		byte[] arr = new byte[4];

		arr[0] = (byte) ((intVal & 0xFF000000) >> 24);
		arr[1] = (byte) ((intVal & 0x00FF0000) >> 16);
		arr[2] = (byte) ((intVal & 0x0000FF00) >> 8);
		arr[3] = (byte) (intVal & 0x000000FF);

		return arr;
	}

	public static byte[] concat(byte[] arr, byte val) {
		byte[] result = new byte[arr.length + 1];

		System.arraycopy(arr, 0, result, 0, arr.length);
		result[arr.length] = val;

		return result;
	}

	public static byte[] concat(byte[] arr1, byte[] arr2) {
		byte[] result = new byte[arr1.length + arr2.length];

		System.arraycopy(arr1, 0, result, 0, arr1.length);
		System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
		return result;
	}
}
