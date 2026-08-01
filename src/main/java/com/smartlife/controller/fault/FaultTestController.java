package com.smartlife.controller.fault;

import com.smartlife.dto.Result;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/test")
public class FaultTestController {

    private static volatile double cpuBlackhole;

    @Value("${fault.redis-enabled:false}")
    private boolean redisFaultEnabled;

    @Value("${fault.mysql-slow-enabled:false}")
    private boolean mysqlSlowEnabled;

    @Value("${fault.mysql-slow-millis:3000}")
    private long mysqlSlowMillis;

    @Value("${fault.mysql-slow-interval-millis:200}")
    private long mysqlSlowIntervalMillis;

    @Value("${fault.oom.enabled:false}")
    private boolean oomFaultEnabled;

    @Value("${fault.oom.block-size:8388608}")
    private int oomFaultBlockSize;

    @Value("${fault.oom.allocation-interval-millis:200}")
    private long oomFaultAllocationIntervalMillis;

    @Value("${fault.oom.target-heap-usage:0.93}")
    private double oomFaultTargetHeapUsage;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JdbcTemplate jdbcTemplate;

    private final AtomicBoolean cpuFaultRunning = new AtomicBoolean(false);
    private final List<Thread> cpuFaultThreads = new ArrayList<>();

    public FaultTestController(MeterRegistry meterRegistry) {
        Gauge.builder("fault.cpu.injection.active", cpuFaultRunning, running -> running.get() ? 1.0 : 0.0)
                .description("Whether the CPU fault injector is active")
                .register(meterRegistry);
        Gauge.builder("fault.oom.injection.active", oomFaultRunning, running -> running.get() ? 1.0 : 0.0)
                .description("Whether the gradual JVM heap pressure injector is active")
                .register(meterRegistry);
        Gauge.builder("fault.oom.retained.bytes", oomFaultRetainedBytes, AtomicLong::get)
                .description("Bytes retained by the JVM heap pressure injector")
                .baseUnit("bytes")
                .register(meterRegistry);
        Gauge.builder("fault.mysql.slow.query.active", mysqlSlowFaultRunning,
                        running -> running.get() ? 1.0 : 0.0)
                .description("Whether the MySQL slow query fault injector is active")
                .register(meterRegistry);
        Gauge.builder("fault.mysql.slow.query.executions", mysqlSlowQueryExecutions, AtomicLong::get)
                .description("Number of completed queries run by the MySQL slow query fault injector")
                .register(meterRegistry);
        Gauge.builder("fault.mysql.slow.query.last.duration", mysqlSlowQueryLastDurationMillis, AtomicLong::get)
                .description("Duration of the latest MySQL slow query fault injection")
                .baseUnit("milliseconds")
                .register(meterRegistry);
    }

    @Value("${fault.cpu.threads:0}")
    private int configuredCpuFaultThreadCount;

    @Value("${fault.cpu.target-usage:0.9}")
    private double cpuFaultTargetUsage;

    @Value("${fault.cpu.oversubscribe-ratio:1.25}")
    private double cpuFaultOversubscribeRatio;

    private final AtomicBoolean oomFaultRunning = new AtomicBoolean(false);
    private final AtomicLong oomFaultRetainedBytes = new AtomicLong(0);
    private final List<byte[]> oomFaultBlocks = new ArrayList<>();
    private volatile Thread oomFaultThread;

    private final AtomicBoolean mysqlSlowFaultRunning = new AtomicBoolean(false);
    private final AtomicLong mysqlSlowQueryExecutions = new AtomicLong(0);
    private final AtomicLong mysqlSlowQueryLastDurationMillis = new AtomicLong(0);
    private final AtomicReference<PreparedStatement> mysqlSlowStatement = new AtomicReference<>();
    private volatile Thread mysqlSlowFaultThread;

    @GetMapping("/fault/cpu")
    public Result startCpuFault() {

        if (!cpuFaultRunning.compareAndSet(false, true)) {
            return Result.ok("CPU fault simulation is already running");
        }

        int processors = Runtime.getRuntime().availableProcessors();
        int threadCount = calculateCpuFaultThreadCount(processors);

        synchronized (cpuFaultThreads) {

            cpuFaultThreads.clear();

            for (int i = 0; i < threadCount; i++) {

                Thread thread = new Thread(
                        this::consumeCpu,
                        "fault-cpu-simulator-" + i
                );

                thread.setDaemon(true);

                thread.start();

                cpuFaultThreads.add(thread);
            }
        }

        log.warn(
                "CPU fault simulation started, threads={}, processors={}, targetUsage={}, oversubscribeRatio={}",
                threadCount,
                processors,
                cpuFaultTargetUsage,
                cpuFaultOversubscribeRatio
        );


        return Result.ok(
                "CPU fault simulation started, threads=" + threadCount +
                        ", processors=" + processors +
                        ", targetUsage=" + cpuFaultTargetUsage +
                        ", oversubscribeRatio=" + cpuFaultOversubscribeRatio
        );
    }

    @GetMapping("/fault/cpu/stop")
    public Result stopCpuFault() {
        stopCpuFaultInternal();
        log.warn("CPU fault simulation stopped");
        return Result.ok("CPU fault simulation stopped");
    }

    @GetMapping("/fault/cpu/status")
    public Result cpuFaultStatus() {
        int liveThreads;
        synchronized (cpuFaultThreads) {
            liveThreads = (int) cpuFaultThreads.stream().filter(Thread::isAlive).count();
        }
        return Result.ok(
                "running=" + cpuFaultRunning.get() +
                        ", liveThreads=" + liveThreads +
                        ", configuredThreads=" + configuredCpuFaultThreadCount
        );
    }

    @GetMapping("/fault/oom")
    public Result oomFault() {
        if (!oomFaultEnabled) {
            return Result.fail("OOM fault simulation is disabled");
        }
        if (!oomFaultRunning.compareAndSet(false, true)) {
            return Result.ok("OOM fault simulation is already running");
        }

        oomFaultThread = new Thread(this::consumeHeap, "fault-oom-simulator");
        oomFaultThread.setDaemon(true);
        oomFaultThread.start();

        log.warn("Gradual JVM heap pressure simulation started, blockSize={} bytes, interval={} ms, targetHeapUsage={}",
                oomFaultBlockSize, oomFaultAllocationIntervalMillis, oomFaultTargetHeapUsage);
        return Result.ok("JVM heap pressure simulation started, blockSize=" + oomFaultBlockSize
                + ", intervalMillis=" + oomFaultAllocationIntervalMillis
                + ", targetHeapUsage=" + oomFaultTargetHeapUsage);
    }

    @PostMapping("/fault/oom/stop")
    public Result stopOomFault() {
        stopOomFaultInternal();
        log.warn("JVM heap pressure simulation stopped and retained objects released");
        return Result.ok("JVM heap pressure simulation stopped and retained objects released");
    }

    @GetMapping("/redis/unavailable")
    public Result redisUnavailable() {
        if (!redisFaultEnabled) {
            return Result.fail("Redis fault simulation is disabled");
        }
        RuntimeException exception = new IllegalStateException("Redis unavailable");
        log.error("redis fault simulated,status=Redis unavailable,errorType={},error={}",
                exception.getClass().getSimpleName(), exception.getMessage(), exception);
        return Result.fail("Redis unavailable");
    }

    @GetMapping("/redis/ping")
    public Result redisPing() {
        try {
            String pong = stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            return Result.ok(pong);
        } catch (Exception e) {
            log.error("redis ping failed,errorType={},error={}", e.getClass().getSimpleName(), e.getMessage(), e);
            return Result.fail("Redis unavailable");
        }
    }

    @GetMapping({"/fault/mysql/slow", "/mysql/slow"})
    public Result startMysqlSlowFault() {
        if (!mysqlSlowEnabled) {
            return Result.fail("MySQL slow query simulation is disabled");
        }
        if (!mysqlSlowFaultRunning.compareAndSet(false, true)) {
            return Result.ok("MySQL slow query simulation is already running");
        }

        mysqlSlowFaultThread = new Thread(this::consumeMysqlSlowQueries, "fault-mysql-slow-simulator");
        mysqlSlowFaultThread.setDaemon(true);
        mysqlSlowFaultThread.start();

        log.warn("MySQL slow query simulation started, queryMillis={}, intervalMillis={}",
                mysqlSlowMillis, mysqlSlowIntervalMillis);
        return Result.ok("MySQL slow query simulation started, queryMillis=" + mysqlSlowMillis
                + ", intervalMillis=" + mysqlSlowIntervalMillis);
    }

    @PostMapping("/fault/mysql/slow/stop")
    public Result stopMysqlSlowFault() {
        stopMysqlSlowFaultInternal();
        log.warn("MySQL slow query simulation stopped");
        return Result.ok("MySQL slow query simulation stopped");
    }

    @PreDestroy
    public void destroy() {
        stopCpuFaultInternal();
        stopOomFaultInternal();
        stopMysqlSlowFaultInternal();
    }

    private void consumeCpu() {
        while (cpuFaultRunning.get()) {
            double result = cpuBlackhole;
            for (int i = 1; i <= 100000; i++) {
                result = Math.sqrt(result + i) * 1.0000001;
            }
            // Publishing the result prevents the JIT compiler from removing the workload.
            cpuBlackhole = result;
        }
    }

    private int calculateCpuFaultThreadCount(int processors) {
        if (configuredCpuFaultThreadCount > 0) {
            return configuredCpuFaultThreadCount;
        }
        double targetUsage = Math.min(1.0, Math.max(0.01, cpuFaultTargetUsage));
        double oversubscribeRatio = Math.min(2.0, Math.max(1.0, cpuFaultOversubscribeRatio));
        return Math.max(1, (int) Math.ceil(processors * targetUsage * oversubscribeRatio));
    }

    private void consumeMysqlSlowQueries() {
        long queryMillis = Math.max(1, mysqlSlowMillis);
        long intervalMillis = Math.max(0, mysqlSlowIntervalMillis);

        try {
            while (mysqlSlowFaultRunning.get()) {
                long start = System.currentTimeMillis();
                try {
                    Integer result = jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
                        try (PreparedStatement statement = connection.prepareStatement("SELECT SLEEP(?)")) {
                            mysqlSlowStatement.set(statement);
                            statement.setDouble(1, queryMillis / 1000.0);
                            try (ResultSet resultSet = statement.executeQuery()) {
                                return resultSet.next() ? resultSet.getInt(1) : null;
                            } finally {
                                mysqlSlowStatement.compareAndSet(statement, null);
                            }
                        }
                    });
                    long duration = System.currentTimeMillis() - start;
                    mysqlSlowQueryLastDurationMillis.set(duration);
                    long executions = mysqlSlowQueryExecutions.incrementAndGet();
                    log.warn("MySQL slow query simulated,queryMillis={},costMillis={},result={},executions={}",
                            queryMillis, duration, result, executions);
                } catch (Exception e) {
                    mysqlSlowQueryLastDurationMillis.set(System.currentTimeMillis() - start);
                    if (mysqlSlowFaultRunning.get()) {
                        log.error("MySQL slow query simulation failed,queryMillis={},errorType={},error={}",
                                queryMillis, e.getClass().getSimpleName(), e.getMessage(), e);
                    }
                }

                if (mysqlSlowFaultRunning.get() && intervalMillis > 0) {
                    Thread.sleep(intervalMillis);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("MySQL slow query simulation interrupted,executions={}", mysqlSlowQueryExecutions.get());
        } finally {
            mysqlSlowStatement.set(null);
        }
    }

    private void consumeHeap() {
        int blockSize = Math.max(1024, oomFaultBlockSize);
        long allocationIntervalMillis = Math.max(100, oomFaultAllocationIntervalMillis);
        double targetHeapUsage = Math.min(0.95, Math.max(0.91, oomFaultTargetHeapUsage));
        Runtime runtime = Runtime.getRuntime();
        long maxHeap = runtime.maxMemory();
        long targetUsedBytes = (long) (maxHeap * targetHeapUsage);
        boolean targetReachedLogged = false;
        long lastProgressLogTime = 0;

        try {
            while (oomFaultRunning.get()) {
                long usedHeap = runtime.totalMemory() - runtime.freeMemory();

                if (usedHeap + blockSize <= targetUsedBytes) {
                    byte[] block = new byte[blockSize];
                    synchronized (oomFaultBlocks) {
                        oomFaultBlocks.add(block);
                    }
                    oomFaultRetainedBytes.addAndGet(block.length);
                    targetReachedLogged = false;

                    long now = System.currentTimeMillis();
                    if (now - lastProgressLogTime >= 10000) {
                        log.warn("JVM heap pressure growing, heapUsed={} bytes, heapMax={} bytes, usage={}, retainedBytes={}",
                                usedHeap, maxHeap, heapUsage(usedHeap, maxHeap), oomFaultRetainedBytes.get());
                        lastProgressLogTime = now;
                    }
                } else if (!targetReachedLogged) {
                    log.warn("JVM heap pressure target reached and will be held, heapUsed={} bytes, heapMax={} bytes, "
                                    + "usage={}, targetUsage={}, retainedBytes={}",
                            usedHeap, maxHeap, heapUsage(usedHeap, maxHeap), targetHeapUsage,
                            oomFaultRetainedBytes.get());
                    targetReachedLogged = true;
                }

                Thread.sleep(allocationIntervalMillis);
            }
        } catch (OutOfMemoryError error) {
            // Unexpected external pressure can still exhaust the reserved headroom.
            // Release injected objects immediately so infrastructure threads can recover.
            oomFaultRunning.set(false);
            clearOomFaultBlocks();
            log.error("JVM heap pressure injector reached an unexpected allocation limit and released retained objects");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("JVM heap pressure simulation interrupted, retainedBlocks={}, retainedBytes={}",
                    getOomFaultBlockCount(), oomFaultRetainedBytes.get());
        } finally {
            if (!oomFaultRunning.get()) {
                clearOomFaultBlocks();
            }
        }
    }

    private double heapUsage(long usedHeap, long maxHeap) {
        return maxHeap <= 0 ? 0 : (double) usedHeap / maxHeap;
    }

    private int getOomFaultBlockCount() {
        synchronized (oomFaultBlocks) {
            return oomFaultBlocks.size();
        }
    }

    private void clearOomFaultBlocks() {
        synchronized (oomFaultBlocks) {
            oomFaultBlocks.clear();
        }
        oomFaultRetainedBytes.set(0);
        System.gc();
    }

    private void stopCpuFaultInternal() {
        cpuFaultRunning.set(false);
        synchronized (cpuFaultThreads) {
            for (Thread thread : cpuFaultThreads) {
                thread.interrupt();
            }
            cpuFaultThreads.clear();
        }
    }

    private void stopOomFaultInternal() {
        oomFaultRunning.set(false);
        Thread thread = oomFaultThread;
        if (thread != null) {
            thread.interrupt();
            oomFaultThread = null;
        }
        clearOomFaultBlocks();
    }

    private void stopMysqlSlowFaultInternal() {
        mysqlSlowFaultRunning.set(false);

        PreparedStatement statement = mysqlSlowStatement.getAndSet(null);
        if (statement != null) {
            try {
                statement.cancel();
            } catch (SQLException e) {
                log.warn("Failed to cancel active MySQL slow query,errorType={},error={}",
                        e.getClass().getSimpleName(), e.getMessage());
            }
        }

        Thread thread = mysqlSlowFaultThread;
        if (thread != null) {
            thread.interrupt();
            mysqlSlowFaultThread = null;
        }
    }
}
