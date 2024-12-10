import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;
import java.net.*;
import java.io.*;

public class CachingTrader {
    private int id;
    private String warehouseHost;
    private int warehousePort;
    private ServerSocket serverSocket;
    private ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();
    private Lock cacheLock = new ReentrantLock();
    private long lastSyncTime;
    private Queue<String> ledger = new ConcurrentLinkedQueue<>();

    // Heartbeat-related fields
    private List<Integer> traderPorts;
    private volatile boolean otherTraderAlive = true;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private int heartbeatTimeout = 5000; // 5 seconds timeout for heartbeat response

    public CachingTrader(int id, String warehouseHost, int warehousePort, int traderPort, List<Integer> traderPorts) throws IOException {
        this.id = id;
        this.warehouseHost = warehouseHost;
        this.warehousePort = warehousePort;
        this.serverSocket = new ServerSocket(traderPort);
        this.traderPorts = traderPorts;
        this.lastSyncTime = System.currentTimeMillis();
        System.out.println("Trader " + id + " started on port " + traderPort);
        periodicSync();
        startHeartbeat();
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

            // Check if the message is the heartbeat message
            if ("ARE_YOU_THERE?".equals(command)) {
                // Respond with "I'M_ALIVE" for heartbeat
                out.writeObject("I'M_ALIVE");
            } else {
                // Process BUY and SELL actions
                String[] parts = command.split(",");
                String action = parts[0];
                String item = parts[1];

                boolean success = false;
                if (action.equals("BUY")) {
                    success = buyItem(item);
                } else if (action.equals("SELL")) {
                    success = sellItem(item);
                }
                // Send the success status back to the client
                out.writeBoolean(success);
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // Using cache to perform buys
    public boolean buyItem(String item) {
        cacheLock.lock();
        try {
            int count = cache.getOrDefault(item, 0);
            if (count > 0) {
                cache.put(item, count - 1);
                ledger.add("BUY " + item);
                return true;
            }
            return false;
        } finally {
            cacheLock.unlock();
        }
    }

    // Sell is still done to the warehouse
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

    // Synchronize cache with warehouse via socket communication
    public void synchronizeCache() {
        cacheLock.lock();
        try {
            // Fetch inventory from the warehouse via socket communication
            Map<String, Integer> warehouseInventory = getInventoryFromWarehouse();

            // Merge with local cache
            for (Map.Entry<String, Integer> entry : warehouseInventory.entrySet()) {
                cache.put(entry.getKey(), entry.getValue());
            }

            // Clear the ledger after sync
            ledger.clear();
            lastSyncTime = System.currentTimeMillis();
        } finally {
            cacheLock.unlock();
        }
    }

    private Map<String, Integer> getInventoryFromWarehouse() {
        Map<String, Integer> inventory = new HashMap<>();

        try (Socket socket = new Socket(warehouseHost, warehousePort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            // Send request for inventory data
            out.writeObject("GET_INVENTORY");

            // Receive the inventory string from the warehouse
            String inventoryString = (String) in.readObject();
            inventory = stringToMap(inventoryString);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        return inventory;
    }

    private Map<String, Integer> stringToMap(String str) {
        Map<String, Integer> map = new HashMap<>();
        String[] entries = str.split(",");
        for (String entry : entries) {
            if (!entry.isEmpty()) {
                String[] keyValue = entry.split(":");
                map.put(keyValue[0], Integer.parseInt(keyValue[1]));
            }
        }
        return map;
    }

    public void periodicSync() {
        scheduler.scheduleAtFixedRate(this::synchronizeCache, 0, 5, TimeUnit.SECONDS);
    }

    // Heartbeat methods
    private void startHeartbeat() {
        // Run heartbeat check for each trader in the list
        for (int port : traderPorts) {
            scheduler.scheduleAtFixedRate(() -> checkHeartbeat(port), 0, 3, TimeUnit.SECONDS);
        }
    }

    // Check heartbeat for another trader
    private void checkHeartbeat(int traderPort) {
        try (Socket socket = new Socket()) {
            // Set a 5-second timeout for the connection and reading data
            socket.connect(new InetSocketAddress("localhost", traderPort), 5000); // 5 seconds timeout for connection
            socket.setSoTimeout(heartbeatTimeout);  // 5 seconds timeout for reading the response

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                 ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                // Send heartbeat message
                System.out.println("Sending heartbeat to trader at port " + traderPort);
                out.writeObject("ARE_YOU_THERE?");

                // Wait for response from the other trader
                String response = (String) in.readObject();

                if ("I'M_ALIVE".equals(response)) {
                    otherTraderAlive = true;
                } else {
                    otherTraderAlive = false;
                }
            } catch (SocketTimeoutException e) {
                // Handle the case where the response is not received in time (timeout)
                System.out.println("Heartbeat response timed out from trader on port " + traderPort);
                otherTraderAlive = false;
            } catch (IOException | ClassNotFoundException e) {
                // Handle any other exceptions during communication
                otherTraderAlive = false;
            }
        } catch (IOException e) {
            // Handle the case where the socket cannot connect (e.g., trader not running)
            System.out.println("Failed to connect to trader on port " + traderPort);
            otherTraderAlive = false;
        }

        if (!otherTraderAlive) {
            System.out.println("Trader on port " + traderPort + " is assumed to have failed. Taking over responsibilities.");
            takeOverResponsibilities();
        }
    }

    // If another trader fails, the current trader takes over the responsibilities
    private void takeOverResponsibilities() {
        // Notify peers that this trader is now the active trader
        // This could be a network call to inform all connected clients
        System.out.println("Now I am the active trader.");
    }

    public static void main(String[] args) {
        // Check if the correct number of arguments is passed
        if (args.length < 5) {
            System.out.println("Usage: java CachingTrader <id> <warehouseHost> <warehousePort> <traderPort> <traderAddresses>");
            System.exit(1);
        }

        // Parse arguments
        int id = Integer.parseInt(args[0]);
        String warehouseHost = args[1];
        int warehousePort = Integer.parseInt(args[2]);
        int traderPort = Integer.parseInt(args[3]);

        // Parse the list of trader addresses
        List<Integer> traderPorts = new ArrayList<>();
        String[] traderAddressesArray = args[4].split(",");
        for (String address : traderAddressesArray) {
            String[] parts = address.split(":");
            traderPorts.add(Integer.parseInt(parts[1]));
        }

        try {
            // Create the CachingTrader instance and start it
            CachingTrader cachingTrader = new CachingTrader(id, warehouseHost, warehousePort, traderPort, traderPorts);
            cachingTrader.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
