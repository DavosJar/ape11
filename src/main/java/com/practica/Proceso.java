package com.practica;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Proceso {
    private int id;
    private boolean activo;
    private boolean esCoordinador;
    private Map<Integer, String> nodos;
    private static final int PUERTO = 9000;
    public static int totalMensajes = 0;

    private AtomicBoolean eleccionEnCurso = new AtomicBoolean(false);
    private AtomicBoolean respuestaRecibida = new AtomicBoolean(false);
    private int coordinadorActual = -1;

    public Proceso(int id, Map<Integer, String> nodos) {
        this.id = id;
        this.activo = true;
        this.esCoordinador = false;
        this.nodos = nodos;
    }

    public void iniciar() {
        new Thread(this::escuchar).start();
        try { Thread.sleep(500); } catch (InterruptedException e) {}
        
        if (esElMayor()) {
            declararCoordinador();
        } else {
            new Thread(this::iniciarEleccion).start();
        }
        
        new Thread(this::heartbeat).start();
    }

    private boolean esElMayor() {
        for (int i = id + 1; i <= nodos.size(); i++) {
            if (nodos.containsKey(i)) return false;
        }
        return true;
    }

    private void escuchar() {
        try (ServerSocket ss = new ServerSocket(PUERTO)) {
            System.out.println("[" + id + "] escuchando en puerto " + PUERTO);
            while (activo) {
                Socket socket = ss.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String linea = in.readLine();
                if (linea != null) {
                    procesarMensaje(Message.deserializar(linea));
                }
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[" + id + "] error: " + e.getMessage());
        }
    }

    private void heartbeat() {
        while (activo) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
            
            if (esCoordinador) {
                // Anunciarse a TODOS los nodos periodicamente
                // Asi si alguien se reconecta, sabe quien manda
                for (int i = 1; i <= nodos.size(); i++) {
                    if (i != id && nodos.containsKey(i)) {
                        enviarMensaje("COORDINATOR", i);
                    }
                }
            } else if (coordinadorActual != -1 && coordinadorActual != id) {
                String host = nodos.get(coordinadorActual);
                if (host == null) continue;
                
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(host, PUERTO), 3000);
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    out.println("PING|" + id + "|" + coordinadorActual);
                    s.close();
                } catch (IOException e) {
                    System.out.println("[" + id + "] coordinador " + coordinadorActual + " no responde");
                    coordinadorActual = -1;
                    if (!eleccionEnCurso.get()) {
                        new Thread(this::iniciarEleccion).start();
                    }
                }
            }
        }
    }

    public void enviarMensaje(String tipo, int receptor) {
        Message msg = new Message(tipo, this.id, receptor);
        String host = nodos.get(receptor);
        if (host == null) return;
        try (Socket socket = new Socket(host, PUERTO);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(msg.serializar());
            totalMensajes++;
            System.out.println("  " + msg);
        } catch (IOException e) {
            System.out.println("[" + id + "] nodo " + receptor + " no responde");
        }
    }

    private void procesarMensaje(Message msg) {
        System.out.println("[" + id + "] recibi: " + msg);
        switch (msg.getTipo()) {
            case "ELECTION":
                enviarMensaje("OK", msg.getEmisor());
                if (!eleccionEnCurso.get()) {
                    new Thread(this::iniciarEleccion).start();
                }
                break;
            case "OK":
                respuestaRecibida.set(true);
                break;
            case "COORDINATOR":
                esCoordinador = false;
                coordinadorActual = msg.getEmisor();
                System.out.println("[" + id + "] coordinador es nodo " + msg.getEmisor());
                eleccionEnCurso.set(false);
                
                // Si el coordinador tiene menor ID que yo, deberia ganar yo
                // Inicio eleccion para destronarlo
                if (msg.getEmisor() < id && !eleccionEnCurso.get()) {
                    new Thread(this::iniciarEleccion).start();
                }
                break;
            case "PING":
                break;
        }
    }

    public void iniciarEleccion() {
        System.out.println("[" + id + "] iniciando eleccion");
        eleccionEnCurso.set(true);
        respuestaRecibida.set(false);

        int mayores = 0;
        for (int i = id + 1; i <= nodos.size(); i++) {
            if (nodos.containsKey(i)) {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(nodos.get(i), PUERTO), 1000);
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    out.println(new Message("ELECTION", this.id, i).serializar());
                    s.close();
                    mayores++;
                } catch (IOException e) {
                    System.out.println("[" + id + "] nodo " + i + " no responde");
                }
            }
        }

        if (mayores == 0) {
            declararCoordinador();
            return;
        }

        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        if (!respuestaRecibida.get()) {
            declararCoordinador();
        }
        eleccionEnCurso.set(false);
    }

    private void declararCoordinador() {
        esCoordinador = true;
        coordinadorActual = id;
        System.out.println("[" + id + "] SOY EL COORDINADOR");
        for (int i = 1; i <= nodos.size(); i++) {
            if (i != id && nodos.containsKey(i)) {
                enviarMensaje("COORDINATOR", i);
            }
        }
    }

    public int getId()                        { return id; }
    public boolean isActivo()                 { return activo; }
    public boolean isCoordinador()            { return esCoordinador; }
    public void setActivo(boolean activo)     { this.activo = activo; }
    public void setCoordinador(boolean value) { this.esCoordinador = value; }

    @Override
    public String toString() {
        return "Proceso[id=" + id + ", activo=" + activo + ", coordinador=" + esCoordinador + "]";
    }
}
