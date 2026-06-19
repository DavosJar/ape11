package com.practica;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public class Proceso {
    private static final int PUERTO = 9000;
    private static final int PUERTO_API = 8080;
    private static final long GRACIA_INICIO = 2500;

    private final EstadoProceso estado;
    private final ElectionManager electionManager;
    private final HeartbeatManager heartbeatManager;
    private final ConsensoManager consensoManager;
    private ApiServer apiServer;

    public Proceso(int id, Map<Integer, String> nodos) {
        this.estado = new EstadoProceso(id, nodos);
        this.electionManager = new ElectionManager(estado, this::enviarMensaje);
        this.heartbeatManager = new HeartbeatManager(estado, this::enviarMensaje, electionManager);
        this.consensoManager = new ConsensoManager(estado, this::enviarMensaje);
    }

    public EstadoProceso getEstado() { return estado; }
    public ConsensoManager getConsensoManager() { return consensoManager; }

    public void iniciar() {
        new Thread(this::escuchar).start();
        try { Thread.sleep(500); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try { Thread.sleep(GRACIA_INICIO); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (estado.getCoordinadorActual() != -1) {
        } else if (electionManager.esElMayor()) {
            electionManager.declararCoordinador();
        } else {
            new Thread(electionManager::iniciarEleccion).start();
        }

        new Thread(() -> {
            while (estado.isActivo()) {
                heartbeatManager.run();
            }
        }).start();

        try {
            int apiPort = PUERTO_API;
            String portStr = System.getenv("API_PORT");
            if (portStr != null && !portStr.isEmpty()) {
                apiPort = Integer.parseInt(portStr);
            }
            this.apiServer = new ApiServer(this, apiPort);
            apiServer.iniciar();
        } catch (IOException e) {
            System.err.println("[" + estado.getId() + "] Error iniciando API server: " + e.getMessage());
        }

        System.out.println("[" + estado.getId() + "] API disponible en http://0.0.0.0:" + (System.getenv("API_PORT") != null ? System.getenv("API_PORT") : "8080") + "/api/status");
    }

    private void escuchar() {
        try (ServerSocket ss = new ServerSocket(PUERTO)) {
            System.out.println("[" + estado.getId() + "] escuchando en puerto " + PUERTO);
            while (estado.isActivo()) {
                Socket socket = ss.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String linea = in.readLine();
                if (linea != null) {
                    procesarMensaje(Message.deserializar(linea));
                }
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("[" + estado.getId() + "] error: " + e.getMessage());
        }
    }

    private void procesarMensaje(Message msg) {
        if (!MessageType.PING.equals(msg.getTipo())) {
            System.out.println("[" + estado.getId() + "] recibi: " + msg);
        }

        switch (msg.getTipo()) {
            case MessageType.ELECTION:
                electionManager.procesarELECTION(msg.getEmisor());
                break;
            case MessageType.OK:
                electionManager.procesarOK();
                break;
            case MessageType.COORDINATOR:
                electionManager.procesarCOORDINATOR(msg.getEmisor());
                break;
            case MessageType.ACK:
                electionManager.procesarACK(msg.getEmisor());
                break;
            case MessageType.PING:
                electionManager.procesarPING(msg.getEmisor());
                break;
            case MessageType.VOTE_REQUEST:
                consensoManager.procesarVOTE_REQUEST(msg.getEmisor(), msg.getContenido());
                break;
            case MessageType.VOTE_SI:
            case MessageType.VOTE_NO:
                consensoManager.procesarVOTO(msg.getEmisor(), msg.getTipo());
                break;
            case MessageType.VOTE_REPORT:
                consensoManager.procesarVOTE_REPORT(msg.getEmisor(), msg.getContenido());
                break;
            case MessageType.DECISION_SI:
            case MessageType.DECISION_NO:
                consensoManager.procesarDECISION(msg.getEmisor(), msg.getTipo());
                break;
        }
    }

    private void enviarMensaje(Message msg) {
        String host = estado.getNodos().get(msg.getReceptor());
        if (host == null) return;
        try (Socket socket = new Socket(host, PUERTO);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.println(msg.serializar());
            if (!MessageType.PING.equals(msg.getTipo()) && !MessageType.COORDINATOR.equals(msg.getTipo())) {
                System.out.println("  " + msg);
            }
        } catch (IOException e) {
            System.out.println("[" + estado.getId() + "] nodo " + msg.getReceptor() + " no responde");
        }
    }
}
