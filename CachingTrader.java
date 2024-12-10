import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.*;

public class CachingTrader {
    private Warehouse warehouse;
    private ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();
    private Lock cacheLock = new ReentrantLock();
    private long lastSyncTime;
    private Queue<String> ledger = new ConcurrentLinkedQueue<>();

    public CachingTrader(Warehouse warehouse) {
        this.warehouse = warehouse;
        this.lastSyncTime = System.currentTimeMillis();
    }

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

    public void sellItem(String item) {
        cacheLock.lock();
        try {
            int count = cache.getOrDefault(item, 0);
            cache.put(item, count + 1);
            ledger.add("SELL " + item);
        } finally {
            cacheLock.unlock();
        }
    }

    public void synchronizeCache() {
        cacheLock.lock();
        try {
            // Fetch inventory from the warehouse
            Map<String, Integer> warehouseInventory = warehouse.getInventory();
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

    public void periodicSync() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::synchronizeCache, 0, 5, TimeUnit.SECONDS);
    }
}