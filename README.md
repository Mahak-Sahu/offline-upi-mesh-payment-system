# 🔐 Offline UPI Mesh Payment System

> **A secure store-and-forward payment architecture for connectivity-constrained environments.**

An experimental Spring Boot system that demonstrates how a payment instruction can be created while the sender is offline, encrypted end-to-end, propagated through a device-to-device mesh, carried by an internet-connected bridge device, and finally processed by a backend with **idempotent settlement, replay protection, authenticated encryption, and transactional ledger updates**.

---

## 🚀 Core Idea

Traditional digital payment systems assume that the sender can communicate directly with a payment backend.

This project explores the opposite scenario:

**What if the sender has no internet connection at all?**

Instead of requiring a direct connection to the backend, the payment is converted into an encrypted packet that can travel through nearby devices until it reaches a device that has internet connectivity.

```text
                         OFFLINE ENVIRONMENT
                              │
                              ▼
                    ┌─────────────────┐
                    │  📱 Alice Phone │
                    │  Create Payment │
                    └────────┬────────┘
                             │
                       🔐 Encrypt
                             │
                             ▼
                    ┌─────────────────┐
                    │ 📱 Mesh Device  │
                    │  Store Packet   │
                    └────────┬────────┘
                             │
                       Mesh Gossip
                             │
                             ▼
                    ┌─────────────────┐
                    │ 📱 Bridge Phone │
                    │   + 4G/Internet │
                    └────────┬────────┘
                             │
                           HTTPS
                             │
                             ▼
                    ┌─────────────────┐
                    │ Spring Boot API │
                    └────────┬────────┘
                             │
                    Security Validation
                             │
                             ▼
                    ┌─────────────────┐
                    │   Settlement    │
                    │     + MySQL     │
                    └─────────────────┘
```

### Payment Lifecycle

```text
Payment Creation
       ↓
Encryption
       ↓
Offline Storage
       ↓
Mesh Propagation
       ↓
Bridge Connectivity
       ↓
Backend Ingestion
       ↓
Security Validation
       ↓
Duplicate Protection
       ↓
Settlement
       ↓
Transaction Ledger
```

---

## ✨ What Makes This Project Different

The project focuses on the problem of performing a payment workflow when the sender temporarily has **no direct internet connectivity**.

The core architectural challenge is:

> **How can an encrypted payment survive a period of connectivity loss and still reach the backend safely and settle only once when connectivity returns?**

The system separates the payment into independent stages:

```text
📱 Sender
   │
   │ Create PaymentInstruction
   ▼
🔐 Encryption
   │
   │ AES-256-GCM
   │ RSA-2048 OAEP
   ▼
📦 MeshPacket
   │
   │ Store-and-Forward
   ▼
📱 Device → 📱 Device → 📱 Device
   │
   │ Mesh Gossip
   ▼
🌐 Bridge Device
   │
   │ Internet Available
   ▼
☁️ Spring Boot Backend
   │
   ├── Idempotency Check
   ├── Decryption
   ├── Freshness Validation
   ├── AES-GCM Authentication
   └── Settlement
   │
   ▼
🗄️ MySQL Ledger
```

The design combines concepts from:

- Offline-first systems
- Store-and-forward networking
- Device-to-device mesh communication
- Hybrid cryptography
- Idempotent distributed processing
- Replay protection
- Transactional database settlement

---

## 🔐 Security Model

The payment information is encrypted before entering the mesh.

Intermediate devices carry the encrypted packet but do not need access to the plaintext payment instruction.

```text
PaymentInstruction
       │
       ▼
JSON Serialization
       │
       ▼
Generate AES-256 Session Key
       │
       ▼
AES-256-GCM Encryption
       │
       ▼
Encrypted Payment Payload
       │
       │
       └──────────────┐
                      │
              AES Session Key
                      │
                      ▼
              RSA-2048 OAEP
                      │
                      ▼
             Encrypted AES Key
                      │
                      ▼
                MeshPacket
```

### 🔑 Hybrid Encryption

The system uses a hybrid encryption design:

- **AES-256-GCM** encrypts the actual payment payload.
- **RSA-2048 OAEP** encrypts the temporary AES session key.
- **AES-GCM authentication** provides ciphertext integrity.
- A fresh AES key is generated for every payment packet.

The resulting encrypted packet contains:

```text
[ RSA-encrypted AES Key ]
          +
[ GCM Initialization Vector ]
          +
[ AES-GCM Ciphertext + Authentication Tag ]
```

This allows the payment payload to remain confidential while travelling through the mesh.

---

## 📦 Payment Packet Lifecycle

A payment moves through the system as an encrypted packet rather than as a direct online transaction.

```text
┌─────────────────┐
│ 1. CREATE       │
│ Payment Intent  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 2. ENCRYPT      │
│ AES-256-GCM     │
│ + RSA-OAEP      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 3. INJECT       │
│ Into Sender     │
│ Device          │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 4. GOSSIP       │
│ Device-to-Device│
│ Propagation     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 5. BRIDGE       │
│ Device Gets     │
│ Internet        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 6. INGEST       │
│ Backend Receives│
│ Packet          │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 7. VALIDATE     │
│ Hash + Decrypt  │
│ + Freshness     │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 8. SETTLE       │
│ Debit + Credit  │
│ Transactionally │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 9. RECORD       │
│ Permanent       │
│ Transaction     │
└─────────────────┘
```

### Packet Structure

```text
                    MeshPacket
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
    packetId           TTL          createdAt
        │
        │
        └───────────────┐
                        ▼
                   ciphertext
                        │
                        ▼
              Encrypted Payment Data
```

The packet can remain stored on intermediate devices while the network is unavailable. Once a bridge device obtains internet connectivity, the packet can be delivered to the backend for validation and settlement.

---

## 🌐 Offline Mesh Propagation

The mesh layer follows a **store-and-forward** approach.

A device does not need internet connectivity to participate in packet delivery. It can temporarily store an encrypted payment packet and forward it when another suitable device becomes available.

```text
                         ☁️ Backend
                             ▲
                             │
                          HTTPS
                             │
                             │
                    ┌────────┴────────┐
                    │  🌐 Bridge Phone │
                    │     + 4G         │
                    └────────▲────────┘
                             │
                          Gossip
                             │
                    ┌────────┴────────┐
                    │ 📱 Stranger 3   │
                    │   Offline       │
                    └────────▲────────┘
                             │
                          Gossip
                             │
                    ┌────────┴────────┐
                    │ 📱 Stranger 2   │
                    │   Offline       │
                    └────────▲────────┘
                             │
                          Gossip
                             │
                    ┌────────┴────────┐
                    │ 📱 Stranger 1   │
                    │   Offline       │
                    └────────▲────────┘
                             │
                          Gossip
                             │
                    ┌────────┴────────┐
                    │   📱 Alice      │
                    │  No Internet    │
                    └─────────────────┘
```

### Store-and-Forward Model

```text
No Internet
     │
     ▼
Create encrypted packet
     │
     ▼
Store locally
     │
     ▼
Discover another device
     │
     ▼
Forward packet
     │
     ▼
Repeat until bridge is reached
     │
     ▼
Bridge obtains connectivity
     │
     ▼
Upload packet to backend
```

### TTL-Based Propagation

Every packet contains a **TTL (hop limit)** value representing the maximum number of remaining mesh forwarding hops.

```text
Initial TTL = 5

Alice
  │
  │ TTL 5 → 4
  ▼
Stranger 1
  │
  │ TTL 4 → 3
  ▼
Stranger 2
  │
  │ TTL 3 → 2
  ▼
Stranger 3
  │
  │ TTL 2 → 1
  ▼
Bridge
```

A packet with `TTL = 0` is not forwarded further. This prevents uncontrolled propagation through the mesh.

---

## 🌐 Bridge Node & Backend Ingestion

A bridge node is a mesh device that currently has internet connectivity.

The bridge does not need to decrypt or understand the payment. Its role is to deliver the encrypted packet to the backend.

```text
📦 Encrypted MeshPacket
          │
          ▼
   🌐 Bridge Node
          │
          │ Internet Available
          ▼
 POST /api/bridge/ingest
          │
          ▼
 ┌──────────────────────┐
 │  Backend Processing  │
 └──────────┬───────────┘
            │
            ▼
      Security Pipeline
            │
            ▼
        Settlement
```

### Backend Ingestion Pipeline

```text
Bridge Upload
     │
     ▼
SHA-256 Ciphertext Hash
     │
     ▼
Idempotency Check
     │
     ▼
Decrypt Ciphertext
     │
     ▼
Freshness Validation
     │
     ▼
Settlement Service
     │
     ▼
MySQL Transaction
```

The bridge communicates with the backend through:

```text
POST /api/bridge/ingest
```

The request can include bridge and hop information so the backend can record how the packet reached the internet-connected node.

### Bridge Upload Result

A successfully processed packet can result in:

```text
SETTLED
```

A packet that has already been processed results in:

```text
DUPLICATE_DROPPED
```

Invalid or stale packets are rejected instead of being settled.

---

## ♻️ Exactly-Once Settlement

Offline delivery creates a distributed-systems problem: the same encrypted payment packet may reach the backend through multiple bridge devices.

For example:

```text
                         ┌── Bridge A ──┐
                         │              │
Alice → Mesh ────────────┼── Bridge B ──┼──→ Backend
                         │              │
                         └── Bridge C ──┘
```

All three bridges may attempt to upload the same packet.

The backend prevents multiple settlements using the encrypted packet's **SHA-256 hash** as the idempotency key.

```text
Encrypted Packet
       │
       ▼
SHA-256 Ciphertext Hash
       │
       ▼
Idempotency Check
       │
       ├──────────────► Already seen
       │                      │
       │                      ▼
       │               DUPLICATE_DROPPED
       │
       ▼
First delivery
       │
       ▼
Decrypt + Validate
       │
       ▼
Settlement
       │
       ▼
SETTLED
```

### Duplicate Delivery Scenario

```text
Bridge A ──┐
           │
Bridge B ──┼──► Same Packet Hash ──► Backend
           │
Bridge C ──┘

First request  → SETTLED
Second request → DUPLICATE_DROPPED
Third request  → DUPLICATE_DROPPED
```

This ensures that a single payment packet cannot cause the same account transfer to be applied multiple times through repeated bridge delivery.

### Defense in Depth

The system uses multiple layers of protection:

```text
Idempotency Cache
       ↓
SHA-256 Packet Hash
       ↓
Database Unique Constraint
       ↓
Optimistic Account Locking
       ↓
Transactional Settlement
```

The result is an **exactly-once settlement behavior for duplicate packet deliveries within the system's idempotency window**.

---

## 🛡️ Replay Protection & Tamper Detection

Offline payments introduce two important security concerns:

1. An old payment packet could be replayed later.
2. An encrypted packet could be modified while travelling through the mesh.

The system addresses both concerns before settlement.
### ⏱️ Replay Protection

Every `PaymentInstruction` contains a unique nonce and a timestamp:

```text
PaymentInstruction
├── senderVpa
├── receiverVpa
├── amount
├── pinHash
├── nonce
└── signedAt
```
The `signedAt` field records when the payment instruction was created. The backend uses this timestamp to enforce a freshness window.
```text
Packet Received
      │
      ▼
Read signedAt
      │
      ▼
Freshness Check
      │
   ┌──┴──┐
   │     │
 VALID  STALE
   │     │
   ▼     ▼
Continue Reject
```
The backend rejects packets that are older than the configured freshness window.

Packets with timestamps significantly ahead of the server clock are also rejected.

The `nonce` provides a unique identifier for each payment intent, so two separate payment creations can be distinguished even when the sender, receiver, and amount are the same.

Together with the ciphertext-based idempotency mechanism, the freshness check helps protect the system against repeated delivery of old payment packets.
---

### 🔒 Tamper Detection

The payment payload is protected using AES-256-GCM authenticated encryption.

```text
PaymentInstruction
       │
       ▼
AES-256-GCM
       │
       ▼
Ciphertext + Authentication Tag
       │
       ▼
Mesh Network
       │
       ▼
Backend Decryption
       │
   ┌───┴────┐
   │        │
 VALID    INVALID
   │        │
   ▼        ▼
Continue   Reject
```

If the encrypted payload is modified, AES-GCM authentication fails during decryption.

The backend therefore rejects the modified packet instead of processing it.

### 🔐 Security Boundary

Intermediate mesh devices can:

```text
✓ Store the packet
✓ Forward the packet
✓ Track packet metadata
✓ Decrease TTL
```

But they cannot:

```text
✗ Read the encrypted payment payload
✗ Decrypt the payment
✗ Modify the payment without detection
```

---
## 💾 Transactional Settlement & Ledger

Once a packet passes all security and validation checks, the payment is handed to the settlement layer.

The settlement operation is performed inside a database transaction so the sender debit and receiver credit are treated as one atomic operation.

```text
Validated Payment
       │
       ▼
┌──────────────────────┐
│ Load Sender Account  │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Load Receiver Account│
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ Check Balance        │
└──────────┬───────────┘
           │
           ▼
      ┌────┴────┐
      │         │
   Enough    Insufficient
   Balance     Balance
      │         │
      ▼         ▼
   Debit &    REJECTED
   Credit
      │
      ▼
┌──────────────────────┐
│ Save Transaction     │
│ in MySQL             │
└──────────────────────┘
```

### Atomic Transfer

```text
Alice Balance
     │
     ├── ₹100 debit
     │
     ▼
Transaction
     │
     ├── ₹100 credit
     │
     ▼
Bob Balance
```

Both account updates occur inside the same transactional boundary.

If the sender does not have sufficient balance, the payment is recorded as `REJECTED` and no debit/credit transfer is performed.

### Concurrency Protection

The account entity uses **optimistic locking** through a version field.

```text
Concurrent Payment Requests
          │
          ▼
    Account Version
          │
     ┌────┴────┐
     │         │
   Update    Conflict
     │         │
     ▼         ▼
  Success   Retry Required
```

This provides an additional layer of protection against concurrent updates to the same account balance.

---

## 🧪 Testing & Reliability

The project includes automated tests for the most important security and distributed-systems scenarios.

### Test Coverage

```text
┌─────────────────────────────────────────┐
│              Test Suite                 │
├─────────────────────────────────────────┤
│                                         │
│  🔐 Encryption / Decryption             │
│       ↓                                 │
│  🛡️ Tampered Ciphertext                │
│       ↓                                 │
│  ♻️ Concurrent Duplicate Delivery       │
│       ↓                                 │
│  💳 Exactly-Once Settlement            │
│                                         │
└─────────────────────────────────────────┘
```

### 🔐 Encryption Round-Trip

The test verifies that a payment instruction encrypted with the server's public key can be successfully decrypted using the corresponding private key.

```text
PaymentInstruction
       │
       ▼
Encrypt
       │
       ▼
Ciphertext
       │
       ▼
Decrypt
       │
       ▼
Original PaymentInstruction
```

### 🛡️ Tampered Ciphertext

The test modifies the encrypted ciphertext before submitting it to the backend.

Expected result:

```text
INVALID
```

The payment must never reach settlement.

### ♻️ Concurrent Duplicate Delivery

The project simulates three bridge devices delivering the same payment packet simultaneously.

```text
              Same Packet
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
     Bridge 1   Bridge 2   Bridge 3
        │          │          │
        └──────────┼──────────┘
                   ▼
                Backend
```

Expected result:

```text
1 × SETTLED
2 × DUPLICATE_DROPPED
```

The test also verifies that the sender and receiver balances change exactly once.


---
## 🏗️ System Architecture

The system is divided into four major layers:

```text
┌──────────────────────────────────────────────┐
│              Sender Device                  │
│          Payment Creation + Crypto          │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│               Mesh Network                  │
│       Store → Forward → Gossip → TTL        │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│             Bridge Device                  │
│       Internet Connectivity + Upload       │
└──────────────────────┬───────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────┐
│             Backend Server                 │
│ Crypto → Idempotency → Validation →        │
│              Settlement → MySQL            │
└──────────────────────────────────────────────┘
```

### Main Components

| Component | Responsibility |
|---|---|
| Sender Device | Creates and encrypts the payment instruction |
| Mesh Devices | Store and forward encrypted packets |
| Bridge Device | Uploads packets when internet connectivity is available |
| Spring Boot Backend | Receives, validates, decrypts and processes packets |
| Idempotency Layer | Prevents duplicate settlement |
| Settlement Service | Performs atomic debit and credit |
| MySQL | Stores accounts and transaction records |

---
## 🖥️ Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21 |
| Framework | Spring Boot |
| API | Spring REST |
| Database | MySQL |
| ORM | Spring Data JPA / Hibernate |
| Encryption | AES-256-GCM |
| Key Encryption | RSA-2048 OAEP |
| Hashing | SHA-256 |
| Mesh Simulation | Java Virtual Devices |
| Testing | JUnit 5 / Spring Boot Test |
| Frontend | HTML, CSS, JavaScript |
| Build Tool | Maven |

---
## 📁 Project Structure

```text
offline-upi-mesh-payment-system/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/demo/upimesh/
│   │   │       ├── controller/
│   │   │       │   └── ApiController.java
│   │   │       │
│   │   │       ├── crypto/
│   │   │       │   ├── HybridCryptoService.java
│   │   │       │   └── ServerKeyHolder.java
│   │   │       │
│   │   │       ├── model/
│   │   │       │   ├── Account.java
│   │   │       │   ├── MeshPacket.java
│   │   │       │   ├── PaymentInstruction.java
│   │   │       │   └── Transaction.java
│   │   │       │
│   │   │       └── service/
│   │   │           ├── BridgeIngestionService.java
│   │   │           ├── DemoService.java
│   │   │           ├── IdempotencyService.java
│   │   │           ├── MeshSimulatorService.java
│   │   │           ├── SettlementService.java
│   │   │           └── VirtualDevice.java
│   │   │
│   │   └── resources/
│   │       ├── static/
│   │       ├── templates/
│   │       └── application.properties
│   │
│   └── test/
│       └── java/
│           └── com/demo/upimesh/
│               └── IdempotencyConcurrencyTest.java
│
├── docs/
│   ├── diagrams/
│   │   ├── system-architecture.md
│   │   └── payment-packet-flow.md
│   │
│   ├── images/
│   └── gifs/
│
├── pom.xml
└── README.md
```

### Main Package Responsibilities

- **`controller`** — REST API endpoints for the mesh, bridge, accounts and transactions.
- **`crypto`** — Hybrid encryption, decryption and server key management.
- **`model`** — Payment packets, payment instructions, accounts and transactions.
- **`service`** — Mesh simulation, bridge ingestion, idempotency and settlement logic.
- **`test`** — Automated security, concurrency and payment-flow tests.
- **`docs`** — Architecture diagrams and project documentation.

---
## 🚀 Getting Started

### Prerequisites

Make sure the following are installed on your system:

- Java 21
- Maven
- MySQL 8+
- Git

### 1. Clone the Repository

```bash
git clone https://github.com/Mahak-Sahu/offline-upi-mesh-payment-system.git
cd offline-upi-mesh-payment-system
```

### 2. Create the MySQL Database

Open MySQL and create the database:

```sql
CREATE DATABASE offline_upi;
```

### 3. Configure Database Credentials

The application reads database credentials from environment variables.

Set:

```text
DB_USERNAME=your_mysql_username
DB_PASSWORD=your_mysql_password
```

Do **not** commit your real database password to GitHub.

### 4. Run the Application

Using Maven:

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package
java -jar target/*.jar
```

### 5. Open the Dashboard

Once the application starts, open:

```text
http://localhost:8080
```

The dashboard allows you to simulate:

```text
Send Payment
     ↓
Mesh Gossip
     ↓
Bridge Upload
     ↓
Backend Processing
     ↓
Settlement
```

---
## 🎬 Demo Workflow

The application provides a visual dashboard to demonstrate the complete offline payment lifecycle.

### Typical Demo Sequence

```text
1. Reset Demo
      ↓
2. Send Payment
      ↓
3. Observe Packet Creation
      ↓
4. Run Mesh Gossip
      ↓
5. Packet Reaches Bridge
      ↓
6. Flush Bridge
      ↓
7. Backend Validates Packet
      ↓
8. Payment Settles
      ↓
9. Transaction Appears in Ledger
```

### Dashboard

The dashboard visualizes:

- Mesh devices and their connectivity state
- Packets currently held by each device
- Packet propagation through the mesh
- Bridge-node delivery
- Account balances
- Transaction history
- Idempotency cache state
- Settlement results

### Example Payment

```text
Alice
₹100
  │
  ▼
Encrypted Packet
  │
  ▼
Offline Mesh
  │
  ▼
Bridge Phone
  │
  ▼
Backend
  │
  ▼
SETTLED
  │
  ├── Alice: ₹5000 → ₹4900
  └── Bob:   ₹1000 → ₹1100
```

---
## 🔌 API Endpoints

### Server Key

```text
GET /api/server-key
```

Returns the server public key used by simulated senders for packet encryption.

### Send Demo Payment

```text
POST /api/demo/send
```

Creates an encrypted payment packet and injects it into the mesh.

### Mesh State

```text
GET /api/mesh/state
```

Returns current device state, packet routes, hop history, and idempotency information.

### Gossip

```text
POST /api/mesh/gossip
```

Simulates one round of mesh packet propagation.

### Flush Bridge

```text
POST /api/mesh/flush
```

Uploads packets held by internet-connected bridge devices.

### Bridge Ingestion

```text
POST /api/bridge/ingest
```

The backend endpoint used by a bridge device to deliver an encrypted packet.

### Accounts

```text
GET /api/accounts
```

Returns the demo account ledger.

### Transactions

```text
GET /api/transactions
```

Returns recent transactions.

---
## 📚 Documentation

Detailed technical documentation is available in the `docs/` directory.

### Architecture

- [System Architecture](docs/diagrams/system-architecture.md)
- [Payment Packet Flow](docs/diagrams/payment-packet-flow.md)

### Documentation Structure

```text
docs/
├── diagrams/
│   ├── system-architecture.md
│   └── payment-packet-flow.md
│
├── images/
└── gifs/
```

The documentation explains the system architecture, encrypted packet lifecycle, mesh propagation model, backend security pipeline, and settlement flow.

---
## 📸 Dashboard Preview

### 🖥️ Main Dashboard

The dashboard provides a visual overview of the offline UPI mesh payment system, including payment creation, mesh simulation, and system status.

![Dashboard](docs/images/dashboard.png)

### 🌐 Live Mesh Network

The live mesh view shows the connected and offline devices participating in the store-and-forward network, including the internet-connected bridge phone.

![Live Mesh Network](docs/images/live-mesh.png)

### 📱 Mesh Devices

This view displays the simulated devices, their connectivity status, and the number of packets currently held by each device.

![Mesh Devices](docs/images/mesh-devices.png)

### 📜 Activity Log

The activity log shows packet encryption, mesh propagation, bridge uploads, settlement, and duplicate detection events.

![Activity Log](docs/images/activity-log.png)

---
## ⚠️ Project Scope

This project is an **experimental prototype and architectural demonstration** of an offline store-and-forward payment system.

It is **not a production UPI implementation** and does not connect to NPCI, real bank accounts, or live UPI infrastructure.

The mesh network and bridge devices are simulated in software to demonstrate the core concepts of:

- Offline payment packet creation
- End-to-end encrypted packet transport
- Store-and-forward mesh propagation
- Bridge-based connectivity recovery
- Duplicate delivery protection
- Replay protection
- Transactional settlement
- Concurrent payment handling

The architecture is designed to demonstrate how these concepts could fit together in a connectivity-constrained payment environment.

---
## 🔮 Future Improvements

Potential extensions include:

- Real Android BLE mesh communication
- Real device discovery and proximity detection
- Persistent encrypted offline packet storage
- Redis-based distributed idempotency
- Hardware-backed key storage
- Device authentication and certificates
- Digital signatures for payment authorization
- Production-grade distributed settlement
- Network partition and failure simulation
- Observability and metrics
- Containerized deployment

---

## 👨‍💻 Author

**Mahak Sahu**

Computer Science — Artificial Intelligence & Data Science

This project was built as an exploration of secure offline payment architecture, distributed packet delivery, and reliable transaction settlement in connectivity-constrained environments.

---
## ⭐ Key Takeaways

This project demonstrates how a payment system can continue operating across a temporary loss of internet connectivity by separating **payment creation** from **payment settlement**.

The key architectural ideas are:

```text
Offline Payment Creation
        ↓
End-to-End Encryption
        ↓
Store-and-Forward Mesh
        ↓
Bridge Connectivity
        ↓
Idempotent Backend Ingestion
        ↓
Replay & Integrity Validation
        ↓
Transactional Settlement
```

The project combines concepts from:

- 🔐 Applied Cryptography
- 📡 Distributed Systems
- ♻️ Idempotency & Duplicate Handling
- 🛡️ Replay & Integrity Protection
- 💾 Database Transactions
- ⚡ Concurrent Request Handling
- 🌐 Offline-First Architecture

---

## 📜 License

This project is intended for educational, research, and architectural demonstration purposes.

---
## 🔗 Repository

**GitHub:**  
https://github.com/Mahak-Sahu/offline-upi-mesh-payment-system

---

<p align="center">
  Built with Java, Spring Boot, MySQL and a strong focus on security, reliability and offline-first architecture.
</p>

