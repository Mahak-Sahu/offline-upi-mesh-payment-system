package com.demo.upimesh;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import com.demo.upimesh.service.IdempotencyService;

@SpringBootTest
class IdempotencyConcurrencyTest {

    private static final String SESSION_ID = "test-session";

    @Autowired
    private DemoService demoService;

    @Autowired
    private BridgeIngestionService bridge;

    @Autowired
    private IdempotencyService idempotency;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private HybridCryptoService crypto;

    @Autowired
    private ServerKeyHolder serverKey;

    @BeforeEach
    void clear() {

        idempotency.clear();

        demoService.initializeSession(SESSION_ID);
    }

    @Test
    void singlePacketDeliveredByThreeBridgesSettlesExactlyOnce()
            throws Exception {

        Account aliceBeforeAccount =
                accounts.findBySessionIdAndVpa(
                        SESSION_ID,
                        "alice@demo"
                ).orElseThrow();

        Account bobBeforeAccount =
                accounts.findBySessionIdAndVpa(
                        SESSION_ID,
                        "bob@demo"
                ).orElseThrow();

        BigDecimal aliceBefore =
                aliceBeforeAccount.getBalance();

        BigDecimal bobBefore =
                bobBeforeAccount.getBalance();

        MeshPacket packet =
                demoService.createPacket(
                        "alice@demo",
                        "bob@demo",
                        new BigDecimal("100.00"),
                        "1234",
                        5
                );

        ExecutorService pool =
                Executors.newFixedThreadPool(3);

        CountDownLatch start =
                new CountDownLatch(1);

        AtomicInteger settled =
                new AtomicInteger();

        AtomicInteger duplicates =
                new AtomicInteger();

        Future<?>[] futures =
                new Future[3];

        for (int i = 0; i < 3; i++) {

            final String node =
                    "bridge-" + i;

            futures[i] =
                    pool.submit(() -> {

                        try {

                            start.await();

                            BridgeIngestionService.IngestResult result =
                                    bridge.ingest(
                                            packet,
                                            node,
                                            3,
                                            SESSION_ID
                                    );

                            if ("SETTLED".equals(result.outcome())) {

                                settled.incrementAndGet();

                            } else if (
                                    "DUPLICATE_DROPPED"
                                            .equals(result.outcome())
                            ) {

                                duplicates.incrementAndGet();
                            }

                        } catch (Exception e) {

                            throw new RuntimeException(e);
                        }
                    });
        }

        start.countDown();

        for (Future<?> future : futures) {

            future.get(
                    5,
                    TimeUnit.SECONDS
            );
        }

        pool.shutdown();

        assertEquals(
                1,
                settled.get(),
                "Exactly one bridge should settle"
        );

        assertEquals(
                2,
                duplicates.get(),
                "The other two bridges should be duplicates"
        );

        Account aliceAfterAccount =
                accounts.findBySessionIdAndVpa(
                        SESSION_ID,
                        "alice@demo"
                ).orElseThrow();

        Account bobAfterAccount =
                accounts.findBySessionIdAndVpa(
                        SESSION_ID,
                        "bob@demo"
                ).orElseThrow();

        BigDecimal aliceAfter =
                aliceAfterAccount.getBalance();

        BigDecimal bobAfter =
                bobAfterAccount.getBalance();

        assertEquals(
                aliceBefore.subtract(
                        new BigDecimal("100.00")
                ),
                aliceAfter
        );

        assertEquals(
                bobBefore.add(
                        new BigDecimal("100.00")
                ),
                bobAfter
        );
    }

    @Test
    void tamperedCiphertextIsRejected()
            throws Exception {

        MeshPacket packet =
                demoService.createPacket(
                        "alice@demo",
                        "bob@demo",
                        new BigDecimal("50.00"),
                        "1234",
                        5
                );

        char[] chars =
                packet.getCiphertext().toCharArray();

        chars[chars.length / 2] =
                chars[chars.length / 2] == 'A'
                        ? 'B'
                        : 'A';

        packet.setCiphertext(
                new String(chars)
        );

        BridgeIngestionService.IngestResult result =
                bridge.ingest(
                        packet,
                        "bridge-x",
                        1,
                        SESSION_ID
                );

        assertEquals(
                "INVALID",
                result.outcome()
        );
    }

    @Test
    void encryptDecryptRoundTrip()
            throws Exception {

        PaymentInstruction original =
                new PaymentInstruction(
                        "alice@demo",
                        "bob@demo",
                        new BigDecimal("123.45"),
                        "abcdef",
                        "nonce-1",
                        System.currentTimeMillis()
                );

        String ciphertext =
                crypto.encrypt(
                        original,
                        serverKey.getPublicKey()
                );

        PaymentInstruction decrypted =
                crypto.decrypt(ciphertext);

        assertEquals(
                original.getSenderVpa(),
                decrypted.getSenderVpa()
        );

        assertEquals(
                original.getReceiverVpa(),
                decrypted.getReceiverVpa()
        );

        assertEquals(
                0,
                original.getAmount()
                        .compareTo(decrypted.getAmount())
        );

        assertEquals(
                original.getNonce(),
                decrypted.getNonce()
        );
    }
}