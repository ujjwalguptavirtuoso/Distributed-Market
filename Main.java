import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: java Main <config_file> <num_traders> <cache_mode>");
            System.exit(1);
        }

        String configFile = args[0];
        int numTraders = Integer.parseInt(args[1]);
        String cacheMode = args[2];

        List<String> peerAddresses = readConfigFile(configFile);
        int numPeers = peerAddresses.size();

        // Start Warehouse
        startProcess("Warehouse", "8000");

        // Start Traders
        for (int i = 0; i < numTraders; i++) {
            if("NO_CACHE".equals(cacheMode)){
                startProcess("Trader", String.valueOf(i), "localhost", "8000", String.valueOf(8001 + i));
            }else if("WITH_CACHE".equals(cacheMode)){
                List<String> traderAddresses = getTradersList(numTraders);
                startProcess("CachingTrader", String.valueOf(i), "localhost", "8000", String.valueOf(8001 + i), String.join(",", traderAddresses));
            }
        }

        // Start Peers (Buyers and Sellers)
        for (int i = 0; i < numPeers; i++) {
            String type = (i % 2 == 0) ? "BUYER" : "SELLER";
            List<String> traderAddresses = getTradersList(numTraders);
            startProcess("Peer", String.valueOf(i), type, String.join(",", traderAddresses));
        }
    }

    private static List<String> getTradersList(int numTraders){
        List<String> traderAddresses = new ArrayList<>();
        for (int j = 0; j < numTraders; j++) {
            traderAddresses.add("localhost:" + (8001 + j));
        }
        return traderAddresses;
    }

    private static void startProcess(String className, String... args) throws IOException {
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        command.add(".");  // Ensure the current directory is in the classpath
        command.add(className);
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(new File(className + "_" + args[0] + ".txt"));
        pb.redirectError(new File(className + "_" + args[0] + "_error.txt"));
        pb.start();
    }

    private static List<String> readConfigFile(String filename) {
        List<String> addresses = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addresses.add(line.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return addresses;
    }
}