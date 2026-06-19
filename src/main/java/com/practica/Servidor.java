package com.practica;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class Servidor {
    private final Proceso proceso;

    public Servidor() {
        InetAddress[] tasks = resolverTareas();
        Map<Integer, String> nodos = new HashMap<>();
        Arrays.sort(tasks, Comparator.comparing(InetAddress::getHostAddress));
        for (int i = 0; i < tasks.length; i++) {
            nodos.put(i + 1, tasks[i].getHostAddress());
        }

        int nodeId = resolverNodeId(tasks);

        System.out.println("[Servidor] Iniciando nodo " + nodeId);
        System.out.println("[Servidor] Nodos del cluster: " + nodos);

        this.proceso = new Proceso(nodeId, nodos);
    }

    private InetAddress[] resolverTareas() {
        String[] names = {"tasks.app", "tasks.cluster_app", "app"};
        for (int retry = 0; retry < 10; retry++) {
            for (String name : names) {
                try {
                    InetAddress[] tasks = InetAddress.getAllByName(name);
                    if (tasks != null && tasks.length >= 1) return tasks;
                } catch (UnknownHostException e) {
                    // try next name
                }
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new RuntimeException("No se pudieron resolver las tareas del servicio");
    }

    private int resolverNodeId(InetAddress[] tasks) {
        String myIP = null;
        try {
            myIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException("No se pudo determinar IP local", e);
        }
        for (int i = 0; i < tasks.length; i++) {
            if (tasks[i].getHostAddress().equals(myIP)) return i + 1;
        }
        throw new RuntimeException("No encontré mi IP " + myIP + " en las tareas del servicio");
    }

    public void iniciar() {
        proceso.iniciar();
    }

    public void detener() {
    }
}
