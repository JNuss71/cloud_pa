package client;

import edu.umass.cs.nio.interfaces.NodeConfig;
import edu.umass.cs.nio.nioutils.NIOHeader;
import org.json.JSONException;
import org.json.JSONObject;
import server.SingleServer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class should implement your DB client.
 */
public class MyDBClient extends Client {
    private NodeConfig<String> nodeConfig= null;
    long reqnum = 0;
    HashMap<Long,JSONObject> outstanding = new HashMap<Long,JSONObject>();
    ConcurrentHashMap<Long, Callback> callbacks = new ConcurrentHashMap<Long,
            Callback>();

    public MyDBClient() throws IOException {
    }

    public MyDBClient(NodeConfig<String> nodeConfig) throws IOException {
        super();
        this.nodeConfig = nodeConfig;
    }

    // TODO: process responses received from server
    protected void handleResponse(byte[] bytes, NIOHeader header) {
        // expect echo reply here
        try {
            JSONObject response = new JSONObject(new String(bytes, SingleServer
                    .DEFAULT_ENCODING));
            Callback callback = callbacks.remove(response.getLong(Keys
                    .REQNUM.toString()));
            if(callback!=null)
                callback.handleResponse(bytes, header);

        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            //e.printStackTrace();
        }
    }

    public static enum Keys {
        REQNUM, TYPE, REQUEST, RESPONSE;
    }
    private synchronized long enqueue(String request) {
        return reqnum++;
    }

    public void callbackSend(InetSocketAddress isa, String request,
                             Callback callback) throws IOException {
        try {
            JSONObject json = new JSONObject().put(Keys.REQNUM.toString(),
                    enqueue(request)).put(Keys.REQUEST.toString(), request);
            this.callbacks.put(json.getLong(Keys.REQNUM.toString()), callback);
            this.send(isa, json.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}