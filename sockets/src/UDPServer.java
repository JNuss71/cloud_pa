import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

class UDPServer {
	public static void main(String args[]) throws Exception {

		final int SIZE = 63*1024;
		DatagramSocket serverSocket = new DatagramSocket(9876);


		while (true) {
			byte[] receiveData = new byte[SIZE];
			byte[] sendData = new byte[SIZE];

			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);
			
			// received
			serverSocket.receive(receivePacket);
			
			String sentence = new String(receivePacket.getData());

			InetAddress IPAddress = receivePacket.getAddress();
			int port = receivePacket.getPort();
			
			String capitalizedSentence = sentence.toUpperCase();
			sendData = capitalizedSentence.getBytes();
			DatagramPacket sendPacket = new DatagramPacket(sendData,
					sendData.length, IPAddress, port);
			
			// send response
			serverSocket.send(sendPacket);
		}
		
	}
}
