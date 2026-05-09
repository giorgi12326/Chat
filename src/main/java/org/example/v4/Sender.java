package org.example.v4;

import org.example.v4.dto.Message;
import org.example.v4.dto.PeerConnection;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.example.v4.Server.*;

public class Sender implements Runnable{


    @Override
    public void run() {
        while(true) {
            try {
                Message take = senderQueue.take();


                if(take.nodeId == -1) {
                    if(logSent)
                        System.out.println("BROADCAST" + "(" + state  + ") " + take.payload);

                    Iterator<Map.Entry<Integer, PeerConnection>> iterator = nodes.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<Integer, PeerConnection> entry = iterator.next();
                        try {
                            BufferedWriter bufferedWriter = entry.getValue().writer;
                            bufferedWriter.write(take.payload + "\n");
                            bufferedWriter.flush();
                        } catch (IOException e) {
                            System.out.println("--------------------------------- SENDER: lost connection to" + entry.getKey() + " ---------------------------------");
                            entry.getValue().close();
                            iterator.remove();
                        }
                    }
                }
                else{
                    if(Server.logSent)
                        System.out.println("SENT" + "(" + state  + ") " + take.payload);

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
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
