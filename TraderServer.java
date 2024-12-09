import java.io.*;
import java.net.*;

public class TraderServer {
    private int port;
    private Trader trader;

    public TraderServer(int port, Trader trader) {
        this.port = port;
        this.trader = trader;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Trader server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                PeerHandler handler = new PeerHandler(clientSocket, trader);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}