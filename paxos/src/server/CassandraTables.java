package server;

import java.util.Set;

import edu.umass.cs.gigapaxos.interfaces.Replicable;
import edu.umass.cs.gigapaxos.interfaces.Request;
import edu.umass.cs.gigapaxos.testing.TESTPaxosConfig;
import edu.umass.cs.nio.interfaces.IntegerPacketType;
import edu.umass.cs.nio.nioutils.NIOHeader;
import edu.umass.cs.reconfiguration.reconfigurationutils.RequestParseException;
import edu.umass.cs.utils.Config;

public class CassandraTables implements Replicable {

	/**
	 * CQLSH is path to canssandra cqlsh. To change this path, don't change
	 * the default value below but instead use the system property
	 * -Dcqlsh=/path/to/cqlsh as an argument to gpServer.sh.
	 *
	 */
	private final static String CQLSH = System.getProperty("cqlsh")!=null ?
			System.getProperty("cqlsh")
			: "/home/ubuntu/apache-cassandra-3.11.1/";
	

	/**
	 * TODO: implement this checkpoint method
	 */
	@Override
	public String checkpoint(String name) {
		return null;
	}

	/**
	 * TODO: implement this execute method
	 */
	@Override
	public boolean execute(Request request) {		
		// execute request here
		return false;
	}
	
	/**
	 * no need to implement this method, implement the execute above
	 */
	@Override
	public boolean execute(Request request, boolean doNotReplyToClient) {
		// execute request without replying back to client

		// identical to above unless app manages its own messaging
		return this.execute(request);
	}

	/**
	 * TODO: implement this restore method
	 */
	@Override
	public boolean restore(String name, String state) {
		return false;
	}

	/**
	 * No need to implement unless you want to implement your own
	 * packet types (optional).
	 */
	@Override
	public Request getRequest(String req) throws RequestParseException {
		return null;
	}

	/**
	 * No need to implement unless you want to implement your own
	 * packet types (optional).
	 */
	@Override
	public Set<IntegerPacketType> getRequestTypes() {
		return null;
	}

}
