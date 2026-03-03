package org.example.v4.dto;

import java.io.*;
import java.net.Socket;

public class PeerConnection {
    public final Socket socket;
    public final BufferedReader reader;
    public final BufferedWriter writer;

    public PeerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    public void close() {
        try { reader.close(); } catch (IOException ignored) {}
        try { writer.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}
    }
}
