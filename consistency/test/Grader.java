import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

import client.Client;
import client.MyDBClient;
import edu.umass.cs.nio.interfaces.NodeConfig;
import edu.umass.cs.nio.nioutils.NIOHeader;
import edu.umass.cs.nio.nioutils.NodeConfigUtils;
import edu.umass.cs.utils.DefaultTest;
import server.MyDBReplicatedServer;
import server.MyDBSingleServer;
import server.ReplicatedServer;
import server.SingleServer;

@FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
public class Grader extends DefaultTest {

    private static final String DEFAULT_KEYSPACE = "demo";
    private static final InetSocketAddress DEFAULT_SADDR = new InetSocketAddress
            ("localhost", 1999);
    private static final InetSocketAddress DEFAULT_DB_ADDR = new
            InetSocketAddress("localhost", 9042);
    private static final int NUM_SERVERS = 3;
    private static final int SLEEP = 1000;
    private static final String CONFIG_FILE = System.getProperty("config")
            != null ? System.getProperty("config") : "conf/servers.properties";

    private static Client client = null;
    private static SingleServer singleServer = null;
    private static SingleServer[] replicatedServers = null;
    private static Map<String, InetSocketAddress> serverMap = null;
    private static String[] servers = null;
    private static Cluster cluster;
    private static Session session = (cluster = Cluster.builder().addContactPoint
            (DEFAULT_SADDR
                    .getHostName()).build()).connect(DEFAULT_KEYSPACE);

    private final static String DEFAULT_TABLE_NAME = "grade";

    private static NodeConfig<String> nodeConfigServer;

    private static final boolean GRADING_MODE = true;

    private static final int NUM_REQS = 100;

    private static final int SLEEP_RATIO = 10;

    @BeforeClass
    public static void setup() throws IOException {
        // setup single server
        singleServer = GRADING_MODE ? new MyDBSingleServer(DEFAULT_SADDR,
                DEFAULT_DB_ADDR, DEFAULT_KEYSPACE) :
                (SingleServer) getInstance(getConstructor("server" +
                                ".AVDBSingleServer",
                        InetSocketAddress.class, InetSocketAddress.class,
                        String.class), DEFAULT_SADDR, DEFAULT_DB_ADDR,
                        DEFAULT_KEYSPACE);
        nodeConfigServer = NodeConfigUtils.getNodeConfigFromFile
                (CONFIG_FILE, ReplicatedServer.SERVER_PREFIX, ReplicatedServer
                        .SERVER_PORT_OFFSET);

        // setup client
        NodeConfig<String> nodeConfigClient = NodeConfigUtils.getNodeConfigFromFile
                (CONFIG_FILE, ReplicatedServer.SERVER_PREFIX);
        client = GRADING_MODE ? new MyDBClient(nodeConfigClient) :
                (Client) getInstance(getConstructor("client.AVDBClient",
                        NodeConfig.class),
                        nodeConfigClient);


        // setup replicated servers and sessions to test
        replicatedServers = new SingleServer[nodeConfigServer.getNodeIDs().size()];
        int i = 0;
        // create keyspaces if not exists
        for (String node : nodeConfigServer.getNodeIDs()) {
            session.execute("create keyspace if not exists " + node + " with replication={'class':'SimpleStrategy', 'replication_factor' : '1'};");
        }

        for (String node : nodeConfigServer.getNodeIDs()) {
            replicatedServers[i++] = GRADING_MODE ? new MyDBReplicatedServer
                    (nodeConfigServer, node, DEFAULT_DB_ADDR) :

                    (SingleServer) getInstance(getConstructor("server" +
                                    ".AVDBReplicatedServer", NodeConfig.class,
                            String
                                    .class, InetSocketAddress.class),
                            nodeConfigServer, node, DEFAULT_DB_ADDR);
        }

        // setup frequently used information
        i = 0;
        servers = new String[nodeConfigServer.getNodeIDs().size()];
        for (String node : nodeConfigServer.getNodeIDs())
            servers[i++] = node;
        serverMap = new HashMap<String, InetSocketAddress>();
        for (String node : nodeConfigClient.getNodeIDs())
            serverMap.put(node, new InetSocketAddress(nodeConfigClient
                    .getNodeAddress
                            (node), nodeConfigClient.getNodePort(node)));

    }

    /**
     * This test tests a simple default DB command expected to always succeed.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void test01_DefaultAsync() throws IOException,
            InterruptedException {
        client.send(DEFAULT_SADDR, "select table_name from system_schema" +
                ".tables");
    }

    /**
     * Tests that a table is indeed being created successfully.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void test02_Single_CreateTable_Async() throws IOException,
            InterruptedException {
        testCreateTable(true, true);
    }


    @Test
    public void test03_InsertRecords_Async() throws IOException, InterruptedException {
        for (int i = 0; i < 10; i++) {
            send("insert into " + TABLE + " (ssn, firstname, lastname) " +
                    "values (" + (int) (Math.random() * Integer.MAX_VALUE) + ", '" +
                    "John" + i + "', '" + "Smith" + i + "')", true);
        }
        Thread.sleep(SLEEP);
    }

    @Test
    public void test04_DeleteRecords_Async() throws IOException, InterruptedException {
        send("truncate users", true);
        Thread.sleep(SLEEP);
        ResultSet resultSet = session.execute("select count(*) from " + TABLE);
        Assert.assertTrue(!resultSet.isExhausted());
        Assert.assertEquals(0, resultSet.one().getLong(0));
    }

    @Test
    public void test05_CreateTable_Sync() throws IOException, InterruptedException {
        testCreateTable(true, false);
    }

    /**
     * Create tables on all keyspaces.
     * Table should always exist after this test.
     * This test should always succeed, it is irrelevant to the total order implementation.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void test10_CreateTables() throws IOException, InterruptedException {
        for (String node : servers) {
            // create default table, node is the keypsace name
            session.execute(getCreateTableWithList(DEFAULT_TABLE_NAME, node));
            Thread.sleep(SLEEP);
        }

        for (String node : servers) {
            verifyTableExists(DEFAULT_TABLE_NAME, node, true);
        }

    }

    /**
     * Select a single server and send all SQL queries to the selected server.
     * Then verify the results on all replicas to see whether they are consistent.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void test11_UpdateRecord_SingleServer() throws IOException, InterruptedException {
        // generate a random key for this test
        int key = ThreadLocalRandom.current().nextInt();

        String selected = servers[0];
        // insert a record first with an empty list
        client.send(serverMap.get(selected), insertRecordIntoTableCmd(key, DEFAULT_TABLE_NAME));
        Thread.sleep(SLEEP);

        for (int i = 0; i < servers.length; i++) {
            client.send(serverMap.get(selected), updateRecordOfTableCmd(key, DEFAULT_TABLE_NAME));
            Thread.sleep(SLEEP);
        }

        verifyOrderConsistent(DEFAULT_TABLE_NAME, key);
    }

    /**
     * Send a simple SQL query to every server in a round robin manner.
     * Then verify the results in all replicas to see whether they are consistent.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @Test
    public void test12_UpdateRecord_AllServer() throws IOException, InterruptedException {
        // generate a random key for this test
        int key = ThreadLocalRandom.current().nextInt();

        // insert a record first with an empty list, it doesn't matter which server we use, it should be consistent
        client.send(serverMap.get(servers[0]), insertRecordIntoTableCmd(key, DEFAULT_TABLE_NAME));
        Thread.sleep(SLEEP);

        for (String node : servers) {
            client.send(serverMap.get(node), updateRecordOfTableCmd(key, DEFAULT_TABLE_NAME));
            Thread.sleep(SLEEP);
        }

        verifyOrderConsistent(DEFAULT_TABLE_NAME, key);
    }

    /**
     * Send each SQL query to a random server.
     * Then verify the results in all replicas to see whether they are consistent.
     *
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void test13_UpdateRecord_RandomServer() throws InterruptedException, IOException {
        // generate a random key for this test
        int key = ThreadLocalRandom.current().nextInt();

        // insert a record first with an empty list, it doesn't matter which server we use, it should be consistent
        client.send(serverMap.get(servers[0]), insertRecordIntoTableCmd(key, DEFAULT_TABLE_NAME));
        Thread.sleep(SLEEP);

        for (int i = 0; i < servers.length; i++) {
            String node = servers[ThreadLocalRandom.current().nextInt(0, servers.length)];
            client.send(serverMap.get(node), updateRecordOfTableCmd(key, DEFAULT_TABLE_NAME));
            Thread.sleep(SLEEP);
        }

        verifyOrderConsistent(DEFAULT_TABLE_NAME, key);
    }


    /**
     * This test is the same as test13, but it will send update request faster than test13, as it only sleeps 10ms
     *
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void test14_UpdateRecordFaster_RandomServer() throws InterruptedException, IOException {
        // generate a random key for this test
        int key = ThreadLocalRandom.current().nextInt();

        // insert a record first with an empty list, it doesn't matter which server we use, it should be consistent
        client.send(serverMap.get(servers[0]), insertRecordIntoTableCmd(key, DEFAULT_TABLE_NAME));
        // this sleep is to guarantee that the record has been created
        Thread.sleep(SLEEP);

        for (int i = 0; i < servers.length; i++) {
            String node = servers[ThreadLocalRandom.current().nextInt(0, servers.length)];
            client.send(serverMap.get(node), updateRecordOfTableCmd(key, DEFAULT_TABLE_NAME));
            // we just sleep 10 milliseconds this time
            Thread.sleep(10);
        }

        Thread.sleep(SLEEP);
        verifyOrderConsistent(DEFAULT_TABLE_NAME, key);
    }


    /**
     * This test is also the same as test13, but it will send update request much faster than test13, as it only sleeps 1ms
     *
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void test15_UpdateRecordMuchFaster_RandomServer() throws InterruptedException, IOException {
        // generate a random key for this test
        int key = ThreadLocalRandom.current().nextInt();

        // insert a record first with an empty list, it doesn't matter which server we use, it should be consistent
        client.send(serverMap.get(servers[0]), insertRecordIntoTableCmd(key, DEFAULT_TABLE_NAME));
        // this sleep is to guarantee that the record has been created
        Thread.sleep(SLEEP);

        for (int i = 0; i < servers.length; i++) {
            String node = servers[ThreadLocalRandom.current().nextInt(0, servers.length)];
            client.send(serverMap.get(node), updateRecordOfTableCmd(key, DEFAULT_TABLE_NAME));
            // we just sleep 10 milliseconds this time
            Thread.sleep(1);
        }
        Thread.sleep(SLEEP);

        verifyOrderConsistent(DEFAULT_TABLE_NAME, key);
    }

    /**
     * This test will not sleep and send more requests (i.e., 10)
     *
     * @throws InterruptedException
     * @throws IOException
     */
    @Test
    public void test16_UpdateRecordFastest_RandomServer() throws InterruptedException, IOException {
        // generate a random key for this test
        int key = ThreadLocalRandom.current().nextInt();

        // insert a record first with an empty list, it doesn't matter which server we use, it should be consistent
        client.send(serverMap.get(servers[0]), insertRecordIntoTableCmd(key, DEFAULT_TABLE_NAME));
        // this sleep is to guarantee that the record has been created
        Thread.sleep(SLEEP);

        for (int i = 0; i < NUM_REQS; i++) {
            String node = servers[ThreadLocalRandom.current().nextInt(0, servers.length)];
            client.send(serverMap.get(node), updateRecordOfTableCmd(key, DEFAULT_TABLE_NAME));

        }

        Thread.sleep(SLEEP * NUM_REQS / SLEEP_RATIO);

        verifyOrderConsistent(DEFAULT_TABLE_NAME, key);
    }


    /**
     * Clean up the default table
     *
     * @throws InterruptedException
     */
    @Test
    public void test19_DropTables() throws InterruptedException {
        for (String node : servers) {
            // clean up
            session.execute(getDropTableCmd(DEFAULT_TABLE_NAME, node));
        }
    }

    @Test
    public void test99_closeSession() {
        session.close();
    }

    private void testCreateTable(boolean single, boolean sleep) throws
            IOException, InterruptedException {
        if (sleep) testCreateTableSleep(single);
        else testCreateTableBlocking(single);
        verifyTableExists(TABLE, DEFAULT_KEYSPACE, true);

    }

    private void verifyTableExists(String table, String keyspace, boolean exists) {
        ResultSet resultSet = session.execute("select table_name from " +
                "system_schema.tables where keyspace_name='" + keyspace + "'");
        Assert.assertTrue(!resultSet.isExhausted());
        boolean match = false;
        for (Row row : resultSet)
            match = match || row.getString("table_name").equals(table);
        if (exists)
            Assert.assertTrue(match);
        else
            Assert.assertFalse(match);
    }

    private void verifyOrderConsistent(String table, int key) {
        String[] results = new String[servers.length];
        int i = 0;
        boolean nonEmpty = false;
        for (String node : servers) {
            ResultSet result = session.execute(readResultFromTableCmd(key, DEFAULT_TABLE_NAME, node));
            results[i] = "";
            for (Row row : result) {
                results[i] += row;
                nonEmpty = true;
            }
            i++;
        }
        i = 0;
        boolean match = true;
        for (String result : results) {
            if (!results[0].equals(result))
                match = false;
        }
        Assert.assertTrue(nonEmpty && match);
        for(i=0; i<results.length; i++)
            System.out.println(i+":"+results[i]);

    }

    private void testCreateTableSleep(boolean single) throws
            InterruptedException, IOException {
        send(getDropTableCmd(TABLE, DEFAULT_KEYSPACE), single);
        Thread.sleep(SLEEP);
        send(getCreateTableCmd(TABLE, DEFAULT_KEYSPACE), single);
        Thread.sleep(SLEEP);
    }


    ConcurrentHashMap<Long, String> outstanding = new ConcurrentHashMap<Long, String>();

    private void testCreateTableBlocking(boolean single) throws
            InterruptedException, IOException {
        waitResponse(callbackSend(DEFAULT_SADDR, getDropTableCmd(TABLE, DEFAULT_KEYSPACE)));
        waitResponse(callbackSend(DEFAULT_SADDR, getCreateTableCmd(TABLE, DEFAULT_KEYSPACE)));
    }

    private Long callbackSend(InetSocketAddress isa, String request) throws
            IOException {
        Long id = enqueueRequest(request);
        client.callbackSend(isa, request, new WaitCallback(id));
        return id;
    }

    private class WaitCallback implements Client.Callback {
        Long monitor; // both id and monitor

        WaitCallback(Long monitor) {
            this.monitor = monitor;
        }

        @Override
        public void handleResponse(byte[] bytes, NIOHeader header) {
            synchronized (this.monitor) {
                outstanding.remove(monitor);
                this.monitor.notify();
            }
        }
    }

    private long reqnum = 0;

    private long enqueue() {
        synchronized (outstanding) {
            return reqnum++;
        }
    }

    private long enqueueRequest(String request) {
        long id = enqueue();
        outstanding.put(id, request);
        return id;
    }

    private void waitResponse(Long id) {
        synchronized (id) {
            while (outstanding.containsKey(id))
                try {
                    id.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
        }
    }


    private static final void send(String cmd, boolean single) throws
            IOException {
        client.send(single ? DEFAULT_SADDR :
                        serverMap.get(servers[(int) (Math.random() * servers.length)]),
                cmd);
    }

    private static final String TABLE = "users";

    private static String getCreateTableCmd(String table, String keyspace) {
        return "create table if not exists " + keyspace + "." + table + " (age int, firstname " +
                "text, lastname text, ssn int, address text, hash bigint, " +
                "primary key (ssn))";
    }

    private static String getDropTableCmd(String table, String keyspace) {
        return "drop table if exists " + keyspace + "." + table;
    }

    private static String getCreateTableWithList(String table, String keyspace) {
        return "create table if not exists " + keyspace + "." + table + " (id int, events list<int>, primary key (id));";
    }

    private static String insertRecordIntoTableCmd(int key, String table) {
        return "insert into " + table + " (id, events) values (" + key + ", []);";
    }

    private static String updateRecordOfTableCmd(int key, String table) {
        return "update " + table + " SET events=events+[" + incrSeq() + "] where id=" + key + ";";
    }

    // This is only used to fetch the result from the table by session directly connected to cassandra
    private static String readResultFromTableCmd(int key, String table, String keyspace) {
        return "select events from " + keyspace + "." + table + " where id=" + key + ";";
    }

    private static long sequencer = 0;

    synchronized static long incrSeq() {
        return sequencer++;
    }

    @AfterClass
    public static void teardown() {
        if (replicatedServers != null)
            for (SingleServer s : replicatedServers)
                s.close();
        if (client != null) client.close();
        if (singleServer != null) singleServer.close();
        session.close();
        cluster.close();
    }

    private static Object getInstance(Constructor<?> constructor,
                                      Object... args) {
        try {
            return constructor.newInstance(args);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Constructor<?> getConstructor(String clazz, Class<?>... types) {
        try {
            Class<?> instance = Class.forName(clazz);
            return instance.getConstructor(types);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void main(String[] args) throws IOException {
        Result result = JUnitCore.runClasses(Grader.class);
        for (Failure failure : result.getFailures()) {
            System.out.println(failure.toString());
            failure.getException().printStackTrace();
        }
    }

}
