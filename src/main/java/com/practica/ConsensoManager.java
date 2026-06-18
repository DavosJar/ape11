package com.practica;

import java.util.*;
import java.util.function.Consumer;

public class ConsensoManager {
    private final EstadoProceso estado;
    private final Consumer<Message> mensajero;

    // Current round state
    private int rondaActual = 1;           // 1, 2, 3
    private boolean rondaEnCurso;
    private Map<Integer, String> votosRecibidos;   // proceso → voto recibido por el coordinador
    private Map<Integer, Map<Integer, String>> reportesVotos; // proceso que reporta → (emisor → voto)
    private int votosSI;
    private int votosNO;
    private int totalRespondidos;

    // Byzantine config per round
    private static final Set<Integer> BIZANTINOS_RONDA_1 = new HashSet<>();  // 0 bizantinos
    private static final Set<Integer> BIZANTINOS_RONDA_2 = new HashSet<>(Arrays.asList(3)); // 1 bizantino
    private static final Set<Integer> BIZANTINOS_RONDA_3 = new HashSet<>(Arrays.asList(3, 4)); // 2 bizantinos

    public ConsensoManager(EstadoProceso estado, Consumer<Message> mensajero) {
        this.estado = estado;
        this.mensajero = mensajero;
    }

    /** Returns the set of Byzantine process IDs for the given round */
    private Set<Integer> bizantinosEnRonda(int ronda) {
        switch (ronda) {
            case 1: return BIZANTINOS_RONDA_1;
            case 2: return BIZANTINOS_RONDA_2;
            case 3: return BIZANTINOS_RONDA_3;
            default: return Collections.emptySet();
        }
    }

    /** Whether THIS process is Byzantine in the current round */
    private boolean soyBizantino() {
        return bizantinosEnRonda(rondaActual).contains(estado.getId());
    }

    /**
     * Called by the coordinator to execute all 3 rounds sequentially.
     * Runs in its own thread.
     */
    public void ejecutarTodasLasRondas() {
        new Thread(() -> {
            // Wait a bit for stabilization after election
            try { Thread.sleep(3000); } catch (InterruptedException e) {}

            for (int ronda = 1; ronda <= 3; ronda++) {
                System.out.println("\n========== RONDA " + ronda + " ==========");
                Set<Integer> bizantinos = bizantinosEnRonda(ronda);
                System.out.println("[" + estado.getId() + "] Bizantinos: " + (bizantinos.isEmpty() ? "ninguno" : bizantinos));
                ejecutarRonda(ronda);
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
            }
        }).start();
    }

    /**
     * Executes a single consensus round (coordinator only).
     */
    private void ejecutarRonda(int ronda) {
        if (!estado.isEsCoordinador()) return;

        rondaActual = ronda;
        rondaEnCurso = true;
        votosRecibidos = new HashMap<>();
        reportesVotos = new HashMap<>();
        votosSI = 0;
        votosNO = 0;
        totalRespondidos = 0;

        System.out.println("[" + estado.getId() + "] Propuesta: APROBAR_TRANSACCION");
        System.out.println("[" + estado.getId() + "] Solicitando votos...");

        // Send VOTE_REQUEST to all other processes (include round number)
        for (int i = 1; i <= estado.getNodos().size(); i++) {
            if (i != estado.getId() && estado.getNodos().containsKey(i)) {
                mensajero.accept(new Message(MessageType.VOTE_REQUEST, estado.getId(), i, String.valueOf(ronda)));
            }
        }

        // Coordinator also votes (honest vote)
        String miVoto = (estado.getId() % 2 == 0) ? MessageType.VOTE_SI : MessageType.VOTE_NO;
        votosRecibidos.put(estado.getId(), miVoto);
        contarVoto(miVoto);

        // Wait for votes (timeout per node: total N-1 * 1000ms)
        int totalEsperados = estado.getNodos().size(); // including self
        int maxEspera = (totalEsperados - 1) * 1000 + 2000;
        try {
            for (int i = 0; i < maxEspera / 100; i++) {
                if (totalRespondidos >= totalEsperados) break;
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        rondaEnCurso = false;

        // Log all received votes
        System.out.println("[" + estado.getId() + "] Votos recibidos:");
        for (Map.Entry<Integer, String> entry : votosRecibidos.entrySet()) {
            System.out.println("  P" + entry.getKey() + " = " + entry.getValue());
        }

        // Decide by majority
        String decision;
        if (votosSI > votosNO) {
            decision = MessageType.DECISION_SI;
        } else if (votosNO > votosSI) {
            decision = MessageType.DECISION_NO;
        } else {
            // Tie → coordinator decides SI
            decision = MessageType.DECISION_SI;
        }

        System.out.println("[" + estado.getId() + "] Total: SI=" + votosSI + ", NO=" + votosNO);
        System.out.println("[" + estado.getId() + "] Decisión: " + decision);

        // Broadcast decision
        for (int i = 1; i <= estado.getNodos().size(); i++) {
            if (i != estado.getId() && estado.getNodos().containsKey(i)) {
                enviarMensaje(decision, i);
            }
        }

        System.out.println("[" + estado.getId() + "] Decisión enviada a todos");

        // CHECK FOR BYZANTINE INCONSISTENCIES
        detectarInconsistencias();
    }

    private void contarVoto(String voto) {
        if (MessageType.VOTE_SI.equals(voto)) {
            votosSI++;
        } else if (MessageType.VOTE_NO.equals(voto)) {
            votosNO++;
        }
        totalRespondidos++;
    }

    /**
     * Detects Byzantine inconsistencies by comparing votes received
     * directly vs votes reported by other processes.
     */
    private void detectarInconsistencias() {
        boolean hayInconsistencia = false;

        for (Map.Entry<Integer, String> entry : votosRecibidos.entrySet()) {
            int emisor = entry.getKey();
            String votoAlCoordinador = entry.getValue();

            // Check what other processes reported receiving from this emisor
            for (Map.Entry<Integer, Map<Integer, String>> reporte : reportesVotos.entrySet()) {
                int reportero = reporte.getKey();
                Map<Integer, String> votosVistos = reporte.getValue();
                String votoReportado = votosVistos.get(emisor);

                if (votoReportado != null && !votoReportado.equals(votoAlCoordinador)) {
                    System.out.println("⚠ INCONSISTENCIA: P" + emisor + " votó " + votoAlCoordinador
                        + " al coordinador pero P" + reportero + " recibió " + votoReportado);
                    hayInconsistencia = true;
                }
            }
        }

        if (!hayInconsistencia) {
            System.out.println("[" + estado.getId() + "] Sin inconsistencias detectadas");
        }
    }

    /**
     * Called by ANY process when receiving VOTE_REQUEST from coordinator.
     * Each process broadcasts its vote to ALL processes (for Byzantine detection).
     */
    public void procesarVOTE_REQUEST(int emisorCoordinador, int ronda) {
        this.rondaActual = ronda;
        rondaEnCurso = true;

        String votoParaCoordinador;
        String votoParaOtros;

        if (soyBizantino()) {
            // Byzantine: says SI to coordinator, NO to everyone else
            votoParaCoordinador = MessageType.VOTE_SI;
            votoParaOtros = MessageType.VOTE_NO;
        } else {
            // Honest: same vote for everyone
            boolean voto = (estado.getId() % 2 == 0);  // even = SI, odd = NO
            votoParaCoordinador = voto ? MessageType.VOTE_SI : MessageType.VOTE_NO;
            votoParaOtros = votoParaCoordinador;
        }

        // Send vote to coordinator
        enviarMensaje(votoParaCoordinador, emisorCoordinador);

        // Send vote to ALL other processes (for Byzantine detection)
        int miId = estado.getId();
        for (int i = 1; i <= estado.getNodos().size(); i++) {
            if (i != miId && i != emisorCoordinador && estado.getNodos().containsKey(i)) {
                // Byzantine: send different vote; Honest: send same vote
                String voto;
                if (soyBizantino()) {
                    // Byzantine sends NO to some, SI to others
                    voto = (i % 2 == 0) ? MessageType.VOTE_SI : MessageType.VOTE_NO;
                } else {
                    voto = votoParaOtros;
                }
                enviarMensaje(voto, i);
            }
        }
    }

    /**
     * Called by coordinator when receiving a VOTE:SI or VOTE:NO message.
     */
    public void procesarVOTO(int emisor, String tipo) {
        if (!rondaEnCurso) return;

        System.out.println("[" + estado.getId() + "] voto: P" + emisor + " = " + tipo);

        if (estado.isEsCoordinador()) {
            if (!votosRecibidos.containsKey(emisor)) {
                votosRecibidos.put(emisor, tipo);
                contarVoto(tipo);
            }
        }
    }

    /**
     * Called by any process when receiving a DECISION:SI or DECISION:NO
     */
    public void procesarDECISION(int emisor, String decision) {
        System.out.println("[" + estado.getId() + "] Decisión final de la ronda: " + decision);
        rondaEnCurso = false;
    }

    private void enviarMensaje(String tipo, int receptor) {
        mensajero.accept(new Message(tipo, estado.getId(), receptor));
    }
}
