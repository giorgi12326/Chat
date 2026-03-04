package org.example.v4;

import org.example.v4.dto.PeerConnection;

import java.io.*;
import java.net.Socket;

import static org.example.v4.Server.*;

public class ConnectionListener implements Runnable{
    @Override
    public void run() {
        if(port == 9002)
            return;
        while (true) {
            try {
                Socket accept = serverSocket.accept();
                int s = exchangeIds(accept);
                PeerConnection peerConnection = new PeerConnection(accept);
                nodes.put(s, peerConnection);
                new Thread(new PeerConnectionReceiverThread(id, peerConnection)).start();
                System.out.println("================================= connection accepted to node with id: " + s + " =================================");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
