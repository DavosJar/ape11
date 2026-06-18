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
    private static final long GRACIA_INICIO = 2500;

    private final EstadoProceso estado;
    private final ElectionManager electionManager;
    private final HeartbeatManager heartbeatManager;
    private final ConsensoManager consensoManager;

    public Proceso(int id, Map<Integer, String> nodos) {
        this.estado = new EstadoProceso(id, nodos);
        this.electionManager = new ElectionManager(estado, this::enviarMensaje);
        this.heartbeatManager = new HeartbeatManager(estado, this::enviarMensaje, electionManager);
        this.consensoManager = new ConsensoManager(estado, this::enviarMensaje);
    }

    public void iniciar() {
        new Thread(this::escuchar).start();
        // Wait for server socket to bind
        try { Thread.sleep(500); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Startup grace period: let other nodes boot before starting elections
        try { Thread.sleep(GRACIA_INICIO); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // After grace period, check if someone already declared as coordinator
        if (estado.getCoordinadorActual() != -1) {
            // Already have a coordinator — do nothing
        } else if (electionManager.esElMayor()) {
            electionManager.declararCoordinador();
        } else {
            new Thread(electionManager::iniciarEleccion).start();
        }

        // Start heartbeat loop
        new Thread(() -> {
            while (estado.isActivo()) {
                heartbeatManager.run();
            }
        }).start();

        // Start consensus rounds (only coordinator actually runs them)
        new Thread(() -> {
            try { Thread.sleep(5000); } catch (InterruptedException e) {}
            consensoManager.ejecutarTodasLasRondas();
        }).start();
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
        // Log receipts except PING (avoid noise)
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
                consensoManager.procesarVOTE_REQUEST(msg.getEmisor(), Integer.parseInt(msg.getContenido()));
                break;
            case MessageType.VOTE_SI:
            case MessageType.VOTE_NO:
                consensoManager.procesarVOTO(msg.getEmisor(), msg.getTipo());
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
            // Log sends except PING and routine COORDINATOR
            if (!MessageType.PING.equals(msg.getTipo()) && !MessageType.COORDINATOR.equals(msg.getTipo())) {
                System.out.println("  " + msg);
            }
        } catch (IOException e) {
            System.out.println("[" + estado.getId() + "] nodo " + msg.getReceptor() + " no responde");
        }
    }
}
