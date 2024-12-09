import java.io.*;
import java.net.*;

public class PeerHandler implements Runnable {
    private Socket socket;
    private Trader trader;
    private PrintWriter out;
    private BufferedReader in;

    public PeerHandler(Socket socket, Trader trader) {
        this.socket = socket;
        this.trader = trader;
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
                String item = parts[1];

                if (action.equals("BUY")) {
                    boolean success = trader.buyItem(item);
                    out.println(success ? "Purchase successful" : "Purchase failed");
                } else if (action.equals("SELL")) {
                    trader.sellItem(item);
                    out.println("Item sold successfully");
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