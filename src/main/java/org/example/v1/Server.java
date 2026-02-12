package org.example.v1;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Scanner;

public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(1234);
        System.out.println("Server Started");

        Socket socket = serverSocket.accept();
        System.out.println("Accepted connection");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Scanner scanner = new Scanner(System.in);

        FileOutputStream fos = new FileOutputStream("messages.log", true); // true = append
        FileChannel channel = fos.getChannel();

        Thread senderThread = new Thread(() -> {
            try {
                while (true) {
                    String s = scanner.nextLine();

                    writer.write(s + "\n");
                    writer.flush();
                    saveMessage(s, channel);

                    Thread.sleep(1000);
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Thread readerThread = new Thread(() -> {
            try{
                while(true){
                    String s = reader.readLine();
                    System.out.println("them: " + s);
                    saveMessage(s, channel);

                }
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        senderThread.start();
        readerThread.start();
    }

    public static void saveMessage(String s, FileChannel channel){
        ByteBuffer buffer = ByteBuffer.wrap(s.getBytes());
        try {
            while(buffer.hasRemaining()) {
                channel.write(buffer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}