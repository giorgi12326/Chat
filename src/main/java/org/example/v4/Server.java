package org.example.v4;

import org.example.v4.dto.*;

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
    public static boolean logSent = false;
    private static boolean logReceived = false;
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

    volatile int currentLeader = -1;
    volatile static State state = FOLLOWER;
    volatile int currentTerm;
    volatile int votedFor = -1;
    volatile Set<Integer> votesReceived = new HashSet<>();

    volatile List<LogEntry> log = new ArrayList<>();
    volatile int[] sentLength = new int[3];

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
        startClientListener();

        new Thread(()->{
            while(true){
                if(state == LEADER && System.currentTimeMillis() - Server.heartBeatTimer > 100 + (id + 1) * 60L){//TODO
                    try {
                        inboundQueue.put("HEARTBEAT_TIMEOUT");
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    Server.heartBeatTimer = System.currentTimeMillis();
                }
                if(state != LEADER && System.currentTimeMillis() - lastElectionTimer >  1500 + (id + 1) * 60L){//TODO
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
                    if(Server.logReceived)
                        System.out.println("RECEIVED" + "(" + state  + ") " + take);
                    if ((take.equals("HEARTBEAT_TIMEOUT") || take.equals("BECAME_LEADER")) && state == LEADER) {
                        heartBeatTimer = System.currentTimeMillis();
                        for(NodeData node : nodeData) {
                            if(node.id != id)
                                replicateLog(node.id);
                            else System.out.println("IMPOSSIBLE?");
                        }
                    }
                    else if (take.equals("ELECTION_TIMEOUT") && state != LEADER) {
                        Server.lastElectionTimer = System.currentTimeMillis();
                        state = CANDIDATE;
                        currentTerm++;
                        votedFor = id;
                        votesReceived.add(id);
                        checkIfMajorityOfVotesAndUpdate();

                        System.out.println("BECAME CANDIDATE: " + currentTerm);
                        senderQueue.put(new Message("VOTE_REQUEST|nodeId=" + id + "|currentTerm=" + currentTerm +"|logLength=" + log.size() + "|lastTerm=" + (log.isEmpty() ? 0 : log.get(log.size()-1).term)));

                    } else {
                        String[] split = take.split("\\|");

                        if(split[0].equals("LogRequest")){
                            int nodeId = Integer.parseInt(split[1].split("=")[1]);
                            int currentTerm = Integer.parseInt(split[2].split("=")[1]);
                            int prefixLen = Integer.parseInt(split[3].split("=")[1]);
                            int prefixTerm = Integer.parseInt(split[4].split("=")[1]);
                            String[] split1 = split[5].split("=");

                            String suffix = "";
                            if(split1.length > 1)
                                suffix = split1[1];

                            if(currentTerm > this.currentTerm){
                                this.currentTerm = currentTerm;
                                votedFor = -1;
                                votesReceived.clear();
                            }
                            else if(currentTerm == this.currentTerm){
                                state = FOLLOWER;
                                currentLeader = nodeId;
                                System.out.println("BECAME FOLLOWER: " + currentTerm);
                            }

                            boolean logOk = (log.size() >= prefixLen) && (prefixLen == 0 || log.get(prefixLen-1).term == prefixTerm);
                            if(this.currentTerm == currentTerm && logOk){
                                appendEntries(prefixLen, computeSuffixEntries(suffix));
                                senderQueue.put(new Message(nodeId, "LogResponse|nodeId=" + id + "|currentTerm=" + currentTerm + "|ack=" + log.size() + "|success=" + true));
                            }
                            else
                                senderQueue.put(new Message(nodeId, "LogResponse|nodeId=" + id + "|currentTerm=" + currentTerm + "|ack=" + 0 + "|success=" + false));
                            lastElectionTimer = System.currentTimeMillis();
                        }
                        if(split[0].equals("LogResponse")){
                            int nodeId = Integer.parseInt(split[1].split("=")[1]);
                            int currentTerm = Integer.parseInt(split[2].split("=")[1]);
                            int ack = Integer.parseInt(split[3].split("=")[1]);
                            boolean success = Boolean.parseBoolean(split[4].split("=")[1]);

                            if(this.currentTerm == currentTerm && state == LEADER){
                                if(success)
                                    sentLength[nodeId] = ack;
                                else{
                                    if(sentLength[nodeId] > 0)
                                        sentLength[nodeId]--;
                                    replicateLog(nodeId);
                                }
                            }
                            lastElectionTimer = System.currentTimeMillis();
                        }

                        if (split[0].equals("VOTE_REQUEST")) {
                            int nodeId = Integer.parseInt(split[1].split("=")[1]);
                            int currentTerm = Integer.parseInt(split[2].split("=")[1]);
                            int cLogLength = Integer.parseInt(split[3].split("=")[1]);
                            int cLastTerm = Integer.parseInt(split[4].split("=")[1]);

                            int lastTerm = log.isEmpty() ? 0 : log.get(log.size()-1).term;

                            boolean logOk = cLastTerm > lastTerm || (cLastTerm == lastTerm && cLogLength >= log.size());

                            if (logOk && (votedFor == -1 || votedFor == nodeId))
                                senderQueue.put(new Message(nodeId,"VOTE_RESPONSE|nodeId=" + id + "|currentTerm=" + currentTerm + "|true"));
                        }

                        if (split[0].equals("VOTE_RESPONSE")) {
                            int nodeId = Integer.parseInt(split[1].split("=")[1]);
                            int currentTerm = Integer.parseInt(split[2].split("=")[1]);
                            boolean granted = split[3].equals("true");
                            if (state == CANDIDATE && currentTerm == this.currentTerm && granted) {
                                votesReceived.add(nodeId);
                                checkIfMajorityOfVotesAndUpdate();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    private void replicateLog(int nodeId) throws InterruptedException {
        int prefixLen = sentLength[nodeId];
        int prefixTerm = 0;
        if(prefixLen > 0)
            prefixTerm = log.get(prefixLen-1).term;

        StringBuilder computeSuffixFormat = getComputeSuffixFormat(prefixLen);
        senderQueue.put(new Message("LogRequest|nodeId=" + id + "|currentTerm=" + currentTerm + "|prefixLen=" + prefixLen + "|prefixTerm=" + prefixTerm + "|suffix=" + computeSuffixFormat));
    }

    private void appendEntries(int prefixLen, List<LogEntry> suffix) {
        int index = Math.min(log.size(), prefixLen + suffix.size()) - 1;
        if(!suffix.isEmpty() && log.size() > prefixLen && log.get(index).term != suffix.get(index - prefixLen).term){
            for(int i = log.size()-1; i >= prefixLen; i--){
                log.remove(i);
            }
        }
        if(prefixLen + suffix.size() > log.size()){
            for(int i = log.size() - prefixLen; i < suffix.size(); i++){
                log.add(suffix.get(i));
                System.out.println(log);
            }
            System.out.println("-----");

        }
    }

    private List<LogEntry> computeSuffixEntries(String suffix) {
        if(suffix.equals(""))
            return new ArrayList<>();
        List<LogEntry> result = new ArrayList<>();
        String[] entries = suffix.split(";");
        for (int i = 0; i < entries.length; i++) {
            String[] split = entries[i].split(",");
            int term = Integer.parseInt(split[0]);
            String message = split[1];
            result.add(new LogEntry(term, message));
        }
        return result;
    }

    private StringBuilder getComputeSuffixFormat(int prefixLen) {
        StringBuilder computeSuffixFormat = new StringBuilder();
        for (int i = prefixLen; i < log.size(); i++) {
            LogEntry entry = log.get(i);
            computeSuffixFormat.append(entry.term).append(",").append(entry.data).append(";");
        }
        return computeSuffixFormat;
    }

    private void checkIfMajorityOfVotesAndUpdate() throws InterruptedException {
        if (votesReceived.size() >= (nodeData.size() + 2) / 2) {
            state = LEADER;
            currentLeader = id;
            System.out.println("BECAME LEADER: " + currentTerm  + " " + votesReceived);

            inboundQueue.put("BECAME_LEADER");
        }
    }

    static int exchangeIds(Socket accept) throws IOException {
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(accept.getOutputStream()));
        bufferedWriter.write(Server.id + "\n");
        bufferedWriter.flush();

        String s = new BufferedReader(new InputStreamReader(accept.getInputStream())).readLine();

        return Integer.parseInt(s);
    }

    private void startClientListener() {
        new Thread(() -> {
            try {
                ServerSocket clientServerSocket = new ServerSocket(9100 + id);
                while (true) {
                    Socket clientSocket = clientServerSocket.accept();
                    System.out.println("Client connected");
                    new Thread(() -> {
                        try {
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(clientSocket.getInputStream())
                            );
                            BufferedWriter writer = new BufferedWriter(
                                    new OutputStreamWriter(clientSocket.getOutputStream())
                            );
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (state == LEADER) {
                                    log.add(new LogEntry(currentTerm, line));
                                    System.out.println(log);

                                    writer.write("OK\n");
                                } else {
                                    writer.write("NOT_LEADER:" + currentLeader + "\n");
                                }
                                writer.flush();
                            }
                        } catch (IOException e) {
                            System.out.println("Client disconnected");
                        }
                    }).start();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }
}
