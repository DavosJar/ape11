package com.practica;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        String hostname = System.getenv("NODE_HOSTNAME");

        Map<Integer, String> nodos = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            String nodo = System.getenv("NODO_" + i);
            if (nodo != null && !nodo.isEmpty()) {
                nodos.put(i, nodo);
            }
        }

        int nodeId = -1;
        for (int i = 1; i <= nodos.size(); i++) {
            String nodo = nodos.get(i);
            if (nodo != null && nodo.equals(hostname)) {
                nodeId = i;
                break;
            }
        }

        if (nodeId == -1) {
            System.err.println("Error: no se pudo determinar NODE_ID para " + hostname);
            System.exit(1);
        }

        System.out.println("Nodo " + nodeId + " iniciado");
        System.out.println("Nodos: " + nodos);

        Proceso proceso = new Proceso(nodeId, nodos);
        proceso.iniciar();
    }
}
