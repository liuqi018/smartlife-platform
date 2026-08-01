package com.smartlife.controller.fault;

import com.smartlife.dto.Result;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FaultTestControllerMysqlTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final FaultTestController controller = new FaultTestController(meterRegistry);

    @AfterEach
    void tearDown() {
        controller.stopMysqlSlowFault();
        meterRegistry.close();
    }

    @Test
    void shouldStartOnlyOnceRunQueriesAndStop() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CountDownLatch queryCompleted = new CountDownLatch(1);
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            queryCompleted.countDown();
            return 0;
        });

        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(controller, "mysqlSlowEnabled", true);
        ReflectionTestUtils.setField(controller, "mysqlSlowMillis", 10L);
        ReflectionTestUtils.setField(controller, "mysqlSlowIntervalMillis", 10L);

        Result started = controller.startMysqlSlowFault();
        Result duplicate = controller.startMysqlSlowFault();

        assertEquals("MySQL slow query simulation started, queryMillis=10, intervalMillis=10", started.getData());
        assertEquals("MySQL slow query simulation is already running", duplicate.getData());
        assertTrue(queryCompleted.await(1, TimeUnit.SECONDS));
        assertEquals(1.0, meterRegistry.get("fault.mysql.slow.query.active").gauge().value());

        controller.stopMysqlSlowFault();

        assertEquals(0.0, meterRegistry.get("fault.mysql.slow.query.active").gauge().value());
        assertTrue(meterRegistry.get("fault.mysql.slow.query.executions").gauge().value() >= 1.0);
        assertTrue(meterRegistry.get("fault.mysql.slow.query.last.duration").gauge().value() >= 0.0);
    }
}
