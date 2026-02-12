package org.example.v2;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Server {
    public final int SERVER_ID;
    public BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
    private final Map<Integer, BufferedWriter> clientSockets = new HashMap<>();

    FileChannel channel;
    FileChannel index;

    public static void main(String[] args) throws IOException {
        Server server1 = new Server(0);
        server1.start(1234);
    }

    public Server(int id) {
        SERVER_ID = id;
    }

    private void start(int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server Started for clients to connect!");

        new Thread(() -> {
            try {
                while (true) {
                    Message message = queue.take();
                    System.out.println(message);

                    for (Integer clientId : clientSockets.keySet()) {
                        if (message.getClientId() == clientId) continue;

                        clientSockets.get(clientId).write(message + "\n");
                        clientSockets.get(clientId).flush();
                    }

                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).start();

        while (true) {

            initializeFiles();

            Socket socket = serverSocket.accept();
            String receivedId = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
            int id = Integer.parseInt(receivedId);

            clientSockets.put(id, new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
            System.out.println("Accepted connection, assigned " + id);
            sendHistoryToClientId(id, channel, index);

            new Thread(() -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    while (true) {
                        String s = reader.readLine();
                        queue.put(new Message(id, s));
                        System.out.println("received: " + s + " id : " + id);
                        saveMessage(id + ":" + s, channel, index);

                    }
                } catch (IOException e) {
                    System.out.println("Client " + id + " disconnected.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    cleanupClient(id, socket);
                }

            }).start();
        }
    }

    public synchronized void saveMessage(String s, FileChannel channel, FileChannel index) {
        try {
            byte[] bytes = s.getBytes();

            long offset = channel.size();
            ByteBuffer offsetBuf = ByteBuffer.allocate(8);
            offsetBuf.putLong(offset);
            offsetBuf.flip();
            while (offsetBuf.hasRemaining()) {
                index.write(offsetBuf);
            }

            ByteBuffer msgBuf = ByteBuffer.wrap(bytes);
            while (msgBuf.hasRemaining()) {
                channel.write(msgBuf);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendHistoryToClientId(int id, FileChannel channel, FileChannel index) {
        System.out.println("Sending history to client " + id);
        ByteBuffer buffer = ByteBuffer.allocate(8);
        try {
            long indexSize = index.size();
            for (long i = 0; i < indexSize; i += 8) {
                buffer.clear();
                index.read(buffer, i);
                buffer.flip();
                long offset = buffer.getLong();

                long nextoffset;
                if (i + 8 >= indexSize) {
                    nextoffset = channel.size();
                } else {
                    buffer.clear();
                    index.read(buffer, i + 8);
                    buffer.flip();
                    nextoffset = buffer.getLong();
                }

                ByteBuffer msgBuf = ByteBuffer.allocate((int)(nextoffset - offset));
                channel.read(msgBuf, offset);
                msgBuf.flip();
                byte[] messageBytes = new byte[msgBuf.remaining()];
                msgBuf.get(messageBytes);
                String message = new String(messageBytes, StandardCharsets.UTF_8);

//                String[] split = message.split(":");
//                if(Integer.parseInt(split[0])==id) continue;
//                System.out.println(split[0] + " " + id);

                BufferedWriter writer = clientSockets.get(id);
                writer.write(message + "\n");
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void cleanupClient(int id, Socket socket) {
        try {
            System.out.println("Cleaning up client " + id);
            clientSockets.remove(id);
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeFiles() {
        RandomAccessFile msgFile = null;
        RandomAccessFile idxFile = null;
        try {
            msgFile = new RandomAccessFile("messages_" + SERVER_ID , "rw");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        try {
            idxFile = new RandomAccessFile("index.log_" + SERVER_ID, "rw");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        channel = msgFile.getChannel();
        index = idxFile.getChannel();
    }
}