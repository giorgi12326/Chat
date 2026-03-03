package org.example.v4;

import org.w3c.dom.Node;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    int id;
    Map<Integer, PeerConnection> nodes = new ConcurrentHashMap<>();
    private int port;
    Set<NodeData> nodeData = new HashSet<>();

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
        server.id = nodeId;
        server.port = nodePort;
        server.nodeData = nodeData;
        server.start();
    }

    private void start() {

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        if(port != 9002)
            connectionListener(serverSocket);
        connectionInitiator();

        heartbeatSender();
        heartbeatListener();

    }

    private void heartbeatListener() {
        new Thread(()->{
            while(true) {
                Iterator<Map.Entry<Integer, PeerConnection>> iterator = nodes.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Integer, PeerConnection> entry = iterator.next();
                    try {
                        BufferedReader bufferedWriter = entry.getValue().reader;
                        String s = bufferedWriter.readLine();
                        if(s == null){
                            System.out.println("--------------------------------- NULLED: lost connection to" + entry.getKey() + " ---------------------------------");
                            entry.getValue().close();
                            iterator.remove();
                        }

                        System.out.println("recieved: " + s);
                    } catch (IOException e) {
                        System.out.println("--------------------------------- LISTENER: lost connection to" + entry.getKey() + " ---------------------------------");
                        entry.getValue().close();
                        iterator.remove();
                    }
                }
            }
        }).start();
    }

    private void heartbeatSender() {
        new Thread(()->{
            while(true) {
                Iterator<Map.Entry<Integer, PeerConnection>> iterator = nodes.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Integer, PeerConnection> entry = iterator.next();
                    try {
                        BufferedWriter bufferedWriter = entry.getValue().writer;
                        bufferedWriter.write("ping from " + port + "\n");
                        bufferedWriter.flush();
                        System.out.println("sent ping to " + entry.getKey());
                    } catch (IOException e) {
                        System.out.println("--------------------------------- SENDER: lost connection to" + entry.getKey() + " ---------------------------------");
                        entry.getValue().close();
                        iterator.remove();
                    }
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

        }).start();
    }

    private void connectionInitiator() {
        new Thread(()-> {
            while (true) {
                for(NodeData data: nodeData){
                    if (this.id > data.id && !nodes.containsKey(data.id)) {
                        try {
                            Socket socket = new Socket(InetAddress.getLocalHost(), data.port);
                            int s = exchangeIds(socket);
                            nodes.put(s, new PeerConnection(socket));

                            System.out.println("================================= connected myself to node with id: " + data.id + " =================================");
                        }
                        catch (SocketTimeoutException e) {
                            System.out.println("Peer did not send ID in time!");
                        }
                        catch (IOException e) {
                            System.out.println("couldn't connect to " + data.id);
                        }
                    }
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    private void connectionListener(ServerSocket serverSocket) {
        new Thread(()-> {
            System.out.println("waiting for connections");
            while (true) {
                try {
                    Socket accept = serverSocket.accept();
                    int s = exchangeIds(accept);
                    nodes.put(s, new PeerConnection(accept));
                    System.out.println("================================= connection accepted to node with id: " + s + " =================================");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }).start();
    }

    private int exchangeIds(Socket accept) throws IOException {
        accept.setSoTimeout(5000);

        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(accept.getOutputStream()));
        bufferedWriter.write(id + "\n");
        bufferedWriter.flush();

        String s = new BufferedReader(new InputStreamReader(accept.getInputStream())).readLine();

        return Integer.parseInt(s);
    }
}
