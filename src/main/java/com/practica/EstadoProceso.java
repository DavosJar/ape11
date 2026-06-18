package com.practica;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class EstadoProceso {
    private final int id;
    private final Map<Integer, String> nodos;
    private volatile boolean activo;
    private volatile boolean esCoordinador;
    private volatile int coordinadorActual = -1;
    private final AtomicBoolean eleccionEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean respuestaRecibida = new AtomicBoolean(false);
    private final Set<Integer> acknowledgedNodes = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean yaAckAlCoordinador = new AtomicBoolean(false);

    public EstadoProceso(int id, Map<Integer, String> nodos) {
        this.id = id;
        this.nodos = nodos;
        this.activo = true;
        this.esCoordinador = false;
    }

    public int getId() { return id; }
    public Map<Integer, String> getNodos() { return nodos; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public boolean isEsCoordinador() { return esCoordinador; }
    public void setEsCoordinador(boolean esCoordinador) { this.esCoordinador = esCoordinador; }
    public int getCoordinadorActual() { return coordinadorActual; }
    public void setCoordinadorActual(int coordinadorActual) { this.coordinadorActual = coordinadorActual; }
    public AtomicBoolean getEleccionEnCurso() { return eleccionEnCurso; }
    public AtomicBoolean getRespuestaRecibida() { return respuestaRecibida; }
    public Set<Integer> getAcknowledgedNodes() { return acknowledgedNodes; }
    public AtomicBoolean getYaAckAlCoordinador() { return yaAckAlCoordinador; }
}
