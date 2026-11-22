package com.zaalima.high_concurrency_agent_orchestrator.cluster;

import com.zaalima.high_concurrency_agent_orchestrator.service.RedisDistributedLockService;
import com.zaalima.high_concurrency_agent_orchestrator.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Member 1 integration coverage: Redis K/V, TTL, two-instance locks, node registry. */
@Testcontainers(disabledWithoutDocker = true)
class RedisClusterIntegrationTest {

    @Container
    public static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private LettuceConnectionFactory factory1;
    private LettuceConnectionFactory factory2;
    private RedisService service1;
    private RedisDistributedLockService lock1;
    private RedisDistributedLockService lock2;

    @BeforeEach
    void setUp() {
        factory1 = factory();
        factory2 = factory();
        service1 = new RedisService(template(factory1));
        lock1 = new RedisDistributedLockService(template(factory1));
        lock2 = new RedisDistributedLockService(template(factory2));
    }

    @Test
    void redisSetGetAndTtl() {
        service1.setWithTtl("it:key", "value", Duration.ofSeconds(30));
        assertEquals("value", service1.get("it:key"));
        assertTrue(service1.getTtl("it:key") > 0);
        assertTrue(service1.delete("it:key"));
        assertNull(service1.get("it:key"));
        assertEquals(-2L, service1.getTtl("it:key"));
    }

    @Test
    void onlyOneInstanceOfTwoCanHoldTheLock() {
        String token = lock1.acquireLock("integration:payment", Duration.ofSeconds(10));
        assertNotNull(token);
        assertNull(lock2.acquireLock("integration:payment", Duration.ofSeconds(10)));

        assertFalse(lock2.releaseLock("integration:payment", "wrong-token"));
        assertTrue(lock1.releaseLock("integration:payment", token));

        String secondToken = lock2.acquireLock("integration:payment", Duration.ofSeconds(10));
        assertNotNull(secondToken);
        assertTrue(lock2.releaseLock("integration:payment", secondToken));
    }

    @Test
    void lockExpiresAutomatically() throws InterruptedException {
        String resource = "integration:expiry";
        String oldToken = lock1.acquireLock(resource, Duration.ofMillis(400));
        assertNotNull(oldToken);
        assertNull(lock2.acquireLock(resource, Duration.ofSeconds(5)));

        String newToken = null;
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            newToken = lock2.acquireLock(resource, Duration.ofSeconds(5));
            if (newToken != null) {
                break;
            }
            Thread.sleep(50);
        }
        assertNotNull(newToken);
        assertFalse(lock1.releaseLock(resource, oldToken));
        assertTrue(lock2.releaseLock(resource, newToken));
    }

    @Test
    void nodeRegistrySavesFindsAndSelectsLeastUtilizedNode() {
        RedisNodeRegistry registry = new RedisNodeRegistry(template(factory1), 60);
        long now = System.currentTimeMillis();

        registry.save(new NodeInfo("node-1", now, 90, 100, 90, 10));
        registry.save(new NodeInfo("node-2", now, 5, 100, 5, 10));
        registry.save(new NodeInfo("node-3", now - 120_000, 0, 100, 0, 0));

        assertEquals(3, registry.findAll().size());
        assertEquals(90L, registry.find("node-1").orElseThrow().activeTasks());
        Optional<NodeInfo> selected = registry.selectLeastUtilized();
        assertTrue(selected.isPresent());
        assertEquals("node-2", selected.get().nodeId());
        assertEquals(1, registry.pruneStaleNodes());
        assertEquals(List.of("node-1", "node-2"),
                registry.rankedNodes().stream().map(NodeInfo::nodeId).sorted().toList());
    }

    private LettuceConnectionFactory factory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        return factory;
    }

    private StringRedisTemplate template(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.afterPropertiesSet();
        return template;
    }
}
