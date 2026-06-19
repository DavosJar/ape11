package com.practica;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

public class ApiServer {
    private final HttpServer server;
    private final Proceso proceso;
    private final int puerto;

    public ApiServer(Proceso proceso, int puerto) throws IOException {
        this.proceso = proceso;
        this.puerto = puerto;
        this.server = HttpServer.create(new InetSocketAddress(puerto), 0);

        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/round", this::handleRound);
        server.createContext("/api/results", this::handleResults);

        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void iniciar() {
        server.start();
        System.out.println("[API] Servidor HTTP iniciado en puerto " + puerto);
    }

    public void detener() {
        server.stop(0);
    }

    // ================== HANDLERS ==================

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            responder(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        EstadoProceso estado = proceso.getEstado();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodeId", estado.getId());
        data.put("isCoordinator", estado.isEsCoordinador());
        data.put("coordinatorId", estado.getCoordinadorActual());
        data.put("active", estado.isActivo());

        List<Map<String, Object>> nodosList = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : estado.getNodos().entrySet()) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("id", entry.getKey());
            n.put("host", entry.getValue());
            n.put("isSelf", entry.getKey() == estado.getId());
            n.put("isCoordinator", entry.getKey() == estado.getCoordinadorActual());
            nodosList.add(n);
        }
        data.put("nodes", nodosList);

        responder(exchange, 200, toJson(data));
    }

    @SuppressWarnings("unchecked")
    private void handleRound(HttpExchange exchange) throws IOException {
        // Handle CORS preflight
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            responder(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = leerCuerpo(exchange);
        Map<String, Object> params = (Map<String, Object>) fromJson(body);

        int ronda = params.containsKey("ronda") ? ((Number) params.get("ronda")).intValue() : 1;
        Set<Integer> bizantinos = new HashSet<>();
        if (params.containsKey("byzantine")) {
            for (Object id : (List<Object>) params.get("byzantine")) {
                bizantinos.add(((Number) id).intValue());
            }
        }

        Map<String, Object> result = proceso.getConsensoManager().ejecutarRonda(ronda, bizantinos);
        responder(exchange, 200, toJson(result));
    }

    private void handleResults(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            responder(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        Map<String, Object> results = proceso.getConsensoManager().getUltimosResultados();
        if (results == null) {
            responder(exchange, 200, "{\"message\":\"No hay resultados aun\"}");
            return;
        }
        responder(exchange, 200, toJson(results));
    }

    // ================== JSON UTILS (sin dependencias) ==================

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":");
            sb.append(toJsonValue(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof String) return "\"" + escapeJson((String) value) + "\"";
        if (value instanceof Map) return toJson((Map<String, Object>) value);
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<Object>) value) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJsonValue(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(value.toString()) + "\"";
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @SuppressWarnings("unchecked")
    private Object fromJson(String json) {
        json = json.trim();
        if (json.startsWith("{")) return fromJsonMap(json);
        if (json.startsWith("[")) return fromJsonList(json);
        if (json.startsWith("\"")) return json.substring(1, json.length() - 1);
        if ("true".equals(json) || "false".equals(json)) return Boolean.parseBoolean(json);
        if ("null".equals(json)) return null;
        try {
            if (json.contains(".")) return Double.parseDouble(json);
            return Integer.parseInt(json);
        } catch (NumberFormatException e) {
            return json;
        }
    }

    private Map<String, Object> fromJsonMap(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        json = json.substring(1, json.lastIndexOf('}')).trim();
        if (json.isEmpty()) return map;

        List<String> tokens = tokenizeJson(json);
        for (String token : tokens) {
            int colonIdx = token.indexOf(':');
            String key = token.substring(0, colonIdx).trim();
            String val = token.substring(colonIdx + 1).trim();
            key = key.substring(1, key.length() - 1);
            map.put(key, fromJson(val));
        }
        return map;
    }

    private List<Object> fromJsonList(String json) {
        List<Object> list = new ArrayList<>();
        json = json.substring(1, json.lastIndexOf(']')).trim();
        if (json.isEmpty()) return list;

        List<String> tokens = tokenizeJson(json);
        for (String token : tokens) {
            list.add(fromJson(token.trim()));
        }
        return list;
    }

    private List<String> tokenizeJson(String s) {
        List<String> tokens = new ArrayList<>();
        int depth = 0;
        boolean inStr = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i-1) != '\\')) inStr = !inStr;
            if (!inStr) {
                if (c == '{' || c == '[') depth++;
                if (c == '}' || c == ']') depth--;
                if (c == ',' && depth == 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    // ================== HTTP UTILS ==================

    private String leerCuerpo(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private void responder(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
