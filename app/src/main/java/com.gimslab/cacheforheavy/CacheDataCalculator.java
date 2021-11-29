package com.gimslab.cacheforheavy;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class CacheDataCalculator {

	@SneakyThrows
	public Map<Long, Long> calculate() {
		log.info("++ calculating... ");
		Thread.sleep(5_000);
		return ImmutableMap.of(1L, 100L, 2L, 200L, 3L, System.currentTimeMillis());
	}
}
