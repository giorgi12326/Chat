package org.example.v4;

import org.example.v4.dto.PeerConnection;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import static org.example.v4.Server.nodes;

public class Receiver implements Runnable{
    @Override
    public void run() {
        while(true) {
            Iterator<Map.Entry<Integer, PeerConnection>> iterator = nodes.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, PeerConnection> entry = iterator.next();
                try {
                    BufferedReader bufferedWriter = entry.getValue().reader;
                    String s = bufferedWriter.readLine();// blocking per node
                    if(s == null){
                        System.out.println("--------------------------------- NULLED: lost connection to" + entry.getKey() + " ---------------------------------");
                        entry.getValue().close();
                        iterator.remove();
                    }

                    System.out.println("received: " + s);

                } catch (IOException e) {
                    System.out.println("--------------------------------- LISTENER: lost connection to" + entry.getKey() + " ---------------------------------");
                    entry.getValue().close();
                    iterator.remove();
                }
            }
        }
    }
}
