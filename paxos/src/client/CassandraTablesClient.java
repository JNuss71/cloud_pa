package client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.umass.cs.utils.Util;
import org.json.JSONException;

import edu.umass.cs.gigapaxos.PaxosClientAsync;
import edu.umass.cs.gigapaxos.PaxosConfig;
import edu.umass.cs.gigapaxos.interfaces.ClientRequest;
import edu.umass.cs.gigapaxos.interfaces.Request;
import edu.umass.cs.gigapaxos.interfaces.RequestCallback;
import edu.umass.cs.utils.Config;

/**
 * Use PaxosClientAsync or ReconfigurableClientAsync as in the tutorial
 * examples.
 */
public class CassandraTablesClient {

	/**
	 * default keyspace
	 */
	public final static String DEFAULT_KEYSPACE = System.getProperty("keyspace")
			!=null ?
			System.getProperty("keyspace")
			: "demo";;

	/**
	 * TODO
	 * @param args
	 */
	public static void main(String[] args) {
		InetSocketAddress isa = args.length>0 ? Util
				.getInetSocketAddressFromString(args[0]) : null;
	}

}
