package com.practica;

import java.util.*;
import java.util.function.Consumer;

public class ConsensoManager {
    private final EstadoProceso estado;
    private final Consumer<Message> mensajero;

    private int rondaActual = 1;
    private boolean rondaEnCurso;
    private Map<Integer, String> votosRecibidos;
    private Map<Integer, Map<Integer, String>> reportesVotos;
    private Map<Integer, String> votosObservados;
    private int votosSI;
    private int votosNO;
    private int totalRespondidos;

    private Set<Integer> bizantinosActuales = new HashSet<>();

    private Map<String, Object> ultimosResultados;

    public ConsensoManager(EstadoProceso estado, Consumer<Message> mensajero) {
        this.estado = estado;
        this.mensajero = mensajero;
    }

    private boolean soyBizantino() {
        return bizantinosActuales.contains(estado.getId());
    }

    public synchronized Map<String, Object> ejecutarRonda(int ronda, Set<Integer> bizantinos) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!estado.isEsCoordinador()) {
            result.put("error", "Solo el coordinador puede ejecutar rondas");
            result.put("coordinator", estado.getCoordinadorActual());
            return result;
        }

        bizantinosActuales = (bizantinos != null) ? new HashSet<>(bizantinos) : new HashSet<>();

        rondaActual = ronda;
        rondaEnCurso = true;
        votosRecibidos = new HashMap<>();
        reportesVotos = new HashMap<>();
        votosObservados = new HashMap<>();
        votosSI = 0;
        votosNO = 0;
        totalRespondidos = 0;

        System.out.println("\n========== RONDA " + ronda + " ==========");
        System.out.println("[" + estado.getId() + "] Bizantinos: " + (bizantinosActuales.isEmpty() ? "ninguno" : bizantinosActuales));
        System.out.println("[" + estado.getId() + "] Propuesta: APROBAR_TRANSACCION");
        System.out.println("[" + estado.getId() + "] Solicitando votos...");

        for (int i = 1; i <= estado.getNodos().size(); i++) {
            if (i != estado.getId() && estado.getNodos().containsKey(i)) {
                // content format: "ronda:byz1,byz2,..."
                StringBuilder content = new StringBuilder();
                content.append(ronda).append(":");
                boolean firstByz = true;
                for (int b : bizantinos) {
                    if (!firstByz) content.append(",");
                    content.append(b);
                    firstByz = false;
                }
                mensajero.accept(new Message(MessageType.VOTE_REQUEST, estado.getId(), i, content.toString()));
            }
        }

        String miVoto = (estado.getId() % 2 == 0) ? MessageType.VOTE_SI : MessageType.VOTE_NO;
        votosRecibidos.put(estado.getId(), miVoto);
        contarVoto(miVoto);

        int totalEsperados = estado.getNodos().size();
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

        System.out.println("[" + estado.getId() + "] Votos recibidos:");
        for (Map.Entry<Integer, String> entry : votosRecibidos.entrySet()) {
            System.out.println("  P" + entry.getKey() + " = " + entry.getValue());
        }

        String decision;
        if (votosSI > votosNO) {
            decision = MessageType.DECISION_SI;
        } else if (votosNO > votosSI) {
            decision = MessageType.DECISION_NO;
        } else {
            decision = MessageType.DECISION_SI;
        }

        System.out.println("[" + estado.getId() + "] Total: SI=" + votosSI + ", NO=" + votosNO);
        System.out.println("[" + estado.getId() + "] Decisión: " + decision);

        for (int i = 1; i <= estado.getNodos().size(); i++) {
            if (i != estado.getId() && estado.getNodos().containsKey(i)) {
                enviarMensaje(decision, i);
            }
        }
        System.out.println("[" + estado.getId() + "] Decisión enviada a todos");

        int reportesEsperados = estado.getNodos().size() - 1;
        int maxEsperaReportes = 5000;
        try {
            for (int i = 0; i < maxEsperaReportes / 100; i++) {
                if (reportesVotos.size() >= reportesEsperados) break;
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[" + estado.getId() + "] Reportes recibidos: " + reportesVotos.size() + "/" + reportesEsperados);

        List<Map<String, Object>> inconsistencias = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : votosRecibidos.entrySet()) {
            int emisor = entry.getKey();
            String votoAlCoordinador = entry.getValue();
            for (Map.Entry<Integer, Map<Integer, String>> reporte : reportesVotos.entrySet()) {
                int reportero = reporte.getKey();
                Map<Integer, String> votosVistos = reporte.getValue();
                String votoReportado = votosVistos.get(emisor);
                if (votoReportado != null && !votoReportado.equals(votoAlCoordinador)) {
                    Map<String, Object> inc = new LinkedHashMap<>();
                    inc.put("emisor", emisor);
                    inc.put("reportero", reportero);
                    inc.put("votoAlCoordinador", votoAlCoordinador);
                    inc.put("votoReportado", votoReportado);
                    inconsistencias.add(inc);
                    System.out.println("⚠ INCONSISTENCIA: P" + emisor + " votó " + votoAlCoordinador
                        + " al coordinador pero P" + reportero + " recibió " + votoReportado);
                }
            }
        }

        if (inconsistencias.isEmpty()) {
            System.out.println("[" + estado.getId() + "] Sin inconsistencias detectadas");
        }

        result.put("ronda", ronda);
        result.put("byzantine", new ArrayList<>(bizantinosActuales));
        result.put("decision", decision);
        result.put("totalSI", votosSI);
        result.put("totalNO", votosNO);

        Map<String, String> votos = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> entry : votosRecibidos.entrySet()) {
            votos.put("P" + entry.getKey(), entry.getValue());
        }
        result.put("votos", votos);

        Map<String, Map<String, String>> reportes = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<Integer, String>> reporte : reportesVotos.entrySet()) {
            Map<String, String> rep = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> e : reporte.getValue().entrySet()) {
                rep.put("P" + e.getKey(), e.getValue());
            }
            reportes.put("P" + reporte.getKey(), rep);
        }
        result.put("reportes", reportes);
        result.put("inconsistencias", inconsistencias);
        result.put("hayInconsistencias", !inconsistencias.isEmpty());

        ultimosResultados = result;
        return result;
    }

    public Map<String, Object> getUltimosResultados() {
        return ultimosResultados;
    }

    private void contarVoto(String voto) {
        if (MessageType.VOTE_SI.equals(voto)) {
            votosSI++;
        } else if (MessageType.VOTE_NO.equals(voto)) {
            votosNO++;
        }
        totalRespondidos++;
    }

    public void procesarVOTE_REQUEST(int emisorCoordinador, String contenido) {
        // Parse format: "ronda:byz1,byz2,..."
        String[] parts = contenido.split(":", 2);
        int ronda = Integer.parseInt(parts[0]);
        Set<Integer> bizantinos = new HashSet<>();
        if (parts.length > 1 && !parts[1].isEmpty()) {
            for (String s : parts[1].split(",")) {
                bizantinos.add(Integer.parseInt(s.trim()));
            }
        }
        this.bizantinosActuales = bizantinos;

        this.rondaActual = ronda;
        rondaEnCurso = true;
        votosObservados = new HashMap<>();

        String votoParaCoordinador;
        String votoParaOtros;

        if (soyBizantino()) {
            votoParaCoordinador = MessageType.VOTE_SI;
            votoParaOtros = MessageType.VOTE_NO;
        } else {
            boolean voto = (estado.getId() % 2 == 0);
            votoParaCoordinador = voto ? MessageType.VOTE_SI : MessageType.VOTE_NO;
            votoParaOtros = votoParaCoordinador;
        }

        enviarMensaje(votoParaCoordinador, emisorCoordinador);

        int miId = estado.getId();
        for (int i = 1; i <= estado.getNodos().size(); i++) {
            if (i != miId && i != emisorCoordinador && estado.getNodos().containsKey(i)) {
                String voto;
                if (soyBizantino()) {
                    voto = (i % 2 == 0) ? MessageType.VOTE_SI : MessageType.VOTE_NO;
                } else {
                    voto = votoParaOtros;
                }
                enviarMensaje(voto, i);
            }
        }

        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException e) { return; }
            if (votosObservados.isEmpty()) return;

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, String> entry : votosObservados.entrySet()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(entry.getKey()).append(":").append(entry.getValue());
            }
            mensajero.accept(new Message(MessageType.VOTE_REPORT, miId, emisorCoordinador, sb.toString()));
            System.out.println("[" + miId + "] Reporte enviado al coordinador: {" + sb + "}");
        }).start();
    }

    public void procesarVOTO(int emisor, String tipo) {
        if (!rondaEnCurso) return;
        if (estado.isEsCoordinador()) {
            if (!votosRecibidos.containsKey(emisor)) {
                votosRecibidos.put(emisor, tipo);
                contarVoto(tipo);
                System.out.println("[" + estado.getId() + "] voto: P" + emisor + " = " + tipo);
            }
        } else {
            votosObservados.put(emisor, tipo);
            System.out.println("[" + estado.getId() + "] voto observado: P" + emisor + " = " + tipo);
        }
    }

    public void procesarVOTE_REPORT(int reportero, String contenido) {
        if (!estado.isEsCoordinador()) return;
        Map<Integer, String> votosVistos = new HashMap<>();
        if (contenido != null && !contenido.isEmpty()) {
            for (String entry : contenido.split(",")) {
                String[] parts = entry.split(":", 2);
                if (parts.length == 2) {
                    try {
                        votosVistos.put(Integer.parseInt(parts[0]), parts[1]);
                    } catch (NumberFormatException e) { }
                }
            }
        }
        reportesVotos.put(reportero, votosVistos);
        System.out.println("[" + estado.getId() + "] Reporte recibido de P" + reportero + ": " + contenido);
    }

    public void procesarDECISION(int emisor, String decision) {
        System.out.println("[" + estado.getId() + "] Decisión final de la ronda: " + decision);
        rondaEnCurso = false;
    }

    private void enviarMensaje(String tipo, int receptor) {
        mensajero.accept(new Message(tipo, estado.getId(), receptor));
    }
}
