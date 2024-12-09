import java.io.*;
import java.net.*;

public class Peer {
    private String type; // "buyer" or "seller"
    private String item;
    private int port;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public Peer(String type, String item, int port) {
        this.type = type;
        this.item = item;
        this.port = port;
    }

    public void start() {
        try {
            socket = new Socket("localhost", port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            if (type.equals("buyer")) {
                buyItem();
            } else if (type.equals("seller")) {
                sellItem();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void buyItem() throws IOException {
        out.println("BUY " + item);
        String response = in.readLine();
        System.out.println("Buyer: " + response);
    }

    private void sellItem() throws IOException {
        out.println("SELL " + item);
        String response = in.readLine();
        System.out.println("Seller: " + response);
    }
}