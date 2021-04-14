import java.io.*;
import java.net.*;

/*
 * This class is a TCP server for reading a byte array of unknown length.
 */
class TCPServerByteArray {
	public static void main(String argv[]) throws Exception {
		byte[] buf = new byte[16];
		ServerSocket welcomeSocket = new ServerSocket(6789);

		try {
			while (true) {
				Socket connectionSocket = welcomeSocket.accept();
				InputStream inStream = ((connectionSocket.getInputStream()));

				int numRead = 0, numTotalRead = 0;
				while ((numRead = inStream.read(buf)) > 0) {
					numTotalRead += numRead;
					System.out.println("received: "
							+ new String(buf, 0, numRead));
				}
				System.out.println("Total bytes read = " + numTotalRead);
			}
		} finally {
			welcomeSocket.close();
		}
	}
}
