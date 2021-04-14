package server;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import edu.umass.cs.nio.*;
import edu.umass.cs.nio.interfaces.NodeConfig;
import edu.umass.cs.nio.nioutils.NIOHeader;
import edu.umass.cs.nio.nioutils.NodeConfigUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;

import java.util.*;

/**
 * @author arun
 *
 * This class extends {@link SingleServer} to implement a simple echo +
 * lazy-relay replicated server.
 */
public class MyDBReplicatedServer extends SingleServer {
    public static final String SERVER_PREFIX = "server.";
    public static final int SERVER_PORT_OFFSET = 1000;

    protected final String myID;
    protected final MessageNIOTransport<String,String> serverMessenger;
    protected int counter;
    protected int coordinator_counter;
    protected String coordinator;
    protected Cluster cluster;
    protected Session session;
    protected PriorityQueue<JSONObject> map;
    protected ConcurrentHashMap<Integer, NIOHeader> header_map = new ConcurrentHashMap<Integer, NIOHeader>();
    protected int header_count = 0;

    /**
     * @param nodeConfig consists of server names and server-facing addresses
     * @param myID is my node name as well as the keyspace name
     * @param isaDB is the socket address of the database
     * @throws IOException
     */

    public MyDBReplicatedServer(NodeConfig<String> nodeConfig, String myID,
                            InetSocketAddress isaDB) throws IOException {
        super(new InetSocketAddress(nodeConfig.getNodeAddress(myID),
                nodeConfig.getNodePort(myID)-SERVER_PORT_OFFSET), isaDB, myID);
        this.counter = 0;
        this.coordinator_counter = 0;
        this.myID = myID;
        map = new PriorityQueue<JSONObject>(11, new JSONComparator());
        this.serverMessenger = new
                MessageNIOTransport<String, String>(myID, nodeConfig,
                new
                        AbstractBytePacketDemultiplexer() {
                            @Override
                            public boolean handleMessage(byte[] bytes, NIOHeader nioHeader) {
                                handleMessageFromServer(bytes, nioHeader);
                                return true;
                            }
                        }, true);
        String min_node = myID;
        for (String node : nodeConfig.getNodeIDs())
            if (nodeConfig.getNodeAddress(node).hashCode() < nodeConfig.getNodeAddress(min_node).hashCode() ||
                    (nodeConfig.getNodeAddress(node).equals(nodeConfig.getNodeAddress(min_node)) &&
                            nodeConfig.getNodePort(node) < nodeConfig.getNodePort(min_node))){
                min_node = node;
            }

        this.coordinator = min_node;

        this.session = (cluster=Cluster.builder().addContactPoint("127.0.0.1")
                .build()).connect(myID);

        log.log(Level.INFO, "Server {0} started on {1}", new Object[]{this
                .myID, this.clientMessenger.getListeningSocketAddress()});
    }

    // TODO: process bytes received from clients here
    @Override
    protected void handleMessageFromClient(byte[] bytes, NIOHeader header) {
        // echo to client
        try {


            ArrayList<String> write_commands = new ArrayList<>();
            write_commands.addAll(java.util.Arrays.asList("create", "insert", "update", "drop", "truncate"));
            String request = new String(bytes, SingleServer.DEFAULT_ENCODING);
            String command = request.split(" ")[0];
            if (write_commands.contains(command)) {
                // Write operations needs to be sent to coordinator
                if (this.coordinator.equals(this.myID)) {

                    try {
                        header_map.put(++header_count, header);
                        JSONObject json = new JSONObject().put("COUNTER",
                                ++coordinator_counter).put("REQUEST", request).put("HEADERNUM", header_count).put("NODE", myID);
                        for (String node : this.serverMessenger.getNodeConfig().getNodeIDs())
                            try {
                                this.serverMessenger.send(node, json.toString().getBytes(SingleServer.DEFAULT_ENCODING));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                    } catch (JSONException e) {
                        //e.printStackTrace();toString
                    }
                    // relay to other servers
                } else {
                    try {
                        header_map.put(++header_count, header);
                        JSONObject json = new JSONObject().put("COUNTER",
                                -1).put("REQUEST", request).put("HEADERNUM", header_count).put("NODE", myID);
                        try {
                            this.serverMessenger.send(coordinator, json.toString().getBytes(SingleServer.DEFAULT_ENCODING));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (JSONException e) {
                        //e.printStackTrace();toString
                    }
                }
            }
            else {
                // Read operations executed locally
                session.execute(request);
                super.handleMessageFromClient(request.getBytes(SingleServer.DEFAULT_ENCODING), header);
            }

        } catch (IOException e) {
        e.printStackTrace();
        }
}




    // TODO: process bytes received from servers here
    protected void handleMessageFromServer(byte[] bytes, NIOHeader header) {
        log.log(Level.INFO, "{0} received relayed message from {1}",
                new Object[]{this.myID, header.sndr}); // simply log
        int request_counter = 0;
        try{
            String request = new String(bytes, SingleServer.DEFAULT_ENCODING);
            JSONObject json = null;
            try {
                json = new JSONObject(request);
                request_counter = json.getInt("COUNTER");
            } catch (JSONException e) {
                //e.printStackTrace();
            }
            if(this.coordinator == this.myID) {
                try {
                    if (request_counter == -1) {
                        JSONObject json_send = new JSONObject().put("COUNTER",
                                ++coordinator_counter).put("REQUEST", json.getString("REQUEST")).put("HEADERNUM", json.getInt("HEADERNUM")).put("NODE", json.getString("NODE"));
                        for (String node : this.serverMessenger.getNodeConfig().getNodeIDs())
                            try {
                                this.serverMessenger.send(node, json_send.toString().getBytes(SingleServer.DEFAULT_ENCODING));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        return;
                    }
                } catch (JSONException e) {
                    return;
                    //e.printStackTrace();toString
                }
            }

            try {
                map.add(json);
                while (map.peek() != null && map.peek().getInt("COUNTER") == counter + 1) {
                    JSONObject run_json = map.poll();
                    String run_request = run_json.getString("REQUEST");
                    session.execute(run_request);
                    int run_header = run_json.getInt("HEADERNUM");
                    String node = run_json.getString("NODE");
                    session.execute(run_request);
                    if (node == myID)
                        super.handleMessageFromClient(request.getBytes(SingleServer.DEFAULT_ENCODING), header_map.get(run_header));
                    counter++;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }



        }
        catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void close() {
        super.close();
        this.serverMessenger.stop();
    }

    /**
     *
     * @param args The first argument is the properties file and the rest are
     *            names of servers to be started.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if(args.length>1)
            for(int i=1; i<args.length; i++)
                new ReplicatedServer(NodeConfigUtils.getNodeConfigFromFile(args[0],
                        SERVER_PREFIX, SERVER_PORT_OFFSET), args[i].trim(),
                        new InetSocketAddress("localhost", 9042));
        else log.info("Incorrect number of arguments; not starting any server");
    }
}

class JSONComparator implements Comparator<JSONObject> {

    // Overriding compare()method of Comparator
    // for descending order of cgpa
    public int compare(JSONObject j, JSONObject k) {
        try {
            if (j.getInt("COUNTER") < k.getInt("COUNTER"))
                return -1;
            else if (j.getInt("COUNTER") > k.getInt("COUNTER"))
                return 1;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }
}