package server;

import edu.umass.cs.nio.AbstractBytePacketDemultiplexer;
import edu.umass.cs.nio.MessageNIOTransport;
import edu.umass.cs.nio.nioutils.NIOHeader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author arun
 *
 * This class implements a simple echo server using non-blocking IO.
 */
public class SingleServer {
    public static final int DEFAULT_PORT = 2000;
    public static final String DEFAULT_ENCODING = "ISO-8859-1";

    protected static final Logger log = Logger.getLogger(SingleServer
            .class.getName());
    protected final MessageNIOTransport<String,String> clientMessenger;

    public SingleServer(InetSocketAddress isa, InetSocketAddress isaDB, String
            keyspace) throws IOException {
        this.clientMessenger = new
                MessageNIOTransport<String, String>(isa.getAddress(), isa.getPort(),
                new AbstractBytePacketDemultiplexer() {
                    @Override
                    public boolean handleMessage(byte[] bytes, NIOHeader nioHeader) {
                        handleMessageFromClient(bytes, nioHeader);
                        return true;
                    }
                });
    }

    // TODO: process bytes received from clients here
    protected void handleMessageFromClient(byte[] bytes, NIOHeader header) {
        // simple echo server
        try {
            log.log(Level.INFO, "{0} received message from {1}", new Object[]
                    {this.clientMessenger.getListeningSocketAddress(), header.sndr});
            this.clientMessenger.send(header.sndr, bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param args The first argument must be of the form [host:]port with an optional host name or IP.
     */
    public static InetSocketAddress getSocketAddress(String[] args) {
        return new InetSocketAddress(args.length>0 && args[0].contains(":")?args
                [0].replaceAll(":.*",""):"localhost",

                args.length>0?Integer.parseInt(args[0]
                        .replaceAll(".*:","")):DEFAULT_PORT);
    }

    public void close() {
        this.clientMessenger.stop();
    }

    public static void main(String[] args) throws IOException {
        new SingleServer(getSocketAddress(args), new InetSocketAddress
                ("localhost", 9042), "demo");
    };
}