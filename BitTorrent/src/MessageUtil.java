import java.io.IOException;
import java.io.InputStream;

public class MessageUtil {

	public static final byte[] HANDSHAKE_HEADER = "P2PFILESHARINGPROJ".getBytes();

	public static final byte[] ZERO_BITS = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

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

	public static byte[] getMessage(String payload, MessageType msgType) {
		int l = payload.getBytes().length;
		byte[] msgL = MessageUtil.intToByteArray(l + 1); // plus one for message type
		return MessageUtil.concatenateByteArrays(msgL,
				MessageUtil.concatenateByteArray(msgType.value, payload.getBytes()));
	}

	public static byte[] concatenateByteArray(byte b, byte[] a) {
		byte[] result = new byte[a.length + 1];
		System.arraycopy(a, 0, result, 0, a.length);
		result[a.length] = b;
		return result;
	}

	public static byte[] concatenateByteArrays(byte[] a, byte[] b) {
		byte[] result = new byte[a.length + b.length];
		System.arraycopy(a, 0, result, 0, a.length);
		System.arraycopy(b, 0, result, a.length, b.length);
		return result;
	}


	public static byte[] intToByteArray(final int integer) {
		byte[] result = new byte[4];

		result[0] = (byte) ((integer & 0xFF000000) >> 24);
		result[1] = (byte) ((integer & 0x00FF0000) >> 16);
		result[2] = (byte) ((integer & 0x0000FF00) >> 8);
		result[3] = (byte) (integer & 0x000000FF);

		return result;
	}

	public static int byteArrayToInt(byte[] b) {
		int value = 0;
		for (int i = 0; i < 4; i++) {
			int shift = (4 - 1 - i) * 8;
			value += (b[i] & 0x000000FF) << shift;
		}
		return value;
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
	
	 public static byte[] concatenateByteArrays(byte[] a, int aLength, byte[] b, int bLength) {
	        byte[] result = new byte[aLength + bLength];
	        System.arraycopy(a, 0, result, 0, aLength);
	        System.arraycopy(b, 0, result, aLength, bLength);
	        return result;
	    }

	 public synchronized static byte[] readBytes(InputStream in, byte[] byteArray, int length) throws IOException {
	        int len = length;
	        int idx = 0;
	        while (len != 0) {
	            int dataAvailableLength = in.available();
	            int read = Math.min(len, dataAvailableLength);
	            byte[] dataRead = new byte[read];
	            if (read != 0) {
	                in.read(dataRead);
	                byteArray = MessageUtil.concatenateByteArrays(byteArray, idx, dataRead, read);
	                idx += read;
	                len -= read;
	            }
	        }
	        return byteArray;
	    }

}
