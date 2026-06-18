package com.practica;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.function.Consumer;

public class HeartbeatManager {
    private static final int PUERTO = 9000;
    private static final long INTERVALO = 5000;

    private final EstadoProceso estado;
    private final Consumer<Message> mensajero;
    private final ElectionManager electionManager;

    public HeartbeatManager(EstadoProceso estado, Consumer<Message> mensajero, ElectionManager electionManager) {
        this.estado = estado;
        this.mensajero = mensajero;
        this.electionManager = electionManager;
    }

    /**
     * Executes one heartbeat cycle: sleeps for the interval then acts.
     * Called repeatedly from a loop in Proceso.
     */
    public void run() {
        try {
            Thread.sleep(INTERVALO);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        if (estado.isEsCoordinador()) {
            // Re-send COORDINATOR only to nodes that have not ACKed yet
            for (Map.Entry<Integer, String> entry : estado.getNodos().entrySet()) {
                int nodeId = entry.getKey();
                if (nodeId != estado.getId() && !estado.getAcknowledgedNodes().contains(nodeId)) {
                    enviarMensaje(MessageType.COORDINATOR, nodeId);
                }
            }
        } else if (estado.getCoordinadorActual() != -1 && estado.getCoordinadorActual() != estado.getId()) {
            String host = estado.getNodos().get(estado.getCoordinadorActual());
            if (host == null) return;

            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(host, PUERTO), 3000);
                PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                out.println(new Message(MessageType.PING, estado.getId(), estado.getCoordinadorActual()).serializar());
                s.close();
            } catch (IOException e) {
                System.out.println("[" + estado.getId() + "] coordinador " + estado.getCoordinadorActual() + " no responde");
                estado.setCoordinadorActual(-1);
                estado.getYaAckAlCoordinador().set(false);
                if (!estado.getEleccionEnCurso().get()) {
                    new Thread(electionManager::iniciarEleccion).start();
                }
            }
        }
    }

    private void enviarMensaje(String tipo, int receptor) {
        mensajero.accept(new Message(tipo, estado.getId(), receptor));
    }
}
