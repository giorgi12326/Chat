package org.example.v1;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws IOException, InterruptedException {
        Socket socket = new Socket("localhost", 1234);
        System.out.println("Accepted connection");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        Scanner scanner = new Scanner(System.in);

        Thread senderThread = new Thread(() -> {
            try {
                while (true) {
                    String s = scanner.nextLine();

                    writer.write(s + "\n");
                    writer.flush();

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
                    System.out.println("them: " + reader.readLine());
                }
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
        senderThread.start();
        readerThread.start();
    }

}
