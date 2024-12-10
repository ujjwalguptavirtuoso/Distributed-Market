import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class Warehouse {
    private static final String INVENTORY_FILE = "inventory.txt";
    private ServerSocket serverSocket;
    private ExecutorService executor;

    public Warehouse(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        executor = Executors.newFixedThreadPool(10);
        System.out.println("Warehouse started on port " + port);
    }

    public void start() {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
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

            if (action.equals("BUY")) {
                boolean success = buyItem(item);
                out.writeBoolean(success);
            } else if (action.equals("SELL")) {
                sellItem(item);
                out.writeBoolean(true);
            } else if (action.equals("GET_INVENTORY")) {
                Map<String, Integer> inventory = getInventory();
                out.writeObject(mapToString(inventory));
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String mapToString(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append(",");
        }
        return sb.toString();
    }
    private ConcurrentHashMap<String, Lock> itemLocks = new ConcurrentHashMap<>();

    public synchronized boolean buyItem(String item) {
        Lock itemLock = itemLocks.computeIfAbsent(item, k -> new ReentrantLock());
        itemLock.lock();
        try {
            int count = readItemCount(item);
            if (count > 0) {
                writeItemCount(item, count - 1);
                return true;
            }
            return false;
        } finally {
            itemLock.unlock();
        }
    }

    public synchronized void sellItem(String item) {
        Lock itemLock = itemLocks.computeIfAbsent(item, k -> new ReentrantLock());
        itemLock.lock();
        try {
            int count = readItemCount(item);
            writeItemCount(item, count + 1);
        } finally {
            itemLock.unlock();
        }
    }

    private int readItemCount(String item) {
        try (BufferedReader reader = new BufferedReader(new FileReader(INVENTORY_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(item)) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private void writeItemCount(String item, int count) {
        try {
            File inputFile = new File(INVENTORY_FILE);
            // Check if the inventory file exists, create it if it doesn't
            if (!inputFile.exists()) {
                inputFile.createNewFile();
            }

            File tempFile = new File("temp_inventory.txt");

            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));

            String line;
            boolean itemFound = false;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts[0].equals(item)) {
                    writer.write(item + "," + count);
                    itemFound = true;
                } else {
                    writer.write(line);
                }
                writer.newLine();
            }

            if (!itemFound) {
                writer.write(item + "," + count);
                writer.newLine();
            }

            writer.close();
            reader.close();

            if (!inputFile.delete()) {
                throw new IOException("Could not delete original inventory file");
            }
            if (!tempFile.renameTo(inputFile)) {
                throw new IOException("Could not rename temp file");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized Map<String, Integer> getInventory() {
    Map<String, Integer> inventory = new HashMap<>();
    try (BufferedReader reader = new BufferedReader(new FileReader(INVENTORY_FILE))) {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts.length == 2) {
                String item = parts[0];
                int count = Integer.parseInt(parts[1]);
                inventory.put(item, count);
            }
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
    return inventory;
}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Warehouse <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        try {
            Warehouse warehouse = new Warehouse(port);
            warehouse.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}