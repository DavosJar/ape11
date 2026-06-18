package com.practica;

public class Message {
    private String tipo;
    private int emisor;
    private int receptor;
    private String contenido;

    public Message(String tipo, int emisor, int receptor) {
        this(tipo, emisor, receptor, "");
    }

    public Message(String tipo, int emisor, int receptor, String contenido) {
        this.tipo = tipo;
        this.emisor = emisor;
        this.receptor = receptor;
        this.contenido = contenido;
    }

    public String getTipo() { return tipo; }
    public int getEmisor() { return emisor; }
    public int getReceptor() { return receptor; }
    public String getContenido() { return contenido; }

    public String serializar() {
        return tipo + "|" + emisor + "|" + receptor + "|" + contenido;
    }

    public static Message deserializar(String linea) {
        String[] p = linea.split("\\|", 4);
        int emisor = Integer.parseInt(p[1]);
        int receptor = Integer.parseInt(p[2]);
        String contenido = p.length >= 4 ? p[3] : "";
        return new Message(p[0], emisor, receptor, contenido);
    }

    @Override
    public String toString() {
        String base = "Message[" + tipo + " emisor=" + emisor + " receptor=" + receptor;
        if (!contenido.isEmpty()) base += " contenido=" + contenido;
        return base + "]";
    }
}
