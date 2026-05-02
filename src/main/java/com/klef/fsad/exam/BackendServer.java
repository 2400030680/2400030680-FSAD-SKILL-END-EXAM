package com.klef.fsad.exam;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BackendServer {

    private static final Gson gson = new Gson();
    private static SessionFactory sessionFactory;
    private static DatabaseSettings databaseSettings = new DatabaseSettings();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/departments", BackendServer::handleDepartments);
        server.createContext("/settings", BackendServer::handleSettings);
        server.setExecutor(null);
        server.start();

        System.out.println("Backend server started at http://localhost:8080");
    }

    private static void handleDepartments(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 204, "");
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                insertDepartment(exchange);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("message", "Database error: " + e.getMessage()));
            }
            return;
        }

        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            try {
                deleteDepartment(exchange);
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("message", "Database error: " + e.getMessage()));
            }
            return;
        }

        sendJson(exchange, 405, Map.of("message", "Method not allowed"));
    }

    private static void handleSettings(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 204, "");
            return;
        }

        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            Map<String, Object> response = new HashMap<>();
            response.put("databaseName", databaseSettings.databaseName);
            response.put("username", databaseSettings.username);
            response.put("connected", sessionFactory != null && !sessionFactory.isClosed());
            sendJson(exchange, 200, response);
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            DatabaseSettings newSettings = gson.fromJson(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8),
                    DatabaseSettings.class
            );

            try {
                databaseSettings = newSettings.withDefaults();
                resetSessionFactory();
                getSessionFactory();
            } catch (Exception e) {
                resetSessionFactory();
                sendJson(exchange, 500, Map.of("message", "Could not connect to MySQL: " + e.getMessage()));
                return;
            }

            sendJson(exchange, 200, Map.of("message", "Database settings saved successfully"));
            return;
        }

        sendJson(exchange, 405, Map.of("message", "Method not allowed"));
    }

    private static void insertDepartment(HttpExchange exchange) throws IOException {
        Department department = gson.fromJson(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8),
                Department.class
        );

        Session session = getSessionFactory().openSession();
        Transaction transaction = session.beginTransaction();
        session.persist(department);
        transaction.commit();
        session.close();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Department inserted successfully");
        response.put("id", department.getId());

        sendJson(exchange, 201, response);
    }

    private static void deleteDepartment(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");

        if (parts.length < 3) {
            sendJson(exchange, 400, Map.of("message", "Department ID is required"));
            return;
        }

        int departmentId;

        try {
            departmentId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, Map.of("message", "Department ID must be a number"));
            return;
        }

        Session session = getSessionFactory().openSession();
        Transaction transaction = session.beginTransaction();
        Department department = session.get(Department.class, departmentId);

        if (department == null) {
            transaction.commit();
            session.close();
            sendJson(exchange, 404, Map.of("message", "Department not found"));
            return;
        }

        session.remove(department);
        transaction.commit();
        session.close();

        sendJson(exchange, 200, Map.of("message", "Department deleted successfully"));
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        sendResponse(exchange, statusCode, gson.toJson(data));
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static SessionFactory getSessionFactory() {
        if (sessionFactory == null || sessionFactory.isClosed()) {
            Configuration configuration = new Configuration();
            configuration.configure("hibernate.cfg.xml");
            configuration.setProperty("hibernate.connection.url", databaseSettings.getUrl());
            configuration.setProperty("hibernate.connection.username", databaseSettings.username);
            configuration.setProperty("hibernate.connection.password", databaseSettings.password);
            sessionFactory = configuration.buildSessionFactory();
        }

        return sessionFactory;
    }

    private static void resetSessionFactory() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            sessionFactory.close();
        }

        sessionFactory = null;
    }

    private static class DatabaseSettings {
        private String databaseName = "fsadendexam";
        private String username = "root";
        private String password = "root";

        private String getUrl() {
            return "jdbc:mysql://localhost:3306/" + databaseName;
        }

        private DatabaseSettings withDefaults() {
            if (databaseName == null || databaseName.isBlank()) {
                databaseName = "fsadendexam";
            }

            if (username == null || username.isBlank()) {
                username = "root";
            }

            if (password == null) {
                password = "";
            }

            return this;
        }
    }
}
