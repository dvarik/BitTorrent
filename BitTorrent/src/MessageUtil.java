public class MessageUtil {

	public static final byte[] HANDSHAKE_HEADER = "P2PFILESHARINGPROJ"
			.getBytes();

	public static final byte[] ZERO_BITS = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	public static byte[] getMessageHeader(final int peerId) {

		byte[] paddedPeerid = integerToByteArray(peerId);
		byte[] temp = concat(HANDSHAKE_HEADER, ZERO_BITS);
		byte[] result = concat(temp, paddedPeerid);

		return result;
	}

	public static byte[] getMessage(final MessageType mType) {
		byte[] msgLength = integerToByteArray(1);
		return concat(msgLength, mType.value);
	}

	public static byte[] getMessage(final byte[] payload,
			final MessageType mType) {
		byte[] msgLength = integerToByteArray(payload.length + 1);
		byte[] temp = concat(msgLength, mType.value);
		return concat(temp, payload);
	}

	public static byte[] getMessage(final String payload,
			final MessageType msgType) {
		int l = payload.getBytes().length;
		byte[] msgL = MessageUtil.integerToByteArray(l + 1); // plus one for
																// message
																// type
		return MessageUtil.concatenateByteArrays(
				msgL,
				MessageUtil.concatenateByteArray(msgType.value,
						payload.getBytes()));
	}

	public static byte[] concatenateByteArray(final byte b, final byte[] a) {
		byte[] result = new byte[a.length + 1];
		System.arraycopy(a, 0, result, 0, a.length);
		result[a.length] = b;
		return result;
	}

	public static byte[] concatenateByteArrays(final byte[] a, final byte[] b) {
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}

	public static byte[] integerToByteArray(final int intVal) {

		byte[] arr = new byte[4];
		arr[0] = (byte) ((intVal & 0xFF000000) >> 24);
		arr[1] = (byte) ((intVal & 0x00FF0000) >> 16);
		arr[2] = (byte) ((intVal & 0x0000FF00) >> 8);
		arr[3] = (byte) (intVal & 0x000000FF);

		return arr;
	}

	public static int byteArrayToInteger(final byte[] b) {
		int ret = 0;
		for (int i = 0; i < 4; i++) {
			int shift = (4 - 1 - i) * 8;
			ret += (b[i] & 0x000000FF) << shift;
		}
		return ret;
	}

	public static byte[] concat(final byte[] arr, final byte val) {
		byte[] result = new byte[arr.length + 1];

		System.arraycopy(arr, 0, result, 0, arr.length);
		result[arr.length] = val;

		return result;
	}

	public static byte[] concat(final byte[] arr1, final byte[] arr2) {
		byte[] result = new byte[arr1.length + arr2.length];

		System.arraycopy(arr1, 0, result, 0, arr1.length);
		System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
		return result;
	}

	public static byte[] concatenateByteArrays(final byte[] a,
			final int aLength, final byte[] b, final int bLength) {
		byte[] result = new byte[aLength + bLength];
		System.arraycopy(a, 0, result, 0, aLength);
		System.arraycopy(b, 0, result, aLength, bLength);
		return result;
	}

}
