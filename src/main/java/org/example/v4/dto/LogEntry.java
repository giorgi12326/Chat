package org.example.v4.dto;

public class LogEntry {
    public int term;
    public String data;

    public LogEntry(int term, String data) {
        this.term = term;
        this.data = data;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "term=" + term +
                ", data='" + data + '\'' +
                '}';
    }
}