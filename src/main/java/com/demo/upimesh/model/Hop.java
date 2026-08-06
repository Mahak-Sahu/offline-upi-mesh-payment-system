package com.demo.upimesh.model;

public class Hop {

    private String from;
    private String to;
    private String packetId;

    public Hop() {
    }

    public Hop(String from, String to, String packetId) {
        this.from = from;
        this.to = to;
        this.packetId = packetId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }
}