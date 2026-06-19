package com.practica;

public class MessageType {
    public static final String ELECTION = "ELECTION";
    public static final String OK = "OK";
    public static final String COORDINATOR = "COORDINATOR";
    public static final String ACK = "ACK";
    public static final String PING = "PING";

    // Byzantine consensus message types
    public static final String VOTE_REQUEST = "VOTE_REQUEST";
    public static final String VOTE_SI = "VOTE:SI";
    public static final String VOTE_NO = "VOTE:NO";
    public static final String DECISION_SI = "DECISION:SI";
    public static final String DECISION_NO = "DECISION:NO";
    public static final String VOTE_REPORT = "VOTE_REPORT";

    private MessageType() {}
}
