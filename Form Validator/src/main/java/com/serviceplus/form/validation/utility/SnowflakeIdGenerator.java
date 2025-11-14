package com.serviceplus.form.validation.utility;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class SnowflakeIdGenerator {

	@Value("${id.generator.worker.id}")
    private Long workerIdConfigured;

	@Value("${id.generator.datacenter.id}")
    private Long datacenterIdConfigured;

    private static Long workerId;
    private static Long datacenterId;

    private long sequence = 0L;

    private final long twepoch = 1288834974657L;
    private final long workerIdBits = 5L;
    private final long datacenterIdBits = 5L;
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    private final long sequenceBits = 12L;

    private final long workerIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);
    private static SnowflakeIdGenerator INSTANCE;
    private long lastTimestamp = -1L;

    @PostConstruct
	private void init() {
    	SnowflakeIdGenerator.workerId = workerIdConfigured;
        SnowflakeIdGenerator.datacenterId = datacenterIdConfigured;
        INSTANCE = this;
	}

    public SnowflakeIdGenerator() {

    }

    public SnowflakeIdGenerator(Long workerId, Long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException("workerId out of range");
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId out of range");
        }
        SnowflakeIdGenerator.workerId = workerId;
        SnowflakeIdGenerator.datacenterId = datacenterId;
    }

    public synchronized Long nextId() {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards.");
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - twepoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

    public static String createUniqueId() {
        if (INSTANCE == null) {
            throw new IllegalStateException("SnowflakeIdGenerator not initialized yet.");
        }
        return INSTANCE.nextId().toString();
    }
}
