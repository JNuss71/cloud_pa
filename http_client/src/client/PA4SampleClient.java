package client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ThreadLocalRandom;

import org.json.simple.JSONObject;

/**
 * @author gaozy
 *
 */
public class PA4SampleClient {
	
	private static String executePost(String targetURL, String requestContent) {
		  HttpURLConnection connection = null;

		  System.out.println("Request Content:" + requestContent);
		  try {
			  //Create connection
			  URL url = new URL(targetURL);
			  connection = (HttpURLConnection) url.openConnection();
			  connection.setRequestMethod("POST");
			  connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			  connection.setRequestProperty("Content-Length", 
					  Integer.toString(requestContent.getBytes().length));
			  connection.setRequestProperty("Content-Language", "en-US");  
			  connection.setUseCaches(false);
			  connection.setDoOutput(true);
			  //Send request
			  DataOutputStream wr = new DataOutputStream (
					  connection.getOutputStream());
			  wr.writeBytes(requestContent);
			  wr.close();

			  //Get Response  
			  InputStream is = connection.getInputStream();
			  BufferedReader rd = new BufferedReader(new InputStreamReader(is));
			  StringBuilder response = new StringBuilder(); // or StringBuffer if Java version 5+
			  String line;
			  while ((line = rd.readLine()) != null) {
				  response.append(line);
			  }
			  rd.close();
			  return response.toString();
		  } catch (Exception e) {
			  e.printStackTrace();
			  return null;
		  } finally {
			  if (connection != null) {
				  connection.disconnect();
			  }
		  }
		}
	
	
	/**
	 * @param args 
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		
		String cloud = System.getProperty("cloud") != null? System.getProperty("cloud"):"AWS";
		String location = System.getProperty("location") != null? System.getProperty("location") : "us-east-1";
		long id = ThreadLocalRandom.current().nextLong();
		String url = System.getProperty("url") != null? System.getProperty("url"):"localhost";
		
		String targetUrl = "http://"+url+"/";
		JSONObject json = new JSONObject();
		json.put("Cloud", cloud);
		json.put("Location", location);
		json.put("Id", id);
		
		String response = executePost(targetUrl, json.toJSONString());
		System.out.println("Successfully send HTTP POST request with the content "+json.toJSONString()+
				" to the server http://"+url+"/, and receive the response "+response);
		assert(json.toJSONString().equals(response)):"The echoed back response is not the same as the request!";
	}
}
