import java.io.*;
import java.net.*;
import java.util.*;

public class Trader {
    private int id;
    private String warehouseHost;
    private int warehousePort;
    private ServerSocket serverSocket;

    public Trader(int id, String warehouseHost, int warehousePort, int traderPort) throws IOException {
        this.id = id;
        this.warehouseHost = warehouseHost;
        this.warehousePort = warehousePort;
        this.serverSocket = new ServerSocket(traderPort);
        System.out.println("Trader " + id + " started on port " + traderPort);
    }

    public void start() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
            ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())
        ) {
            String command = (String) in.readObject();
            String[] parts = command.split(",");
            String action = parts[0];
            String item = parts[1];

            boolean success = false;
            if (action.equals("BUY")) {
                success = buyItem(item);
            } else if (action.equals("SELL")) {
                success = sellItem(item);
            }
            out.writeBoolean(success);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public boolean buyItem(String item) {
        try (Socket socket = new Socket(warehouseHost, warehousePort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.writeObject("BUY," + item);
            return in.readBoolean();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean sellItem(String item) {
        try (Socket socket = new Socket(warehouseHost, warehousePort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            out.writeObject("SELL," + item);
            return in.readBoolean();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}