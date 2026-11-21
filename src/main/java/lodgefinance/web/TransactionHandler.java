package lodgefinance.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import lodgefinance.database.TransactionRepository;
import lodgefinance.model.Transaction;
import lodgefinance.service.TransactionManager;

public class TransactionHandler {
    private final TransactionRepository repository;
    private final TransactionManager manager;
    private final String transactionsPage;

    public TransactionHandler(TransactionRepository repository) throws IOException {
        this.repository = repository;
        this.manager = new TransactionManager(repository);
        this.transactionsPage = WebHandler.loadPage("/pages/transactions.html");

    }

    // main transaction url routing and handling
    public synchronized void handle(HttpExchange exchange, Integer userID) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.equals("/transactions")) {
            if (method.equals("GET")) {
                System.out.println(" handling /transactions in TransactionHandler.handle() ");
                showTransactionsPage(exchange);
            } else {
                WebHandler.methodNotAllowed(exchange, "GET");
            }
            return;
        }
        // @TODO edit, delete, undo handling
        if (path.equals("/transactions/add")){
            if (method.equals("POST")){
                handleAddTransaction(exchange, userID);
            } else {
                WebHandler.methodNotAllowed(exchange, "POST");
            }
            return;
        }
        WebHandler.send(exchange,404,"Transaction page not found", "text/plain");
    }



    private void showTransactionsPage(HttpExchange exchange) throws IOException {
        TransactionManager mngr = getManager();
        try {
            mngr.getTransaction();
            StringBuilder rows = new StringBuilder();
            for (Transaction transaction : mngr.linearSearchDescription("")) {
                rows.append("<tr>");
                rows.append("<td>").append(transaction.getTransactionID()).append("</td>");
                rows.append("<td>").append(transaction.getDate()).append("</td>");
                rows.append("<td>").append(WebHandler.escapeHtml(transaction.getDescription())).append("</td>");
                rows.append("<td>").append(WebHandler.escapeHtml(transaction.getType())).append("</td>");
                rows.append("<td>").append(transaction.getAmount()).append("</td>");
                rows.append("<td>").append(transaction.getUserID()).append("</td>");
                rows.append("<td><button type=\"button\" onclick=\"openEdit(this)\">Edit</button></td>");
                rows.append("</tr>");
            }
            // if the loop added nothing, show one message across all six columns
            if (rows.length() == 0){
                rows.append("<tr><td colspan=\"6\">No transactions yet.</td></tr>");
            }
            String html = transactionsPage.replace("{{transactionRows}}", rows.toString());
            WebHandler.send(exchange, 200, html, "text/html");
        } catch (SQLException e) {
            System.out.println("Cannot load transactions: " + e.getMessage());
            WebHandler.send(exchange, 503, "Unable to load transactions", "text/plain");
        }
    }

    private TransactionManager getManager(){
        return this.manager;
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new HashMap<>();
        for (String field : body.split("&")) {
            String[] parts = field.split("=", 2);
            if (parts.length == 2) {
                String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                form.put(name, value);
            }
        }
        return form;
    }

    public void handleAddTransaction(HttpExchange exchange, int userID) throws IOException{
        TransactionManager mngr = getManager();
        try {
            Map<String, String> form = readForm(exchange);
            LocalDate date = LocalDate.parse(form.getOrDefault("date", ""));
            String description = form.getOrDefault("description", "").trim();
            String type = form.getOrDefault("type", "");
            int amount = Integer.parseInt(form.getOrDefault("amount", ""));
            mngr.addTransaction(date, description, type, amount, userID);
        } catch (DateTimeParseException e) {
            WebHandler.send(exchange, 400, "Please enter a valid date", "text/plain");
            return;
        } catch (IllegalArgumentException e) {
            WebHandler.send(exchange, 400, "Please enter a description, choose Income or Expense, and use a positive amount",
                    "text/plain");
        return;
        } catch (SQLException e) {
        System.out.println("Could not add transaction: " + e.getMessage());
        WebHandler.send(exchange, 503, "Unable to save the transaction", "text/plain");
        return;
        }
        WebHandler.redirect(exchange, "/transactions");
    }
}
