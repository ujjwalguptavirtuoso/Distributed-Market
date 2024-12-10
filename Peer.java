import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Peer {
    private int id;
    private String type; // "BUYER" or "SELLER"
    private List<String> traderAddresses;
    private int currentTraderIndex;
    private ScheduledExecutorService executor;
    private static final int Ng = 5; // Number of goods to generate
    private static final int Tg = 10; // Time interval in seconds to generate goods

    public Peer(int id, String type, List<String> traderAddresses) {
        this.id = id;
        this.type = type;
        this.traderAddresses = traderAddresses;
        this.currentTraderIndex = 0;
        this.executor = Executors.newScheduledThreadPool(1);
    }

    public void start() {
        if (type.equals("SELLER")) {
            startSellingProcess();
        } else if (type.equals("BUYER")) {
            startBuyingProcess();
        }
    }

    private void startSellingProcess() {
        executor.scheduleAtFixedRate(this::generateAndSellGoods, 0, Tg, TimeUnit.SECONDS);
    }

    private void generateAndSellGoods() {
        String item = generateRandomItem();
        for (int i = 0; i < Ng; i++) {
            sellItem(item);
        }
    }

    private void startBuyingProcess() {
        executor.scheduleAtFixedRate(this::buyRandomItem, 0, 5, TimeUnit.SECONDS);
    }

    private void sellItem(String item) {
        String traderAddress = getRandomTraderAddress();
        try (Socket socket = new Socket(traderAddress, 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject("SELL," + item);
            boolean success = in.readBoolean();
            System.out.println("Seller " + id + " " + (success ? "sold" : "failed to sell") + " " + item);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void buyRandomItem() {
        String item = generateRandomItem();
        String traderAddress = getRandomTraderAddress();
        try (Socket socket = new Socket(traderAddress, 8080);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            
            out.writeObject("BUY," + item);
            boolean success = in.readBoolean();
            System.out.println("Buyer " + id + " " + (success ? "bought" : "failed to buy") + " " + item);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getRandomTraderAddress() {
        currentTraderIndex = (currentTraderIndex + 1) % traderAddresses.size();
        return traderAddresses.get(currentTraderIndex);
    }

    private String generateRandomItem() {
        String[] items = {"Salt", "Fish", "Boar", "Mead"};
        return items[new Random().nextInt(items.length)];
    }

    public void shutdown() {
        executor.shutdown();
    }
}