package com.practica;

import java.util.HashMap;
import java.util.Map;

public class Servidor {
    private final Proceso proceso;

    public Servidor() {
        Map<Integer, String> nodos = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            String nodo = System.getenv("NODO_" + i);
            if (nodo != null && !nodo.isEmpty()) {
                nodos.put(i, nodo);
            }
        }

        int nodeId = Integer.parseInt(System.getenv("NODE_ID"));

        System.out.println("[Servidor] Iniciando nodo " + nodeId);
        System.out.println("[Servidor] Nodos del cluster: " + nodos);

        this.proceso = new Proceso(nodeId, nodos);
    }

    public void iniciar() {
        proceso.iniciar();
    }

    public void detener() {
    }
}
