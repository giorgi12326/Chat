package org.example.v4;

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
                nodes.put(s, new PeerConnection(accept));
                System.out.println("================================= connection accepted to node with id: " + s + " =================================");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
