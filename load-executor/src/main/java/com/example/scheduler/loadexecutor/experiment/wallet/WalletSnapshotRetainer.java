package com.example.scheduler.loadexecutor.experiment.wallet;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WalletSnapshotRetainer {

    private final Map<Long, Deque<WalletSnapshot>> perUserSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<WalletSnapshot> globalSnapshots = new ConcurrentLinkedQueue<>();
    private final AtomicLong globalCounter = new AtomicLong(0);

    public void retain(WalletSnapshot snapshot, int perUserLimit, int globalLimit) {
        if (snapshot == null) {
            return;
        }
        if (perUserLimit > 0) {
            Deque<WalletSnapshot> deque = perUserSnapshots.computeIfAbsent(snapshot.getUserId(), id -> new ArrayDeque<>(perUserLimit));
            synchronized (deque) {
                deque.addLast(snapshot);
                while (deque.size() > perUserLimit) {
                    deque.removeFirst();
                }
            }
        }
        if (globalLimit != 0) {
            globalSnapshots.add(snapshot);
            long size = globalCounter.incrementAndGet();
            if (globalLimit > 0) {
                while (size > globalLimit) {
                    WalletSnapshot removed = globalSnapshots.poll();
                    if (removed == null) {
                        break;
                    }
                    size = globalCounter.decrementAndGet();
                }
            }
        }
    }
}
