
import java.io.*;
import java.net.*;
import java.util.Scanner;

class TCPClientFileTransfer
{

 public static void main(String argv[]) throws Exception
 {
  Socket clientSocket = new Socket("localhost", 6789);
  DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());

  String content = new Scanner(new File("/Users/arun/workspace/sockets/src/TCPClientFileTransfer.java")).useDelimiter("\\Z").next();
  System.out.println(content.length());
  
  outToServer.writeBytes(content);
  clientSocket.close();
 }
}
