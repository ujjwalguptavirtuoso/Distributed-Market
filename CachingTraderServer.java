import java.io.*;
import java.net.*;

public class CachingTraderServer {
    private int port;
    private CachingTrader cachingTrader;

    public CachingTraderServer(int port, CachingTrader cachingTrader) {
        this.port = port;
        this.cachingTrader = cachingTrader;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Caching Trader server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                CachingTraderPeerHandler handler = new CachingTraderPeerHandler(clientSocket, cachingTrader);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

