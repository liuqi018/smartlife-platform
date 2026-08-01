package com.smartlife.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {


    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Resource
    private JdbcTemplate jdbcTemplate;


    /**
     * 综合健康检查
     */
    @GetMapping("/detail")
    public Map<String, String> detail() {

        Map<String, String> result = new LinkedHashMap<>();

        result.put("service", "smartlife");
        result.put("redis", checkRedis());
        result.put("mysql", checkMysql());

        result.put(
                "status",
                "UP".equals(result.get("redis"))
                        && "UP".equals(result.get("mysql"))
                        ? "UP"
                        : "DOWN"
        );

        return result;
    }


    /**
     * Redis 专用健康检查
     */
    @GetMapping("/redis")
    public ResponseEntity<String> redisHealth() {

        String status = checkRedis();

        if ("UP".equals(status)) {
            return ResponseEntity.ok(status);
        }

        return ResponseEntity.status(503).body(status);
    }


    /**
     * MySQL 专用健康检查
     */
    @GetMapping("/mysql")
    public ResponseEntity<String> mysqlHealth() {

        String status = checkMysql();

        if ("UP".equals(status)) {
            return ResponseEntity.ok(status);
        }

        return ResponseEntity.status(503).body(status);
    }


    private String checkRedis() {

        try {

            String pong = stringRedisTemplate.execute(
                    (RedisCallback<String>) connection -> connection.ping()
            );

            return "PONG".equalsIgnoreCase(pong)
                    ? "UP"
                    : "DOWN";

        } catch (Exception e) {

            return "DOWN";
        }
    }



    private String checkMysql() {

        try {

            Integer value =
                    jdbcTemplate.queryForObject(
                            "SELECT 1",
                            Integer.class
                    );

            return Integer.valueOf(1).equals(value)
                    ? "UP"
                    : "DOWN";

        } catch (Exception e) {

            return "DOWN";
        }
    }
}