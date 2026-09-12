package com.demo.upimesh.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.Transaction;
import com.demo.upimesh.model.TransactionRepository;
import com.demo.upimesh.service.BridgeIngestionService;
import com.demo.upimesh.service.DemoService;
import com.demo.upimesh.service.IdempotencyService;
import com.demo.upimesh.service.MeshSimulatorService;
import com.demo.upimesh.service.SettlementService;
import com.demo.upimesh.service.VirtualDevice;

import jakarta.servlet.http.HttpSession;

/**
 * Public REST surface.
 *
 * Each browser session gets its own isolated demo accounts and mesh state.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private ServerKeyHolder serverKey;

    @Autowired
    private DemoService demo;

    @Autowired
    private MeshSimulatorService mesh;

    @Autowired
    private BridgeIngestionService bridge;

    @Autowired
    private SettlementService settlement;

    @Autowired
    private AccountRepository accountRepo;

    @Autowired
    private TransactionRepository txRepo;

    @Autowired
    private IdempotencyService idempotency;

    // -------------------------------------------------------------- key

    @GetMapping("/server-key")
    public Map<String, String> getServerPublicKey() {

        return Map.of(
                "publicKey",
                serverKey.getPublicKeyBase64(),

                "algorithm",
                "RSA-2048 / OAEP-SHA256",

                "hybridScheme",
                "RSA-OAEP encrypts an AES-256-GCM session key"
        );
    }

    // -------------------------------------------------------------- demo

    /**
     * Creates the demo accounts for this browser session if they
     * do not already exist.
     */
    @PostMapping("/demo/send")
    public ResponseEntity<?> demoSend(
            @RequestBody DemoSendRequest req,
            HttpSession session) throws Exception {

        String sessionId = session.getId();

        // Make sure this browser has its own demo accounts.
        demo.initializeSession(sessionId);

        MeshPacket packet = demo.createPacket(
                req.senderVpa,
                req.receiverVpa,
                req.amount,
                req.pin,
                req.ttl == null ? 5 : req.ttl
        );

        String startDevice =
                req.startDevice == null
                        ? "phone-alice"
                        : req.startDevice;

        mesh.inject(startDevice, packet);

        return ResponseEntity.ok(
                Map.of(
                        "packetId",
                        packet.getPacketId(),

                        "ciphertextPreview",
                        packet.getCiphertext()
                                .substring(0, 64)
                                + "...",

                        "ttl",
                        packet.getTtl(),

                        "injectedAt",
                        startDevice
                )
        );
    }

    public static class DemoSendRequest {

        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
        public String pin;
        public Integer ttl;
        public String startDevice;
    }

    // -------------------------------------------------------------- mesh

    @GetMapping("/mesh/state")
    public Map<String, Object> meshState(
            HttpSession session) {

        // Ensure this session has demo accounts.
        demo.initializeSession(session.getId());

        List<Map<String, Object>> deviceData =
                new ArrayList<>();

        for (VirtualDevice d : mesh.getDevices()) {

            deviceData.add(
                    Map.of(
                            "deviceId",
                            d.getDeviceId(),

                            "hasInternet",
                            d.hasInternet(),

                            "packetCount",
                            d.packetCount(),

                            "packetIds",
                            d.getHeldPackets()
                                    .stream()
                                    .map(p ->
                                            p.getPacketId()
                                                    .substring(
                                                            0,
                                                            8
                                                    )
                                    )
                                    .toList()
                    )
            );
        }

        return Map.of(
                "devices",
                deviceData,

                "routes",
                mesh.getPacketRoutes(),

                "hopHistory",
                mesh.getHopHistory(),

                "idempotencyCacheSize",
                idempotency.size()
        );
    }

    @PostMapping("/mesh/gossip")
    public Map<String, Object> meshGossip(
            HttpSession session) {

        demo.initializeSession(session.getId());

        MeshSimulatorService.GossipResult r =
                mesh.gossipOnce();

        return Map.of(
                "transfers",
                r.transfers(),

                "deviceCounts",
                r.deviceCounts(),

                "packetId",
                r.packetId(),

                "from",
                r.from(),

                "to",
                r.to(),

                "ttl",
                r.ttl(),

                "reachedBridge",
                r.reachedBridge()
        );
    }

    /**
     * Upload all packets currently held by bridge nodes.
     */
    @PostMapping("/mesh/flush")
    public Map<String, Object> meshFlush(
            HttpSession session) {

        String sessionId = session.getId();

        demo.initializeSession(sessionId);

        List<MeshSimulatorService.BridgeUpload> uploads =
                mesh.collectBridgeUploads();

        List<Map<String, Object>> results =
                new ArrayList<>();

        uploads.parallelStream().forEach(up -> {

            String packetId =
                    up.packet().getPacketId();

            List<String> route =
                    mesh.getPacketRoutes()
                            .get(packetId);

            int hopCount = 0;

            if (route != null) {
                hopCount = route.size() - 1;
            }

            BridgeIngestionService.IngestResult r =
                    bridge.ingest(
                            up.packet(),
                            up.bridgeNodeId(),
                            hopCount,
                            sessionId
                    );

            synchronized (results) {

                results.add(
                        Map.of(
                                "bridgeNode",
                                up.bridgeNodeId(),

                                "packetId",
                                packetId.substring(
                                        0,
                                        Math.min(
                                                8,
                                                packetId.length()
                                        )
                                ),

                                "outcome",
                                r.outcome(),

                                "reason",
                                r.reason() == null
                                        ? ""
                                        : r.reason(),

                                "transactionId",
                                r.transactionId() == null
                                        ? -1
                                        : r.transactionId()
                        )
                );
            }

            /*
             * Remove the packet after backend processing.
             *
             * RETRY_REQUIRED is kept so the packet can be retried.
             */
            if ("SETTLED".equals(r.outcome())
                    || "DUPLICATE_DROPPED".equals(r.outcome())
                    || "INVALID".equals(r.outcome())) {

                mesh.clearPacket(packetId);
            }
        });

        return Map.of(
                "uploadsAttempted",
                uploads.size(),

                "results",
                results
        );
    }

    // -------------------------------------------------------------- reset

    @PostMapping("/mesh/reset")
    public Map<String, Object> meshReset(
            HttpSession session) {

        demo.initializeSession(session.getId());

        mesh.resetMesh();

        idempotency.clear();

        return Map.of(
                "status",
                "mesh and idempotency cache cleared"
        );
    }

    @PostMapping("/demo/reset")
    public Map<String, Object> resetDemo(
            HttpSession session) {

        String sessionId = session.getId();

        demo.initializeSession(sessionId);

        settlement.resetDemoAccounts(sessionId);

        mesh.resetMesh();

        idempotency.clear();

        return Map.of(
                "status",
                "Demo data reset successfully"
        );
    }

    // -------------------------------------------------------------- bridge

    /**
     * Production-style bridge ingestion endpoint.
     *
     * For this simulator, the browser session identifies the isolated
     * demo environment to which the packet belongs.
     */
    @PostMapping("/bridge/ingest")
    public ResponseEntity<?> ingest(
            @RequestBody MeshPacket packet,

            @RequestHeader(
                    value = "X-Bridge-Node-Id",
                    defaultValue = "unknown"
            )
            String bridgeNodeId,

            @RequestHeader(
                    value = "X-Hop-Count",
                    defaultValue = "0"
            )
            int hopCount,

            HttpSession session) {

        String sessionId = session.getId();

        demo.initializeSession(sessionId);

        BridgeIngestionService.IngestResult r =
                bridge.ingest(
                        packet,
                        bridgeNodeId,
                        hopCount,
                        sessionId
                );

        return ResponseEntity.ok(r);
    }

    // ------------------------------------------------------------- accounts

    @GetMapping("/accounts")
    public List<Account> listAccounts(
            HttpSession session) {

        String sessionId = session.getId();

        demo.initializeSession(sessionId);

        return accountRepo.findAllBySessionId(sessionId);
    }

    @GetMapping("/transactions")
    public List<Transaction> listTransactions(
            HttpSession session) {

        String sessionId = session.getId();

        demo.initializeSession(sessionId);

        return txRepo.findTop20BySessionIdOrderByIdDesc(
                sessionId
        );
    }
}