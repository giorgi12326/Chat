package org.example.v4.dto;

public class Message {
    public int nodeId = -1;
    public String payload;

    public Message(int nodeId, String payload) {
        this.nodeId = nodeId;
        this.payload = payload;
    }

    public Message(String payload) {
        this.payload = payload;
    }
}
