package in.kevinj.lancamp.model;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Central place to access all used threads except the Swing EDT.
 */
public class Dispatchers {
	public static final Dispatchers instance = new Dispatchers();

	private final ReadWriteLock lock;
	private boolean stopped;
	private ScheduledExecutorService timerDispatcher;
	private ExecutorService jsDispatcher;
	private ExecutorService networkIoDispatcher;
	private ExecutorService fileIoDispatcher;
	private ExecutorService pollingDispatcher;

	private Dispatchers() {
		lock = new ReentrantReadWriteLock();
		initDispatchers();
	}

	private void initDispatchers() {
		stopped = false;
		//assuming not many tasks will be submitted
		timerDispatcher = Executors.newSingleThreadScheduledExecutor();
		//assuming JSObject.getWindow() is not thread-safe
		jsDispatcher = Executors.newSingleThreadExecutor();
		//assuming submitted tasks complete in long amount of time
		networkIoDispatcher = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		//assuming submitted tasks complete in short amount of time - do not allow two threads to access the same file at once
		fileIoDispatcher = Executors.newSingleThreadExecutor();
		//assuming submitted tasks run indefinitely
		pollingDispatcher = Executors.newCachedThreadPool();
	}

	public ScheduledExecutorService getTimerDispatcher() {
		lock.readLock().lock();
		try {
			return timerDispatcher;
		} finally {
			lock.readLock().unlock();
		}
	}

	public ExecutorService getJsDispatcher() {
		lock.readLock().lock();
		try {
			return jsDispatcher;
		} finally {
			lock.readLock().unlock();
		}
	}

	public ExecutorService getNetworkIoDispatcher() {
		lock.readLock().lock();
		try {
			return networkIoDispatcher;
		} finally {
			lock.readLock().unlock();
		}
	}

	public ExecutorService getFileIoDispatcher() {
		lock.readLock().lock();
		try {
			return fileIoDispatcher;
		} finally {
			lock.readLock().unlock();
		}
	}

	public ExecutorService getPollingDispatcher() {
		lock.readLock().lock();
		try {
			return pollingDispatcher;
		} finally {
			lock.readLock().unlock();
		}
	}

	public void shutdownNow() {
		lock.writeLock().lock();
		try {
			stopped = true;
			timerDispatcher.shutdownNow();
			jsDispatcher.shutdownNow();
			networkIoDispatcher.shutdownNow();
			fileIoDispatcher.shutdownNow();
			pollingDispatcher.shutdownNow();
		} finally {
			lock.writeLock().unlock();
		}
	}

	public void restartIfShutdown() {
		lock.writeLock().lock();
		try {
			if (stopped)
				initDispatchers();
		} finally {
			lock.writeLock().unlock();
		}
	}
}
