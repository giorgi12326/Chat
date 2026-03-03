package org.example.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) {
        ServerSocket serverSocket = null;
        BufferedReader reader;
        try {
            serverSocket = new ServerSocket(1234);
            System.out.println("waiting");
            Socket accept = serverSocket.accept();
            System.out.println("connected");
            reader = new BufferedReader(new InputStreamReader(accept.getInputStream()));

            while(true){
                String s = reader.readLine();
                if(s == null) {
                    System.out.println("pipe closed!!!");
                    accept.close();
                    serverSocket.close();
                    break;
                }
                System.out.println(s);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}
