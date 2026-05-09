package org.example.v4;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        try {
            Socket socket = new Socket(InetAddress.getLocalHost(), 9100);
            BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())
            );
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream())
            );
            while (true) {
                String s = scanner.nextLine();
                writer.write(s + "\n");
                writer.flush();
                String response = reader.readLine();
                System.out.println("Server response: " + response);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}