package org.example.v4;

import org.example.v4.dto.Message;
import org.example.v4.dto.PeerConnection;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.example.v4.Server.nodes;

public class Sender implements Runnable{


    @Override
    public void run() {
        while(true) {
            try {
//                Message take = queue.take();
                Message take = new Message("ping");
                if(take.nodeId == -1) {
                    Iterator<Map.Entry<Integer, PeerConnection>> iterator = nodes.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<Integer, PeerConnection> entry = iterator.next();
                        try {
                            BufferedWriter bufferedWriter = entry.getValue().writer;
                            bufferedWriter.write(take.payload + "\n");
                            bufferedWriter.flush();
                            System.out.println("sent ping to " + entry.getKey());
                        } catch (IOException e) {
                            System.out.println("--------------------------------- SENDER: lost connection to" + entry.getKey() + " ---------------------------------");
                            entry.getValue().close();
                            iterator.remove();
                        }
                    }
                }
                else{
                    PeerConnection peerConnection;
                    peerConnection = nodes.get(take.nodeId);
                    try {
                        peerConnection.writer.write(take.payload + "\n");
                        peerConnection.writer.flush();
                    } catch (IOException e) {
                        peerConnection.close();
                        nodes.remove(take.nodeId);
                    }
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
