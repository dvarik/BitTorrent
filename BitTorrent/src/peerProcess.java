
public class peerProcess {

	Socket requestSocket;           //socket connect to the server 
	ObjectOutputStream out;         //stream write to the socket  
	ObjectInputStream in;          //stream read from the socket 
	String message;                //message send to the server
	String MESSAGE;                //capitalized message read from the server

	public void establishClientConnection(int selfPeerId)
	{
		HashMap<Integer, PeerConfig> peerMap = ConfigurationReader.getPeerInfo();
		for (Integer currPeerId : peerMap.keySet()) 
		{
			if (currPeerId < selfPeerId) 
			{
				PeerConfig currPeerInfo = peerMap.get(currPeerId);

				try{
					//create a socket to connect to the peer 
					requestSocket = new Socket(PeerConfig.hostName, PeerConfig.port); 
					
					System.out.println("Connected to "+ PeerConfig.hostName + "in port number" + PeerConfig.port); 
					
					//initialize inputStream and outputStream 
					out = new ObjectOutputStream(requestSocket.getOutputStream()); 
					out.flush(); 
					in = new ObjectInputStream(requestSocket.getInputStream());
					
					while(true)
					{
						//test input message 
						message = "This is a test."; 
						//Send the message to the server 
						sendMessage(message);
						//Receive the upperCase sentence from the server 
						MESSAGE = (String)in.readObject(); 
						//show the message to the user 
						System.out.println("Receive message: " + MESSAGE);
					}
				} catch (ConnectException e) {
					System.err.println("Connection refused. You need to initiate a server first.");
				} catch ( ClassNotFoundException e ) {             
					System.err.println("Class not found");         
				} catch(UnknownHostException unknownHost){
					System.err.println("You are trying to connect to an unknown host!"); 
				}
				catch(IOException ioException){ ioException.printStackTrace(); } finally{
					//Close connections 
					try{ 
						in.close(); out.close();
						requestSocket.close();
					} catch(IOException ioException){

						ioException.printStackTrace();
					}
				}

			}

		}
	}
	
	public void acceptServerConnection(int selfPeerId)
	{
		
	}
	
	public void findPreferredNeighbours(int k, int p)
	{
		
	}	
	
	public void findOptimisticallyUnchokedNeighbour(int p)
	{
		
	}

	public static void main(String[] args) {

		int peerId = Integer.parseInt(args[0]);
		PeerConfig peerInfo = ConfigurationReader.getPeerInfo().get(peerId);
		peerProcess peer = new peerProcess();
        peer.establishClientConnection(peerConfig.peerId);

	}
}
