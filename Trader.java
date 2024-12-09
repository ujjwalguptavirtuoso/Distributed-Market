import java.util.concurrent.locks.*;

public class Trader {
    private Warehouse warehouse;
    private Lock lock = new ReentrantLock();

    public Trader(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public synchronized boolean buyItem(String item) {
        lock.lock();
        try {
            return warehouse.buyItem(item);
        } finally {
            lock.unlock();
        }
    }

    public synchronized void sellItem(String item) {
        lock.lock();
        try {
            warehouse.sellItem(item);
        } finally {
            lock.unlock();
        }
    }
}