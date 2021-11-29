package com.gimslab.cacheforheavy;

import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyMap;

public class SecondCacheForHeavyJob {

	int LOOP_BREAK_MAX_5_MIN = 5 * 60 * 1000;
	int CACHE_EXPIRE_2_MIN = 2 * 60 * 1000;
	//	int CACHE_EXPIRE_2_MIN = 30 * 1000; // local test

	private final CacheDataCalculator cacheDataCalculator;

	private final AtomicReference<CacheData> cache = new AtomicReference<>(new CacheData());
	private final AtomicReference<CacheData> cacheNew = new AtomicReference<>(new CacheData());
	private final Lock lock = new ReentrantLock();

	public SecondCacheForHeavyJob(CacheDataCalculator cacheDataCalculator) {
		this.cacheDataCalculator = cacheDataCalculator;
	}

	public Map<Long, Long> getAndRefresh() {
		deleteCacheIfStale();
		requestDataRefresh();
		return waitAndGetData();
	}

	private void deleteCacheIfStale() {
		synchronized (cache) {
			if (currentTimeMillis() - cache.get().lastUpdatedAt > CACHE_EXPIRE_2_MIN)
				cache.get().removeData();
		}
	}

	private void requestDataRefresh() {
		Mono.fromCallable(() -> {
					if (!lock.tryLock()) {
						log.debug("++ lock fail to calculating. skip this");
						return null;
					}
					try {
						return cacheDataCalculator.calculate();
					} finally {
						lock.unlock();
					}
				})
				.subscribeOn(Schedulers.boundedElastic())
				.subscribe(result -> {
					synchronized (cache) {
						cache.get().replaceWith(result);
					}
				});
	}

	@SneakyThrows
	private Map<Long, Long> waitAndGetData() {
		Map<Long, Long> returnValue;
		long loopStartedAt = currentTimeMillis();
		while (true) {
			returnValue = cache.get().data;
			if (returnValue != null || (currentTimeMillis() - loopStartedAt > LOOP_BREAK_MAX_5_MIN))
				break;
			Thread.sleep(100);
		}
		if (returnValue == null) {
			log.error("++ maybe there are some errors while calculating...");
			returnValue = emptyMap();
		}
		return returnValue;
	}

	@ToString
	private static class CacheData {
		private Map<Long, Long> data;
		private Long lastUpdatedAt = 0L;

		synchronized private void removeData() {
			data = null;
			lastUpdatedAt = currentTimeMillis();
			log.info("++ cache expired");
		}

		synchronized private void replaceWith(Map<Long, Long> data) {
			this.data = data;
			lastUpdatedAt = currentTimeMillis();
			log.debug("++ data replaced with {}", data);
		}
	}
}
