import java.io.*;
import java.net.*;

class UDPClient {
	public static void main(String args[]) throws Exception {

		int SIZE = 63*1024;
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(
				System.in));

		DatagramSocket clientSocket = new DatagramSocket();

		InetAddress IPAddress = InetAddress.getByName("fig.cs.umass.edu");

		byte[] sendData = new byte[SIZE];
		byte[] receiveData = new byte[SIZE];

		String sentence = inFromUser.readLine();
		
		while (sentence.length() < 31*1000)
			sentence = sentence + sentence;
		
		sendData = sentence.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData,
				sendData.length, IPAddress, 9876);

		clientSocket.send(sendPacket);

		DatagramPacket receivePacket = new DatagramPacket(receiveData,
				receiveData.length);

		clientSocket.receive(receivePacket);

		String modifiedSentence = new String(receivePacket.getData());

		System.out.println("FROM SERVER:" + modifiedSentence);
		clientSocket.close();
	}
}
