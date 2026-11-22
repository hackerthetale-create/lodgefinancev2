package lodgefinance.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import lodgefinance.database.Database;
import lodgefinance.model.User;
import lodgefinance.database.TransactionRepository;
import lodgefinance.model.Transaction;
import lodgefinance.service.TransactionManager;

public class WebHandler implements HttpHandler {
    private final Database database;
    private final String loginPage;
    private final String indexPage;
    private final TransactionHandler transactionHandler;
    private final Map<String, User> sessions = new HashMap<>();
    private final SecureRandom random = new SecureRandom();

    public WebHandler(Database database, TransactionRepository transactionRepository) throws IOException {
        this.database = database;
        this.transactionHandler = new TransactionHandler(transactionRepository);
        this.loginPage = loadPage("/pages/login.html");
        this.indexPage = loadPage("/pages/index.html");
    }

    // the main url routing method
    @Override
    public synchronized void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            System.out.println("Now handling " + path);

            if ("/login".equals(path)) {
                if ("GET".equals(method)) {
                    System.out.println("Now handling GET " + path);
                    showLoginPage(exchange);
                } else if ("POST".equals(method)) {
                    System.out.println("Now handling POST " + path);
                    handleLogin(exchange);
                }
            } else if ("/".equals(path)) {
                // redirect to /login if not logged in, show index page if already logged in
                String token = getToken(exchange);
                User user = sessions.get(token);

                if (user == null) {
                    System.out.println("user not logged in, goto login page first");
                    redirect(exchange, "/login");
                } else {
                    showIndexPage(exchange);
                }
            }
            else if ("/logout".equals(path)) {
                handleLogout(exchange);
            } else if(path!=null && path.startsWith("/transactions")) {

                String token = WebHandler.getToken(exchange);
                User user = sessions.get(token);

                if (user == null) {
                    WebHandler.redirect(exchange, "/login");
                    return;
                }

                transactionHandler.handle(exchange, user);
            }

            else {
                send(exchange, 404, "Page not found.", "text/plain");
            }

        } finally {
            exchange.close();
        }
    } // end of public synchronized void handle(HttpExchange exchange)



    //--- login handling and cookie handling methods
    private void showLoginPage (HttpExchange exchange) throws IOException {
        String token = getToken(exchange);
        if (sessions.containsKey(token)){
            System.out.println("already logged in, no need to log in again, goto index page");
            redirect(exchange, "/");
            return;
        }
        sendLoginPage(exchange, 200, "");
    }

    private void sendLoginPage(HttpExchange exchange, int status, String msg) throws IOException{
        String html = loginPage.replace("{{errorMessage}}", escapeHtml(msg));
        send(exchange, status, html, "text/html");
    }

    private void handleLogin(HttpExchange exchange) throws IOException{
        byte[] bytes = exchange.getRequestBody().readNBytes(8193);

        Map<String, String> form = new HashMap<>();
        try {
            for (String field : new String(bytes, StandardCharsets.UTF_8).split("&")) {
                String[] parts = field.split("=", 2);
                if (parts.length == 2) {
                    form.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                }
            }
        } catch (IllegalArgumentException e) {
            sendLoginPage(exchange, 400, "Invalid form data.");
            return;
        }

        String username = form.getOrDefault("username", "").trim();
        String password = form.getOrDefault("password", "");
        if (username.isEmpty() || password.isEmpty()){
            sendLoginPage(exchange, 300, "Please enter username and password!");
            return;
        }
        try {
            User user = database.checkLogin(username, password);
            if (user == null) {
                sendLoginPage(exchange, 401, "Incorrect username or password");
                return;
            }
            String prevToken = getToken(exchange);
            sessions.remove(prevToken); //remove previous token from browser
            String token = generateToken();
            sessions.put(token, user); //mark which user owns this token
            System.out.println(" put token " + token +" for user "
                    + user.getUsername()
                    + " with userID " + user.getUserID());

            // save token in a cookie and send back to the browser
            exchange.getResponseHeaders().add("Set-Cookie", "sessionToken=" + token + "; Path=/; HttpOnly; SameSite=Strict");
            redirect(exchange, "/");
        } catch (SQLException e){
            System.out.println("Login database error: " + e.getMessage());
            sendLoginPage(exchange, 503, "Unable to log in right now, try again later");
        }
    }

    private void showIndexPage(HttpExchange exchange)  throws IOException {
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

        String token = getToken(exchange);
        User user = sessions.get(token);

        String html = indexPage.replace("{{username}}", user.getUsername())
                .replace("{{databaseStatus}}", dbStatus)
                .replace("{{userCount}}", userCount)
                .replace("{{transactionCount}}", transactionCount);
        send(exchange, statusCode, html, "text/html");
    }

    private void handleLogout(HttpExchange exchange)  throws IOException {
        String token = getToken(exchange);

        // delete token from sessions
        sessions.remove(token);
        //delete cookie from browser
        clearCookie(exchange);
        redirect(exchange, "/login");
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }


    private void clearCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add(
                "Set-Cookie",
                "sessionToken=; Path=/; Max-Age=0;"
                        + " HttpOnly; SameSite=Strict");
    }
    //---end login handling and cookie handling methods


    //---- utility methods , public static
    public static String getToken(HttpExchange exchange) {
        String cookieHeader =
                exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) {
            return null;
        }

        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);

            if (parts.length == 2
                    && "sessionToken".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }

    public static String loadPage(String path) throws IOException {
        try (InputStream input = WebHandler.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing pages: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public static void methodNotAllowed(HttpExchange exchange, String allowedMethod)
            throws IOException {
        exchange.getResponseHeaders().set("Allow", allowedMethod);
        WebHandler.send(exchange, 405, "Method not allowed.", "text/plain");
    }

    public static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(303, -1);
    }

    public static void send(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }


    //----  end  utility methods , public static
}
