import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

public class Warehouse {
    private ConcurrentHashMap<String, Integer> inventory = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Lock> itemLocks = new ConcurrentHashMap<>();

    public boolean buyItem(String item) {
        Lock itemLock = itemLocks.computeIfAbsent(item, k -> new ReentrantLock());
        itemLock.lock();
        try {
            int count = inventory.getOrDefault(item, 0);
            if (count > 0) {
                inventory.put(item, count - 1);
                return true;
            }
            return false;
        } finally {
            itemLock.unlock();
        }
    }

    public void sellItem(String item) {
        Lock itemLock = itemLocks.computeIfAbsent(item, k -> new ReentrantLock());
        itemLock.lock();
        try {
            int count = inventory.getOrDefault(item, 0);
            inventory.put(item, count + 1);
        } finally {
            itemLock.unlock();
        }
    }

    public Map<String, Integer> getInventory() {
        return new HashMap<>(inventory);
    }
}