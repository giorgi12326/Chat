package org.example.v4;

import org.example.v4.dto.Message;
import org.example.v4.dto.NodeData;
import org.example.v4.dto.PeerConnection;
import org.example.v4.dto.State;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static org.example.v4.dto.State.*;

/**
 * Only one leader at the time
 *    you need the majority of nodes, it is guaranteed that one node can only vote once, and we also use SET on recipient side.
 * if no leader one will be elected one will be elected
 *    followers transition into candidate state and randomly election times out and increase their term and send VOTE_REQUESTS, if timeout window allows candidate to hav highest term for amount of time it takes to send and recieve notes and send out heartbeat
 *
 */

public class Server {
    static int id;
    static int port;
    static Set<NodeData> nodeData = new HashSet<>();
    static Map<Integer, PeerConnection> nodes = new ConcurrentHashMap<>();
    static BlockingQueue<Message> senderQueue = new LinkedBlockingQueue<>();
    static BlockingQueue<String> inboundQueue = new LinkedBlockingQueue<>();
    static ServerSocket serverSocket;
    Random random = new Random();

    volatile static long heartBeatTimer = System.currentTimeMillis();
    volatile static long lastElectionTimer = System.currentTimeMillis();

    int currentLeader = -1;
    static State state = CANDIDATE;
    int currentTerm;
    int votedFor = -1;
    Set<Integer> votesReceived = new HashSet<>();

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


        new Thread(()->{
            while(true){
                double v1 = 500 + (random.nextDouble() * 1000);
                if(state == LEADER && System.currentTimeMillis() - Server.heartBeatTimer > v1){//TODO
                    try {
                        inboundQueue.put("HEARTBEAT_TIMEOUT");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    Server.heartBeatTimer = System.currentTimeMillis();
                }
                double v2 = 5000 + (random.nextDouble() * 8000);
                if(state != LEADER && System.currentTimeMillis() - lastElectionTimer > v2){//TODO
                    try {
                        inboundQueue.put("ELECTION_TIMEOUT");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    lastElectionTimer = System.currentTimeMillis();
                }
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
        leaderElection();
    }
    
    public void leaderElection(){
        new Thread(()-> {
            while(true) {
                try {
                    String take = inboundQueue.take();

                    if (take.equals("HEARTBEAT_TIMEOUT") && state == LEADER) {
                        heartBeatTimer = System.currentTimeMillis();
                        senderQueue.put(new Message("HEARTBEAT|nodeId=" + id + "|currentTerm=" + currentTerm));
                    }
                    else if (take.equals("ELECTION_TIMEOUT") && state != LEADER) {
                        Server.lastElectionTimer = System.currentTimeMillis();
                        state = CANDIDATE;
                        currentTerm++;
                        votedFor = id;
                        votesReceived.add(id);
                        checkIfQuorumAndUpdate();

                        senderQueue.put(new Message("VOTE_REQUEST|nodeId=" + id + "|currentTerm=" + currentTerm));

                    } else {
                        String[] split = take.split("\\|");
                        if(split[0].equals("HEARTBEAT")){
                            int nodeId = Integer.parseInt(split[1].split("=")[1]);
                            int currentTerm = Integer.parseInt(split[2].split("=")[1]);
                            if(currentTerm > this.currentTerm){
                                this.currentTerm = currentTerm;
                                if(state != FOLLOWER)
                                    System.out.println("STEPPING DOWNNN BECAUSE OF OTHER LEADER!");
                                votedFor = -1;
                                votesReceived.clear();
                            }
                            if(currentTerm == this.currentTerm) {
                                state = FOLLOWER;
                                currentLeader = nodeId;
                            }
                            lastElectionTimer = System.currentTimeMillis();
                        }

                        if (split[0].equals("VOTE_REQUEST")) {
                            int nodeId = Integer.parseInt(split[1].split("=")[1]);
                            int currentTerm = Integer.parseInt(split[2].split("=")[1]);
                            if (currentTerm > this.currentTerm) {// only vote for higher one because  if equals it means there was a leader in that term already and candidate asking for vote was in old term and now it increased it ORR its candidate election time outing at the same time
                                System.out.println("BECAME FOLLOWER!");
                                state = FOLLOWER;
                                this.currentTerm = currentTerm;
                                votedFor = -1;
                                votesReceived.clear();
                            }
                            if (currentTerm == this.currentTerm && (votedFor == -1 || votedFor == nodeId))
                                senderQueue.put(new Message(nodeId,"VOTE_RESPONSE|nodeId=" + id + "|currentTerm=" + currentTerm + "|true"));
                            else
                                senderQueue.put(new Message(nodeId,"VOTE_RESPONSE|nodeId=" + id + "|currentTerm=" + currentTerm + "|false"));
                        }

                        if (split[0].equals("VOTE_RESPONSE")) {
                            int nodeId = Integer.parseInt(split[1].split("=")[1]);
                            int currentTerm = Integer.parseInt(split[2].split("=")[1]);
                            boolean granted = split[3].equals("true");
                            if (state == CANDIDATE && currentTerm == this.currentTerm && granted) {
                                votesReceived.add(nodeId);
                                checkIfQuorumAndUpdate();
                            }
                            else if(currentTerm > this.currentTerm){
                                this.currentTerm = currentTerm;
                                votedFor = -1;
                                votesReceived.clear();
                                state = FOLLOWER;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    private void checkIfQuorumAndUpdate() throws InterruptedException {
        if (votesReceived.size() >= (nodes.size() + 2) / 2) {
            state = LEADER;
            currentLeader = id;
            System.out.println("BECAME LEADER");

            senderQueue.put(new Message("HEARTBEAT|nodeId=" + id + "|currentTerm=" + this.currentTerm));
        }
    }

    static int exchangeIds(Socket accept) throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(accept.getOutputStream()));
        bufferedWriter.write(Server.id + "\n");
        bufferedWriter.flush();

        String s = new BufferedReader(new InputStreamReader(accept.getInputStream())).readLine();

        return Integer.parseInt(s);
    }
}
