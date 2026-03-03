package org.example.v4;

import org.w3c.dom.Node;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class Server {
    static int id;
    static int port;
    static Map<Integer, PeerConnection> nodes = new ConcurrentHashMap<>();
    static Set<NodeData> nodeData = new HashSet<>();
    static BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
    static ServerSocket serverSocket;


    public static void main(String[] args) {
        Properties props = new Properties();

        int nodeId;
        int nodePort;
        Set<NodeData> nodeData = new HashSet<>();

        String fileName = "/server-" + args[0] + ".properties";

        try (InputStream in = Server.class.getResourceAsStream(fileName)) {
            props.load(in);

            nodeId = Integer.parseInt(props.getProperty("node.id"));
            nodePort = Integer.parseInt(props.getProperty("node.port"));
            String clusterId = props.getProperty("cluster.ids");
            String clusterPort = props.getProperty("cluster.nodes");

            String[] ids = clusterId.split(",");
            String[] ports = clusterPort.split(",");

            for (int i = 0; i < ids.length; i++) {
                NodeData e = new NodeData(Integer.parseInt(ids[i]), Integer.parseInt(ports[i]));
                nodeData.add(e);
                System.out.println(e);
            }

        } catch (IOException e) {
            throw new RuntimeException("WRONG CONFIG FILE!",e);
        }

        Server server = new Server();
        id = nodeId;
        port = nodePort;
        Server.nodeData = nodeData;
        server.start();
    }

    private void start() {

        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        new Thread(new ConnectionInitiator()).start();
        new Thread(new ConnectionListener()).start();
        new Thread(new Sender()).start();
        new Thread(new Receiver()).start();
    }

    static int exchangeIds(Socket accept) throws IOException {
        accept.setSoTimeout(5000);

        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(accept.getOutputStream()));
        bufferedWriter.write(Server.id + "\n");
        bufferedWriter.flush();

        String s = new BufferedReader(new InputStreamReader(accept.getInputStream())).readLine();

        return Integer.parseInt(s);
    }
}
