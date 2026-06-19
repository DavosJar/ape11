package com.practica;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.function.Consumer;

public class ElectionManager {
    private static final int PUERTO = 9000;
    private static final int ESPERA_RESPUESTA = 2000;

    private final EstadoProceso estado;
    private final Consumer<Message> mensajero;

    public ElectionManager(EstadoProceso estado, Consumer<Message> mensajero) {
        this.estado = estado;
        this.mensajero = mensajero;
    }

    public boolean esElMayor() {
        Map<Integer, String> nodos = estado.getNodos();
        for (int i = estado.getId() + 1; i <= nodos.size(); i++) {
            if (nodos.containsKey(i)) return false;
        }
        return true;
    }

    public void iniciarEleccion() {
        System.out.println("[" + estado.getId() + "] iniciando eleccion");
        estado.getEleccionEnCurso().set(true);
        estado.getRespuestaRecibida().set(false);

        int mayores = 0;
        Map<Integer, String> nodos = estado.getNodos();
        for (int i = estado.getId() + 1; i <= nodos.size(); i++) {
            if (nodos.containsKey(i)) {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(nodos.get(i), PUERTO), 1000);
                    PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                    out.println(new Message(MessageType.ELECTION, estado.getId(), i).serializar());
                    s.close();
                    mayores++;
                } catch (IOException e) {
                    System.out.println("[" + estado.getId() + "] nodo " + i + " no responde");
                }
            }
        }

        if (mayores == 0) {
            declararCoordinador();
            return;
        }

        try { Thread.sleep(ESPERA_RESPUESTA); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!estado.getRespuestaRecibida().get()) {
            declararCoordinador();
        }
        estado.getEleccionEnCurso().set(false);
    }

    public void procesarELECTION(int emisor) {
        enviarMensaje(MessageType.OK, emisor);
        if (estado.isEsCoordinador()) {
            enviarMensaje(MessageType.COORDINATOR, emisor);
        } else if (!estado.getEleccionEnCurso().get()) {
            new Thread(this::iniciarEleccion).start();
        }
    }

    public void procesarOK() {
        estado.getRespuestaRecibida().set(true);
    }

    public void procesarCOORDINATOR(int emisor) {
        if (estado.isEsCoordinador() && emisor < estado.getId()) {
            enviarMensaje(MessageType.COORDINATOR, emisor);
            return;
        }

        estado.setEsCoordinador(false);
        if (emisor != estado.getCoordinadorActual()) {
            System.out.println("[" + estado.getId() + "] coordinador es nodo " + emisor);
            estado.getYaAckAlCoordinador().set(false);
        }
        estado.setCoordinadorActual(emisor);
        estado.getEleccionEnCurso().set(false);

        if (!estado.getYaAckAlCoordinador().getAndSet(true)) {
            enviarMensaje(MessageType.ACK, emisor);
        }

        if (emisor > estado.getId()) {
            estado.getRespuestaRecibida().set(true);
        } else if (emisor < estado.getId() && !estado.getEleccionEnCurso().get()) {
            new Thread(this::iniciarEleccion).start();
        }
    }

    public void procesarACK(int emisor) {
        estado.getAcknowledgedNodes().add(emisor);
        System.out.println("[" + estado.getId() + "] nodo " + emisor + " acknowledged");
        checkAllAcknowledged();
    }

    public void procesarPING(int emisor) {
        if (estado.isEsCoordinador()) {
            if (estado.getAcknowledgedNodes().add(emisor)) {
                System.out.println("[" + estado.getId() + "] nodo " + emisor + " acknowledged (PING)");
                checkAllAcknowledged();
            }
        }
    }

    private void checkAllAcknowledged() {
        boolean todosOk = true;
        for (Map.Entry<Integer, String> entry : estado.getNodos().entrySet()) {
            int nodeId = entry.getKey();
            if (nodeId != estado.getId() && !estado.getAcknowledgedNodes().contains(nodeId)) {
                todosOk = false;
                break;
            }
        }
        if (todosOk) {
            System.out.println("[" + estado.getId() + "] todos los nodos reconocen coordinador");
        }
    }

    public void declararCoordinador() {
        estado.setEsCoordinador(true);
        estado.setCoordinadorActual(estado.getId());
        estado.getAcknowledgedNodes().clear();
        System.out.println("[" + estado.getId() + "] SOY EL COORDINADOR");
        for (Map.Entry<Integer, String> entry : estado.getNodos().entrySet()) {
            int i = entry.getKey();
            if (i != estado.getId()) {
                enviarMensaje(MessageType.COORDINATOR, i);
            }
        }
    }

    private void enviarMensaje(String tipo, int receptor) {
        mensajero.accept(new Message(tipo, estado.getId(), receptor));
    }
}
