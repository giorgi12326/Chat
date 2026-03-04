package org.example.v4;

import org.example.v4.dto.PeerConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.example.v4.Server.inboundQueue;
import static org.example.v4.Server.nodes;

public class Receiver implements Runnable{


    private final PeerConnection peerConnection;
    private final int nodeId;

    public Receiver(int nodeId ,PeerConnection peerConnection) {
        this.nodeId = nodeId;
        this.peerConnection = peerConnection;
    }


    @Override
    public void run() {
        try {
            while (true) {
                String s = peerConnection.reader.readLine();

                if (s == null) {
                    System.out.println("--------------------------------- NULLED: lost connection to" + nodeId + " ---------------------------------");
                    break;
                }

                inboundQueue.put(s);
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("--------------------------------- LISTENER: lost connection to" + nodeId + " ---------------------------------");
        } finally {
            peerConnection.close();
            nodes.remove(nodeId);
            System.out.println("Receiver thread for " + nodeId + " stopped.");
        }
    }
}
