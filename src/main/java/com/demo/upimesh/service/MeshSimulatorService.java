package com.demo.upimesh.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.SessionScope;

import com.demo.upimesh.model.Hop;
import com.demo.upimesh.model.MeshPacket;

/**
 * Simulates the Bluetooth mesh.
 *
 * Each VirtualDevice represents a phone. The "gossip" step picks pairs of
 * devices that are nearby (we just say all devices are nearby for the demo)
 * and copies packets between them, decrementing TTL each hop.
 *
 * When a device with internet (a "bridge node") holds a packet, the demo's
 * /api/mesh/flush endpoint causes it to actually POST that packet to our
 * backend — simulating the moment a phone walks outside and gets 4G.
 */

@Service
@SessionScope
public class MeshSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(MeshSimulatorService.class);

    private final Map<String, VirtualDevice> devices = new ConcurrentHashMap<>();
    private final Random random = new Random();

    /*
    * Stores the actual path followed by every packet.
    *
    * packetId
    *     ↓
    * phone-alice -> phone-stranger2 -> phone-bridge
    */
    private final Map<String, List<String>> packetRoutes = new ConcurrentHashMap<>();
    private final Map<String,List<Hop>> hopHistory = new HashMap<>();
    public MeshSimulatorService() {
        // Default scenario: 4 offline phones in a basement, 1 phone outside with 4G
        seedDefaultDevices();
    }

    private void seedDefaultDevices() {
        devices.put("phone-alice",   new VirtualDevice("phone-alice",   false));
        devices.put("phone-stranger1", new VirtualDevice("phone-stranger1", false));
        devices.put("phone-stranger2", new VirtualDevice("phone-stranger2", false));
        devices.put("phone-stranger3", new VirtualDevice("phone-stranger3", false));
        devices.put("phone-bridge",  new VirtualDevice("phone-bridge",  true));
    }

    public Collection<VirtualDevice> getDevices() {
        return devices.values();
    }

    public VirtualDevice getDevice(String id) {
        return devices.get(id);
    }

    /**
     * Sender drops a packet into the mesh by handing it to their own device.
     */
    public void inject(String senderDeviceId, MeshPacket packet) {
        VirtualDevice sender = devices.get(senderDeviceId);
        if (sender == null) throw new IllegalArgumentException("Unknown device: " + senderDeviceId);
        sender.hold(packet);
        packetRoutes.put(packet.getPacketId(), new ArrayList<>());
        hopHistory.put(
        packet.getPacketId(),
        new ArrayList<>()
);
        packetRoutes.get(packet.getPacketId()).add(senderDeviceId);
        log.info("Packet {} injected at {} (TTL={})",
                packet.getPacketId().substring(0, 8), senderDeviceId, packet.getTtl());
    }

    /**
     * One round of gossip. Every device shares everything it has with every
     * other device. TTL is decremented per hop; packets at TTL 0 stay where
     * they are but are not forwarded further.
     *
     * Real BLE gossip would be pair-by-pair when devices come into range.
     * For the demo we let everyone gossip with everyone in one round, which
     * is equivalent to "fast-forward N rounds of pairwise gossip".
     */
    private static final int MIN_STRANGER_HOPS = 2;
    private static final int MAX_STRANGER_HOPS = 2;

public synchronized GossipResult gossipOnce() {
    

    // We simulate ONE real mesh hop per gossip call.
    // The frontend will call this automatically after a delay.

    if (packetRoutes.isEmpty()) {
        return new GossipResult(
        0,
        snapshotMap(),
        null,
        null,
        null,
        0,
        false
);
    }

    // Demo currently works with one active packet at a time.
    String packetId = packetRoutes.keySet().stream()
            .findFirst()
            .orElse(null);

    if (packetId == null) {
        return new GossipResult(
        0,
        snapshotMap(),
        null,
        null,
        null,
        0,
        false
);
    }

    List<String> route = packetRoutes.get(packetId);

    if (route == null || route.isEmpty()) {
        return new GossipResult(
        0,
        snapshotMap(),
        null,
        null,
        null,
        0,
        false
);
    }

    String currentNodeId = route.get(route.size() - 1);

    VirtualDevice current = devices.get(currentNodeId);

    if (current == null) {
        return new GossipResult(
        0,
        snapshotMap(),
        null,
        null,
        null,
        0,
        false
);
    }

    // If packet has already reached bridge, gossip is finished.
    if (current.hasInternet()) {
        log.info("Packet {} already reached bridge.", packetId.substring(0, 8));
        return new GossipResult(
        0,
        snapshotMap(),
        null,
        null,
        null,
        0,
        false
);
    }

    // Find the actual packet currently held by this node.
    MeshPacket packet = current.getHeldPackets()
            .stream()
            .filter(p -> packetId.equals(p.getPacketId()))
            .findFirst()
            .orElse(null);

    if (packet == null) {
        log.warn("Packet {} is not held by current node {}",
                packetId.substring(0, 8), currentNodeId);
        return new GossipResult(
        0,
        snapshotMap(),
        null,
        null,
        null,
        0,
        false
);
    }

    if (packet.getTtl() <= 0) {
        log.warn("Packet {} TTL exhausted.", packetId.substring(0, 8));
        return new GossipResult(
        0,
        snapshotMap(),
        null,
        null,
        null,
        0,
        false
);
    }

    /*
     * Number of stranger hops already completed.
     *
     * Example:
     *
     * [Alice]
     * strangerHops = 0
     *
     * [Alice, Stranger1]
     * strangerHops = 1
     *
     * [Alice, Stranger1, Stranger2]
     * strangerHops = 2
     */
    int strangerHops = (int) route.stream()
            .filter(nodeId ->
                    nodeId.startsWith("phone-stranger"))
            .count();

    List<VirtualDevice> candidates = new ArrayList<>();

    for (VirtualDevice device : devices.values()) {

        // Never stay on the same node.
        if (device.getDeviceId().equals(currentNodeId)) {
            continue;
        }

        // Never visit a node that already appears in this route.
        if (route.contains(device.getDeviceId())) {
            continue;
        }

        // IMPORTANT:
        // Before the minimum stranger hops are completed,
        // the bridge is NOT a valid candidate.
        if (device.hasInternet() && strangerHops < MIN_STRANGER_HOPS) {
            continue;
        }

        candidates.add(device);
    }

    /*
     * We want exactly two stranger hops before bridge.
     *
     * If we still need stranger hops, only stranger nodes are allowed.
     */
    if (strangerHops < MAX_STRANGER_HOPS) {

        candidates.removeIf(VirtualDevice::hasInternet);

    } else {

        // Minimum stranger hops completed.
        // At this point bridge is the next destination.
        candidates.removeIf(device -> !device.hasInternet());
    }

    if (candidates.isEmpty()) {

        log.warn(
                "No valid next hop for packet {} from {}",
                packetId.substring(0, 8),
                currentNodeId
        );

        return new GossipResult(
        0,
        snapshotMap(),
        null,
        null,
        null,
        0,
        false
);
    }

    // Pick one valid stranger randomly.
    VirtualDevice chosen =
            candidates.get(random.nextInt(candidates.size()));

    // Create the forwarded packet.
    MeshPacket copy = new MeshPacket();

    copy.setPacketId(packet.getPacketId());
    copy.setCiphertext(packet.getCiphertext());
    copy.setCreatedAt(packet.getCreatedAt());
    copy.setTtl(packet.getTtl() - 1);

    chosen.hold(copy);

    // Record hop history.
    hopHistory
            .computeIfAbsent(
                    packetId,
                    k -> new ArrayList<>()
            )
            .add(
                    new Hop(
                            currentNodeId,
                            chosen.getDeviceId(),
                            packetId
                    )
            );

    // Record actual route.
    route.add(chosen.getDeviceId());

    log.info(
            "MESH HOP: {} ---> {} | strangerHops={} | TTL={}",
            currentNodeId,
            chosen.getDeviceId(),
            strangerHops,
            copy.getTtl()
    );

    return new GossipResult(
        1,
        snapshotMap(),
        packetId,
        currentNodeId,
        chosen.getDeviceId(),
        copy.getTtl(),
        chosen.hasInternet()
);
}








/**
 * Counts only offline Stranger nodes in the route.
 *
 * Example:
 *
 * Alice
 *   -> Stranger2
 *   -> Stranger1
 *
 * returns 2.
 */
private int countStrangerHops(List<String> route) {

    int count = 0;

    for (String nodeId : route) {

        if (nodeId.startsWith("phone-stranger")) {
            count++;
        }
    }

    return count;
}

    public Map<String, Integer> snapshotMap() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (VirtualDevice d : devices.values()) {
            m.put(d.getDeviceId(), d.packetCount());
        }
        return m;
    }

    /**
     * Returns all packets held by devices with internet — these are what would
     * be uploaded to the backend the moment they reach connectivity.
     */
    public List<BridgeUpload> collectBridgeUploads() {
        List<BridgeUpload> out = new ArrayList<>();
        for (VirtualDevice d : devices.values()) {
            if (!d.hasInternet()) continue;
            for (MeshPacket pkt : d.getHeldPackets()) {
                out.add(new BridgeUpload(d.getDeviceId(), pkt));
            }
        }
        return out;
    }
    public synchronized void clearPacket(String packetId) {

    if (packetId == null || packetId.isBlank()) {
        return;
    }

    // Remove the packet from every virtual mesh device
    devices.values().forEach(device ->
            device.removePacket(packetId)
    );

    // Remove route information
    packetRoutes.remove(packetId);

    // Remove hop history
    hopHistory.remove(packetId);

    log.info(
            "🧹 Completed packet {} removed from mesh state",
            packetId.substring(0, Math.min(8, packetId.length()))
    );
}

    public void resetMesh() {

    devices.values().forEach(VirtualDevice::clear);

    packetRoutes.clear();
    hopHistory.clear();
}
    public Map<String, List<String>> getPacketRoutes() {
    return packetRoutes;
}

    public record GossipResult(
        int transfers,
        Map<String, Integer> deviceCounts,
        String packetId,
        String from,
        String to,
        int ttl,
        boolean reachedBridge
) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet) {}
    public Map<String,List<Hop>> getHopHistory() {

    return hopHistory;

}
}
