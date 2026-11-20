package lodgefinance;

import com.sun.net.httpserver.HttpServer;
import lodgefinance.database.Database;
import lodgefinance.web.WebHandler;
import lodgefinance.database.TransactionRepository;

import java.net.InetSocketAddress;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args)
    {
        System.out.println("Hello Lodge");
        Path databasePath = Path.of("data/lodge_finance.db");
        int port = 8080;

        //create database when launch
        try {
            Database db = new Database(databasePath);
            db.initialize();
            TransactionRepository repository = new TransactionRepository(db);

            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port),0);
            server.createContext("/", new WebHandler(db, repository));
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
            server.start();

            System.out.println("Database: " + databasePath.toAbsolutePath().normalize());
            System.out.println("Open http://localhost:" + server.getAddress().getPort() + "/");
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}