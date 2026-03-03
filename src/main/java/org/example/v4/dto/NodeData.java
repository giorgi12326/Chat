package org.example.v4.dto;

public class NodeData {
    public Integer id;
    public Integer port;

    public NodeData(Integer id, Integer port) {
        this.id = id;
        this.port = port;
    }

    @Override
    public String toString() {
        return "NodeData{" +
                "id=" + id +
                ", port=" + port +
                '}';
    }
}
