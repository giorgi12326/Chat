package org.example.v3;

import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Server {
    Set<Socket> followers = Collections.synchronizedSet(new HashSet<>());

    public void start() {
        tryBinding();
    }

    private void tryBinding() {
        System.out.println("Server started");
        try {
            ServerSocket serverSocket = new ServerSocket(1234);
            System.out.println("got port 1234");
            new Thread(this::startLeaderJobThread).start();
            new Thread(() -> startAcceptConnectionThread(serverSocket)).start();
        }
        catch (BindException e){
            new Thread(this::startFollowing).start();
        }
        catch (IOException e) {
            System.out.println("UNEXPECTED IO EXCEPTION");
            throw new RuntimeException(e);
        }
    }

    private void startFollowing() {
        try {
            System.out.println("following instead!");
            Socket socket = new Socket(InetAddress.getLocalHost(),1234);

            System.out.println("connected well");
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("waiting for messages");

            while(true){
                System.out.println(reader.readLine());
            }
        } catch (IOException ex) {
            System.out.println("Server is not reachable or unexpectedly crashed");
            new Thread(this::tryBinding).start();
        }
    }

    private void startLeaderJobThread() {
        while(true) {
            Iterator<Socket> iterator = followers.iterator();
            while (iterator.hasNext()) {
                Socket socket = iterator.next();
                try {
                    BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    bufferedWriter.write("i am the leader!\n");
                    bufferedWriter.flush();
                }
                catch (SocketException e){
                    System.out.println("follower disconnected! removing it from set!");
                    iterator.remove();
                }
                catch (IOException e) {
                    System.out.println("couldn't write to follower");
                    iterator.remove();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void startAcceptConnectionThread(ServerSocket serverSocket) {
        while (true) {
            Socket socket;
            try {
                socket = serverSocket.accept();
                System.out.println("got new follower");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            followers.add(socket);
        }
    }

    public static void main(String[] args) {
        new Server().start();
    }

}
