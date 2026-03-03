package org.example.v2;

public class Message {
    int clientId;
    String message;

    public Message() {
    }
    public Message(int clientId, String message) {
        this.clientId = clientId;
        this.message = message;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getClientId() {
        return clientId;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return clientId + ":" + message;
    }
}
