package com.midnightmunchies.application.dash;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** Normal API requests share access; a reset waits for them and then owns the state exclusively. */
@Component
public class SimulationAccess {
    private final ReentrantReadWriteLock access = new ReentrantReadWriteLock(true);
    private final AtomicBoolean resetting = new AtomicBoolean();
    private final AtomicLong revision = new AtomicLong();
    private volatile boolean recoveryRequired;

    public Lock openRequest(boolean allowRecovery) {
        long before = revision.get();
        if (resetting.get()) throw new UnavailableException(false);
        Lock read = access.readLock();
        read.lock();
        // A request that raced with reset must not execute afterward with its old command data.
        if (resetting.get() || before != revision.get()) {
            read.unlock();
            throw new UnavailableException(false);
        }
        if (recoveryRequired && !allowRecovery) {
            read.unlock();
            throw new UnavailableException(true);
        }
        return read;
    }

    public <T> T reset(Supplier<T> operation) {
        if (!resetting.compareAndSet(false, true)) throw new UnavailableException(false);
        revision.incrementAndGet();
        Lock write = access.writeLock();
        write.lock();
        try {
            return operation.get();
        } finally {
            write.unlock();
            resetting.set(false);
        }
    }

    void requireRecovery() { recoveryRequired = true; }
    void restored() { recoveryRequired = false; }
    long revision() { return revision.get(); }

    public static class UnavailableException extends RuntimeException {
        private final boolean recoveryRequired;

        public UnavailableException(boolean recoveryRequired) {
            super(recoveryRequired
                    ? "The simulator requires a successful reset before other commands can run."
                    : "The simulator is resetting. Please retry your request after reset completes.");
            this.recoveryRequired = recoveryRequired;
        }

        public boolean recoveryRequired() { return recoveryRequired; }
    }
}
