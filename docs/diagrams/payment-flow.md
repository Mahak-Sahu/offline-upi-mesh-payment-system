# Payment Packet Flow

The Offline UPI Mesh Payment System follows a store-and-forward model where an encrypted payment packet can travel through disconnected devices before reaching an internet-connected bridge.

## End-to-End Payment Flow

```mermaid
sequenceDiagram

    participant S as 📱 Alice
    participant M1 as 📱 Stranger 1
    participant M2 as 📱 Stranger 2
    participant M3 as 📱 Stranger 3
    participant B as 🌐 Bridge Phone
    participant API as ☁️ Spring Boot Backend
    participant DB as 🗄️ MySQL

    S->>S: Create PaymentInstruction
    S->>S: Generate nonce & timestamp
    S->>S: Encrypt payment payload

    S->>M1: Inject encrypted MeshPacket

    M1->>M2: Gossip packet
    M2->>M3: Gossip packet
    M3->>B: Forward packet

    B->>API: Upload encrypted packet

    API->>API: Calculate packet hash
    API->>API: Check idempotency

    alt New packet
        API->>API: Decrypt packet
        API->>API: Verify integrity
        API->>API: Check packet freshness
        API->>DB: Execute settlement
        DB-->>API: Settlement successful
        API-->>B: SETTLED
    else Duplicate packet
        API-->>B: DUPLICATE_DROPPED
    end