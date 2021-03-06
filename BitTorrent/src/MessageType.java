
public enum MessageType {

	CHOKE((byte) 0), UNCHOKE((byte) 1), INTERESTED((byte) 2), NOT_INTERESTED((byte) 3), HAVE((byte) 4), BITFIELD(
			(byte) 5), REQUEST((byte) 6), PIECE((byte) 7);

	byte value;

	private MessageType(byte id) {
		this.setValue(id);
	}

	public byte getValue() {
		return value;
	}

	public void setValue(byte value) {
		this.value = value;
	}
	
	public static MessageType getType(byte value) {

		for (MessageType t : MessageType.values()) {
			if (t.value == value) {
				return t;
			}
		}
		return null;
	}

}
