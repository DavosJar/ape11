package com.practica;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        Map<Integer, String> nodos = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            String nodo = System.getenv("NODO_" + i);
            if (nodo != null && !nodo.isEmpty()) {
                nodos.put(i, nodo);
            }
        }

        int nodeId = -1;

        // 1. Modo simulación: NODE_ID explícito (docker-compose.dev.yml)
        String nodeIdStr = System.getenv("NODE_ID");
        if (nodeIdStr != null && !nodeIdStr.isEmpty()) {
            try {
                nodeId = Integer.parseInt(nodeIdStr);
            } catch (NumberFormatException e) {
                nodeId = -1;  // fall through to hostname
            }
        }

        // 2. Modo 5 máquinas reales: mapeo por hostname (SIN CAMBIOS)
        if (nodeId == -1) {
            String hostname = System.getenv("NODE_HOSTNAME");
            if (hostname != null && !hostname.isEmpty()) {
                for (int i = 1; i <= nodos.size(); i++) {
                    String nodo = nodos.get(i);
                    if (nodo != null && nodo.equals(hostname)) {
                        nodeId = i;
                        break;
                    }
                }
            }
        }

        if (nodeId == -1) {
            System.err.println("Error: no se pudo determinar NODE_ID para hostname="
                + System.getenv("NODE_HOSTNAME") + " NODE_ID=" + System.getenv("NODE_ID"));
            System.exit(1);
        }

        System.out.println("Nodo " + nodeId + " iniciado");
        System.out.println("Nodos: " + nodos);

        Proceso proceso = new Proceso(nodeId, nodos);
        proceso.iniciar();
    }
}
