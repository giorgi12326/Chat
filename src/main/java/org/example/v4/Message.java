package org.example.v4;

public class Message {
    int nodeId = -1;
    String payload;

    public Message(int nodeId, String payload) {
        this.nodeId = nodeId;
        this.payload = payload;
    }

    public Message(String payload) {
        this.payload = payload;
    }
}
