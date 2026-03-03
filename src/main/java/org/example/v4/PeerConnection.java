package org.example.v4;

import java.io.*;
import java.net.Socket;

class PeerConnection {
    final Socket socket;
    final BufferedReader reader;
    final BufferedWriter writer;

    PeerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    void close() {
        try { reader.close(); } catch (IOException ignored) {}
        try { writer.close(); } catch (IOException ignored) {}
        try { socket.close(); } catch (IOException ignored) {}
    }
}
