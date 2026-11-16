package lodgefinance.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lodgefinance.database.Database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class StatusHandler implements HttpHandler {
    private final Database database;
    private final String page;

    public StatusHandler(Database database) throws IOException{
        this.database = database;
        try (InputStream input = StatusHandler.class.getResourceAsStream("/pages/index.html")) {
            if (input == null) {
                throw new IOException("Missing pages/index.html resource.");
            }
            page = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            System.out.println("Now handling " + path);
            if ("/".equals(path)) {
                handleRoot(exchange);
            } else {
                send(exchange, 404, "Page not found.", "text/plain");
            }
        } finally {
            exchange.close();
        }
    }

    private void handleRoot(HttpExchange exchange) throws IOException{
        String dbStatus = "Connected";
        String userCount = "Unavailable";
        String transactionCount = "Unavailable";
        int statusCode = 200;
        String sql = "SELECT (SELECT COUNT(*) FROM USERS) AS users, "
                + "(SELECT COUNT(*) FROM TRANSACTIONS) AS transactions";
        try (Connection conn = database.getConnection()){
            Statement stmt = conn.createStatement();
            ResultSet rows = stmt.executeQuery(sql);
            rows.next();
            userCount = Integer.toString(rows.getInt("users"));
            transactionCount = Integer.toString(rows.getInt("transactions"));
        } catch (SQLException exception) {
            dbStatus = "Unavailable";
            statusCode = 503;
            System.out.println("Failed: " + exception.getMessage());
        }

        String html = page.replace("{{databaseStatus}}", dbStatus)
                .replace("{{userCount}}", userCount)
                .replace("{{transactionCount}}", transactionCount);
        send(exchange, statusCode, html, "text/html");
    }

    private void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
