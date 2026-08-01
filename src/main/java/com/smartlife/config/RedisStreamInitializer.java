package com.smartlife.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Ensures that the seckill order stream infrastructure exists before its
 * consumer is started. XGROUP CREATE with MKSTREAM is atomic and therefore
 * also safe when several application instances start at the same time.
 */
@Component
@Slf4j
public class RedisStreamInitializer {

    private static final String STREAM_KEY = "stream.orders";
    private static final String CONSUMER_GROUP = "g1";

    private final StringRedisTemplate stringRedisTemplate;
    private final String redisHost;
    private final int redisPort;
    private final int redisDatabase;

    public RedisStreamInitializer(StringRedisTemplate stringRedisTemplate,
                                  @Value("${spring.redis.host}") String redisHost,
                                  @Value("${spring.redis.port}") int redisPort,
                                  @Value("${spring.redis.database:0}") int redisDatabase) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisDatabase = redisDatabase;
    }

    public void initialize() {
        Boolean streamExists = stringRedisTemplate.hasKey(STREAM_KEY);
        boolean groupExists = streamExists != null && streamExists && consumerGroupExists();

        log.info("Redis host={}", redisHost);
        log.info("Redis port={}", redisPort);
        log.info("Redis database={}", redisDatabase);
        log.info("stream.orders status={}", Boolean.TRUE.equals(streamExists) ? "exists" : "missing");
        log.info("consumer group g1 status={}", groupExists ? "exists" : "missing");

        if (!groupExists) {
            createConsumerGroup();
            log.info("stream.orders status=exists");
            log.info("consumer group g1 status=exists");
        }
    }

    private boolean consumerGroupExists() {
        return stringRedisTemplate.opsForStream().groups(STREAM_KEY).stream()
                .anyMatch(group -> CONSUMER_GROUP.equals(group.groupName()));
    }

    private void createConsumerGroup() {
        try {
            stringRedisTemplate.execute((RedisCallback<Object>) connection -> executeXGroupCreate(connection));
            log.info("Created Redis consumer group with XGROUP CREATE {} {} 0 MKSTREAM",
                    STREAM_KEY, CONSUMER_GROUP);
        } catch (DataAccessException exception) {
            // Another application instance may have created it between the check and command.
            if (exception.getMessage() != null && exception.getMessage().contains("BUSYGROUP")) {
                log.info("Redis consumer group {} already exists for stream {}", CONSUMER_GROUP, STREAM_KEY);
                return;
            }
            throw exception;
        }
    }

    private Object executeXGroupCreate(RedisConnection connection) {
        return connection.execute("XGROUP",
                bytes("CREATE"), bytes(STREAM_KEY), bytes(CONSUMER_GROUP), bytes("0"), bytes("MKSTREAM"));
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
