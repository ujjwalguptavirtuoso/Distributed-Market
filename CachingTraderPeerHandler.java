import java.io.*;
import java.net.*;

public class CachingTraderPeerHandler implements Runnable {
    private Socket socket;
    private CachingTrader cachingTrader;  // CachingTrader class
    private PrintWriter out;
    private BufferedReader in;

    public CachingTraderPeerHandler(Socket socket, CachingTrader cachingTrader) {
        this.socket = socket;
        this.cachingTrader = cachingTrader;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] parts = inputLine.split(" ");
                String action = parts[0];

                if ("ARE_YOU_THERE?".equals(inputLine)) {
                    out.println("I'M_ALIVE");
                } else {
                    String item = parts[1];
                    if ("BUY".equals(action)) {
                        boolean success = cachingTrader.buyItem(item);
                        out.println(success ? "Purchase successful" : "Purchase failed");
                    } else if ("SELL".equals(action)) {
                        cachingTrader.sellItem(item);
                        out.println("Item sold successfully");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

