package org.example.v4;

import org.example.v4.dto.NodeData;
import org.example.v4.dto.PeerConnection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static org.example.v4.Server.*;

public class ConnectionInitiator implements Runnable{
    @Override
    public void run() {
        while (true) {
            for(NodeData data: nodeData){
                if (Server.id > data.id && !nodes.containsKey(data.id)) {
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
    }
}
