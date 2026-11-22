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
import java.util.List;

import lodgefinance.database.TransactionRepository;
import lodgefinance.model.Transaction;
import lodgefinance.model.User;
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
    public synchronized void handle(HttpExchange exchange, User user) throws IOException {
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
        if (path.equals("/transactions/add")){
            if (method.equals("POST")){
                handleAddTransaction(exchange, user.getUserID());
            } else {
                WebHandler.methodNotAllowed(exchange, "POST");
            }
            return;
        }
        if (path.equals("/transactions/update")) {
            if (method.equals("POST")) {
                handleUpdateTransaction(exchange, user.getUserID());
            } else {
                WebHandler.methodNotAllowed(exchange, "POST");
            }
            return;
        }
        if (path.equals("/transactions/delete")) {
            if (method.equals("POST")) {
                handleDeleteTransaction(exchange, user);
            } else {
                WebHandler.methodNotAllowed(exchange, "POST");
            }
            return;
        }
        if (path.equals("/transactions/undo")) {
            if (method.equals("POST")) {
                handleUndoDelete(exchange, user);
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
            String keyword = "";
            String sortField = "amount";
            boolean ascending = true;
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) {
                String[] fields = query.split("&");
                for (int i = 0; i < fields.length; i++) {
                    String field = fields[i];
                    if (field.startsWith("search=")) {
                        String value = field.substring("search=".length());
                        keyword = URLDecoder.decode(value, StandardCharsets.UTF_8).trim();
                    } else if (field.equals("sort=id-asc")) {
                        sortField = "id";
                    } else if (field.equals("sort=id-desc")) {
                        sortField = "id";
                        ascending = false;
                    } else if (field.equals("sort=amount-desc")) {
                        ascending = false;
                    } else if (field.equals("sort=date-asc")) {
                        sortField = "date";
                        ascending = true;
                    } else if (field.equals("sort=date-desc")) {
                        sortField = "date";
                        ascending = false;
                    }
                }
            }
            mngr.getTransaction();
            StringBuilder rows = new StringBuilder();
            List<Transaction> matches = mngr.linearSearchDescription(keyword);
            List<Transaction> sorted = mngr.sortTransactions(matches, sortField, ascending);

            for (int i = 0; i < sorted.size(); i++) {
                Transaction transaction = sorted.get(i);
                rows.append("<tr>");
                rows.append("<td>").append(transaction.getTransactionID()).append("</td>");
                rows.append("<td>").append(transaction.getDate()).append("</td>");
                rows.append("<td>").append(WebHandler.escapeHtml(transaction.getDescription())).append("</td>");
                rows.append("<td>").append(WebHandler.escapeHtml(transaction.getType())).append("</td>");
                rows.append("<td>").append(transaction.getAmount()).append("</td>");
                rows.append("<td>").append(transaction.getUserID()).append("</td>");
                rows.append("<td>");
                rows.append("<button type=\"button\" onclick=\"openEdit(this)\">Edit</button> ");
                rows.append("<button type=\"button\" onclick=\"openDelete(this)\">Delete</button>");
                rows.append("</td>");
                rows.append("</tr>");
            }
            // if the loop added nothing, show one message across all six columns
            if (rows.length() == 0){
                rows.append("<tr><td colspan=\"6\">No transactions yet.</td></tr>");
            }
            String html = transactionsPage
                    .replace("{{searchText}}", WebHandler.escapeHtml(keyword))
                    .replace("{{transactionRows}}", rows.toString());
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
            WebHandler.send(exchange, 400, "Please enter a description, choose Income or Expense, and use "
                    + "a positive amount", "text/plain");
        return;
        } catch (SQLException e) {
        System.out.println("Could not add transaction: " + e.getMessage());
        WebHandler.send(exchange, 503, "Unable to save the transaction", "text/plain");
        return;
        }
        WebHandler.redirect(exchange, "/transactions");
    }

    private void handleUpdateTransaction(HttpExchange exchange, int userID) throws IOException{
        try {
            Map<String, String> form = readForm(exchange);
            int transactionID = Integer.parseInt(form.getOrDefault("id", ""));
            LocalDate date = LocalDate.parse(form.getOrDefault("date", ""));
            String description = form.getOrDefault("description", "");
            String type = form.getOrDefault("type", "");
            int amount = Integer.parseInt(form.getOrDefault("amount", ""));
            boolean updated = manager.updateTransaction(transactionID,date,description,type,amount,userID);
            if(!updated) {
                WebHandler.send(exchange, 404, "This transaction no longer exists", "text/plain");
                return;
            }
        } catch (DateTimeParseException e){
            WebHandler.send(exchange, 400, "Enter a valid date!", "text/plain");
            return;
        } catch (NumberFormatException e) {
            WebHandler.send(exchange, 400, "Transaction ID and amount must be WHOLE numbers",
                    "text/plain");
            return;
        } catch (IllegalArgumentException e) {
            WebHandler.send(exchange, 400, "Enter a description, choose Income or Expense, and use a " +
                            "positive amount", "text/plain");
            return;
        } catch (SQLException e) {
            System.out.println("Could not update transaction: " + e.getMessage());
            WebHandler.send(exchange, 503, "Unable to update the transaction", "text/plain");
            return;
        }
        WebHandler.redirect(exchange, "/transactions");
    }

    private void handleDeleteTransaction(HttpExchange exchange, User user) throws IOException {
        try {
            Map<String, String> form = readForm(exchange);
            int transactionID = Integer.parseInt(form.getOrDefault("id", ""));
            if (!manager.deleteTransaction(transactionID, user)) {
                WebHandler.send(exchange, 404, "This transaction no longer exists", "text/plain");
                return;
            }
        } catch (IllegalArgumentException e){
            WebHandler.send(exchange, 400, "Invalid transaction ID", "text/plain");
            return;
        } catch (SQLException e) {
            System.out.println("Could not delete transaction: " + e.getMessage());
            WebHandler.send(exchange, 503, "Unable to delete the transaction", "text/plain");
            return;
        }
        WebHandler.redirect(exchange, "/transactions");
    }

    private void handleUndoDelete(HttpExchange exchange, User user) throws IOException {
        try {
            boolean restored = manager.undoDelete(user);
            if (!restored) {
                WebHandler.send(exchange, 409, "There are no deleted transactions to restore",
                        "text/plain");
                return;
            }
        } catch (SQLException e) {
            System.out.println("Undo failed: " + e.getMessage());
            WebHandler.send(exchange, 503, "Unable to complete undo", "text/plain");
            return;
        }
        WebHandler.redirect(exchange, "/transactions");
    }
}
