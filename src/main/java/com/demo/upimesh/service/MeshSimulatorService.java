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
    public GossipResult gossipOnce() {

    int transfers = 0;

    List<VirtualDevice> deviceList = new ArrayList<>(devices.values());

    // Snapshot of current packets
    Map<String, List<MeshPacket>> snapshot = new HashMap<>();

    for (VirtualDevice d : deviceList) {
        snapshot.put(d.getDeviceId(),
                new ArrayList<>(d.getHeldPackets()));
    }

    for (VirtualDevice src : deviceList) {

        for (MeshPacket pkt : snapshot.get(src.getDeviceId())) {

            if (pkt.getTtl() <= 0)
                continue;

            // Build candidate neighbours
            List<VirtualDevice> candidates = new ArrayList<>();

            for (VirtualDevice dst : deviceList) {

                if (dst == src)
                    continue;

                if (dst.holds(pkt.getPacketId()))
                    continue;

                candidates.add(dst);
            }

            if (candidates.isEmpty())
                continue;

            // Prefer bridge if available
            VirtualDevice chosen = null;

            for (VirtualDevice d : candidates) {

                if (d.hasInternet()) {
                    chosen = d;
                    break;
                }
            }

            // Otherwise choose random neighbour
            if (chosen == null) {

                chosen = candidates.get(
                        random.nextInt(candidates.size())
                );
            }

            MeshPacket copy = new MeshPacket();

            copy.setPacketId(pkt.getPacketId());
            copy.setCiphertext(pkt.getCiphertext());
            copy.setCreatedAt(pkt.getCreatedAt());
            copy.setTtl(pkt.getTtl() - 1);

            chosen.hold(copy);
            hopHistory
    .computeIfAbsent(
        pkt.getPacketId(),
        k -> new ArrayList<>()
    )
    .add(
        new Hop(
            src.getDeviceId(),
            chosen.getDeviceId(),
            pkt.getPacketId()
        )
    );
            packetRoutes
                    .computeIfAbsent(pkt.getPacketId(),
                            k -> new ArrayList<>())
                    .add(chosen.getDeviceId());

            transfers++;

            log.info("{}  --->  {}",
                    src.getDeviceId(),
                    chosen.getDeviceId());
        }
    }

    log.info("Dynamic Gossip : {} transfers", transfers);

    return new GossipResult(transfers, snapshotMap());
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

    public void resetMesh() {

    devices.values().forEach(VirtualDevice::clear);

    packetRoutes.clear();
}
    public Map<String, List<String>> getPacketRoutes() {
    return packetRoutes;
}

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet) {}
    public Map<String,List<Hop>> getHopHistory() {

    return hopHistory;

}
}
