package com.practica;

public class Message {
    private String tipo;
    private int emisor;
    private int receptor;

    public Message(String tipo, int emisor, int receptor) {
        this.tipo = tipo;
        this.emisor = emisor;
        this.receptor = receptor;
    }

    public String getTipo() { return tipo; }
    public int getEmisor() { return emisor; }
    public int getReceptor() { return receptor; }

    public String serializar() {
        return tipo + "|" + emisor + "|" + receptor;
    }

    public static Message deserializar(String linea) {
        String[] p = linea.split("\\|");
        return new Message(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    @Override
    public String toString() {
        return "Message[" + tipo + " emisor=" + emisor + " receptor=" + receptor + "]";
    }
}
