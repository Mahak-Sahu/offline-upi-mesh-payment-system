
function log(msg) {
    const el = document.getElementById('log');
    el.textContent = '[' + new Date().toLocaleTimeString() + '] ' + msg + '\n' + el.textContent;
}
/*function drawConnections() {

    const topology = document.getElementById("mesh-topology");

    if (!topology) return;

    // remove previous svg
    const old = document.getElementById("network-svg");
    if (old) old.remove();

    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");

    svg.setAttribute("id", "network-svg");

    svg.style.position = "absolute";
    svg.style.left = "0";
    svg.style.top = "0";
    svg.style.width = "100%";
    svg.style.height = "100%";
    svg.style.pointerEvents = "none";

    topology.style.position = "relative";

    topology.appendChild(svg);

    const bridge = document.getElementById("bridge-node");
    const internet = document.getElementById("internet-node");

    const alice = document.getElementById("alice-node");
    const s1 = document.getElementById("stranger1-node");
    const s2 = document.getElementById("stranger2-node");
    const s3 = document.getElementById("stranger3-node");

    function center(el, side = "center") {

    const r = el.getBoundingClientRect();
    const parent = topology.getBoundingClientRect();
    switch(side){

    case "top":
        return {
            x: r.left - parent.left + r.width/2,
            y: r.top - parent.top
        };

    case "bottom":
        return {
            x: r.left - parent.left + r.width/2,
            y: r.bottom - parent.top
        };

    case "left":
        return {
            x: r.left - parent.left,
            y: r.top - parent.top + r.height/2
        };

    case "right":
        return {
            x: r.right - parent.left,
            y: r.top - parent.top + r.height/2
        };

    default:
        return {
            x: r.left - parent.left + r.width/2,
            y: r.top - parent.top + r.height/2
        };
}
}

    function line(a,b){

    let startSide="bottom";
    let endSide="top";

    if(a.id==="bridge-node"){
        startSide="bottom";
    }

    if(a.id==="internet-node"){
        startSide="bottom";
    }

    const p1=center(a,startSide);
    const p2=center(b,endSide);

    const l=document.createElementNS(
        "http://www.w3.org/2000/svg",
        "line"
    );

    l.setAttribute("x1",p1.x);
    l.setAttribute("y1",p1.y);

    l.setAttribute("x2",p2.x);
    l.setAttribute("y2",p2.y);

    l.setAttribute("stroke","#2f81f7");
    l.setAttribute("stroke-width","4");
    l.setAttribute("stroke-linecap","round");

    svg.appendChild(l);
}

    // Internet → Bridge
line(internet, bridge);

// Bridge → S1
line(bridge, s1);

// S1 → S3
line(s1, s3);

// S1 → Alice
line(s1, alice);

// Alice → S2
line(alice, s2);

// S2 → S3
line(s2, s3);

}
 */ 
function removeLivePacket() {

    const packet = document.getElementById("packet");

    if (packet) {
        packet.style.display = "none";
    }

}
function movePacket(fromId, toId) {

    const packet = document.getElementById("packet");

    const from = document.getElementById(fromId);

    const to = document.getElementById(toId);

    if(!packet || !from || !to) return;

    const topology = document.getElementById("mesh-topology");

    const parent = topology.getBoundingClientRect();

    const a = from.getBoundingClientRect();

    const b = to.getBoundingClientRect();

    packet.style.display="block";

    packet.style.left=(a.left-parent.left+a.width/2-9)+"px";

    packet.style.top=(a.top-parent.top+a.height/2-9)+"px";

    requestAnimationFrame(()=>{

        packet.style.left=(b.left-parent.left+b.width/2-9)+"px";

        packet.style.top=(b.top-parent.top+b.height/2-9)+"px";

    });

}

async function refresh() {
    // Mesh state
    const m = await fetch('/api/mesh/state').then(r => r.json());
    window.meshState = m;
    const devicesDiv = document.getElementById('devices');
    devicesDiv.innerHTML = m.devices.map(d => `
        <div class="device ${d.hasInternet ? 'bridge' : 'offline'}">
            <strong>${d.deviceId}</strong>
            <span class="badge ${d.hasInternet ? 'badge-online' : 'badge-offline'}">
                ${d.hasInternet ? '🌐 4G' : '🚫 OFFLINE'}
            </span>
            <span class="small">holding ${d.packetCount} packet(s)</span>
            <div>${d.packetIds.map(id => `<span class="packet-id">${id}</span>`).join('')}</div>
        </div>
    `).join('');
    document.getElementById('cacheInfo').textContent =
        `Idempotency cache size: ${m.idempotencyCacheSize}`;

    // Accounts
    const accs = await fetch('/api/accounts').then(r => r.json());
    document.querySelector('#accounts-table tbody').innerHTML = accs.map(a => `
        <tr><td>${a.vpa}</td><td>${a.holderName}</td>
            <td class="balance">₹${parseFloat(a.balance).toFixed(2)}</td></tr>
    `).join('');

    // Transactions
    const txs = await fetch('/api/transactions').then(r => r.json());
    document.querySelector('#tx-table tbody').innerHTML = txs.map(t => `
        <tr>
            <td>${t.id}</td><td>${t.senderVpa}</td><td>${t.receiverVpa}</td>
            <td class="balance">₹${parseFloat(t.amount).toFixed(2)}</td>
            <td class="status-${t.status}">${t.status}</td>
            <td>${t.bridgeNodeId}</td><td>${t.hopCount}</td>
            <td class="small">${new Date(t.settledAt).toLocaleTimeString()}</td>
        </tr>
    `).join('');
    //drawConnections();
    updateTopology(m.devices);
}
function updateTopology(devices){

    const map = {
        "phone-alice":"alice-node",
        "phone-stranger1":"stranger1-node",
        "phone-stranger2":"stranger2-node",
        "phone-stranger3":"stranger3-node",
        "phone-bridge":"bridge-node"
    };

    // Reset all nodes
    Object.values(map).forEach(id=>{

        const node=document.getElementById(id);

        if(!node) return;

        node.classList.remove(
            "node-online",
            "node-offline",
            "node-holding",
            "node-bridge"
        );

    });

    devices.forEach(device=>{

        const id=map[device.deviceId];

        const node=document.getElementById(id);

        if(!node) return;

        if(device.hasInternet){

            node.classList.add("node-bridge");

        }else if(device.packetCount>0){

            node.classList.add("node-holding");

        }else{

            node.classList.add("node-offline");

        }

    });

}

function getNodeCenter(deviceId){

    const htmlId = deviceId
        .replace("phone-", "")
        + "-node";

    const node = document.getElementById(htmlId);

    const topology = document.getElementById("mesh-topology");

    const r = node.getBoundingClientRect();
    const p = topology.getBoundingClientRect();

    return {

        x: r.left - p.left + r.width / 2,
        y: r.top - p.top + r.height / 2

    };

}
async function animateHop(fromDevice, toDevice){

    const packet = document.getElementById("packet");

    const from = getNodeCenter(fromDevice);
    const to = getNodeCenter(toDevice);

    packet.style.display = "block";

    // Start position
    packet.style.left = (from.x - 9) + "px";
packet.style.top  = (from.y - 9) + "px";

    // Browser ko first position render karne do
    await new Promise(r => requestAnimationFrame(r));

    // Move
    packet.style.left = (to.x - 9) + "px";
packet.style.top  = (to.y - 9) + "px";

    // Wait for animation
    await new Promise(r => setTimeout(r, 900));

}
async function animateRoute(packetId) {

    const route =
        window.meshState?.routes?.[packetId];

    if (!route || route.length < 2) {
        return;
    }

    /*
     * Backend gives the actual route:
     *
     * phone-alice
     * phone-stranger2
     * phone-stranger1
     * phone-bridge
     *
     * Frontend simply visualizes that route.
     */

    for (let i = 0; i < route.length - 1; i++) {

        const fromDevice = route[i];
        const toDevice = route[i + 1];

        log(
            `📦 LIVE MESH: ${fromDevice} → ${toDevice}`
        );

        await animateHop(
            fromDevice,
            toDevice
        );
    }
}


async function sendPacket() {
    const body = {
        senderVpa: document.getElementById('senderVpa').value,
        receiverVpa: document.getElementById('receiverVpa').value,
        amount: parseFloat(document.getElementById('amount').value),
        pin: document.getElementById('pin').value,
        ttl: 5,
        startDevice: 'phone-alice'
    };
    const r = await fetch('/api/demo/send', {
        method: 'POST', headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(body)
    }).then(r => r.json());
    log(`📤 Packet ${r.packetId.substring(0,8)} encrypted & injected at ${r.injectedAt} (TTL ${r.ttl})`);
    log(`   ciphertext (truncated): ${r.ciphertextPreview}`);

const gossipButton =
    document.getElementById("gossipButton");

if (gossipButton) {

    gossipButton.disabled = false;

    gossipButton.textContent =
        "🚀 Start Gossip";
}

const uploadButton =
    document.getElementById("uploadButton");

if (uploadButton) {

    uploadButton.disabled = true;

    uploadButton.textContent =
        "📡 Upload to Backend";
}

await refresh();
}

let gossipRunning = false;

const GOSSIP_DELAY = 2000; // 2 seconds between mesh hops

async function gossip() {

    // Prevent multiple gossip processes from running together
    if (gossipRunning) {
        return;
    }

    gossipRunning = true;

    const button = document.getElementById("gossipButton");

    if (button) {
        button.disabled = true;
        button.textContent = "⏳ Gossiping...";
    }

    log("🚀 Automatic mesh gossip started...");

    try {

        while (true) {

            // Perform exactly ONE mesh hop
            const response = await fetch(
                '/api/mesh/gossip',
                {
                    method: 'POST'
                }
            );

            if (!response.ok) {
                throw new Error(
                    `Gossip request failed: ${response.status}`
                );
            }

            const result = await response.json();

            log(
                `🔄 Gossip hop: ${result.transfers} transfer(s) — ${JSON.stringify(result.deviceCounts)}`
            );
            if (
                result.transfers === 1 &&
                result.from &&
                result.to
            ) {

                log(
                    `📦 LIVE MESH: ${result.from} → ${result.to} | TTL=${result.ttl}`
                );

                await animateHop(
                    result.from,
                    result.to
                );
            }

            // Update current mesh state
            await refresh();

            /*
             * If backend could not perform another hop,
             * gossip is finished.
             */
            if (result.transfers === 0) {

                log("🏁 No more mesh hops available.");

                break;
            }

            if (result.reachedBridge) {

                log("🌐 Packet reached Bridge Phone!");

                const uploadButton =
                    document.getElementById("uploadButton");

                if (uploadButton) {

                    uploadButton.disabled = false;

                    uploadButton.textContent =
                        "📡 Upload to Backend";
                }

                break;
            }
            
            /*
             * Wait before the next automatic gossip round.
             */
            await sleep(GOSSIP_DELAY);
        }

    } catch (error) {

        console.error("Automatic gossip error:", error);

        log(`❌ Gossip failed: ${error.message}`);

    } finally {

        gossipRunning = false;

        if (button) {
            button.disabled = true;
            button.textContent = "✅ Gossip Complete";
        }

        await refresh();
    }
}


function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function flushBridges() {

    const uploadButton =
        document.getElementById("uploadButton");

    if (uploadButton) {
        uploadButton.disabled = true;
        uploadButton.textContent = "⏳ Uploading...";
    }

    log("📡 Uploading bridge packet to backend...");

    try {

        const response = await fetch(
            '/api/mesh/flush',
            {
                method: 'POST'
            }
        );

        if (!response.ok) {
            throw new Error(
                `Backend upload failed: ${response.status}`
            );
        }

        const result = await response.json();

        log(
            `📡 ${result.uploadsAttempted} bridge upload(s)`
        );

        let settlementCompleted = false;

        result.results.forEach(res => {

            log(
                `   ${res.bridgeNode} packet ${res.packetId} → ${res.outcome}` +
                (
                    res.reason
                        ? ` (${res.reason})`
                        : ''
                )
            );

            if (res.outcome === "SETTLED") {
                settlementCompleted = true;
            }

            if (res.outcome === "DUPLICATE_DROPPED") {
                settlementCompleted = true;
            }

            if (res.outcome === "INVALID") {
                settlementCompleted = true;
            }

            if (res.outcome === "RETRY_REQUIRED") {

                log(
                    "⚠ Another transaction modified this account."
                );

                log(
                    "🔁 Please retry this payment."
                );
            }
        });

        /*
         * Move the visual packet from Bridge
         * toward Internet only after upload.
         */
        
            if (settlementCompleted) {

                log(
                    "🧹 Transaction completed. Mesh packet cleared."
                );

                // Remove the visual packet after settlement.
                removeLivePacket();

                await sleep(300);

                await refresh();

            /*
             * Reset the gossip button for the next
             * transaction.
             */
            const gossipButton =
                document.getElementById("gossipButton");

            if (gossipButton) {

                gossipButton.disabled = true;

                gossipButton.textContent =
                    "🚀 Start Gossip";
            }

            /*
             * Upload is disabled again until the
             * next packet reaches the bridge.
             */
            if (uploadButton) {

                uploadButton.disabled = true;

                uploadButton.textContent =
                    "📡 Upload to Backend";
            }

            log(
                "✅ Ready for the next transaction."
            );
        }

    } catch (error) {

        console.error(error);

        log(
            `❌ Upload failed: ${error.message}`
        );

        if (uploadButton) {

            uploadButton.disabled = false;

            uploadButton.textContent =
                "📡 Upload to Backend";
        }
    }

    await refresh();
}

async function resetMesh() {
    await fetch('/api/demo/reset', {method: 'POST'});
    log('🗑 mesh + idempotency cache cleared');
    refresh();
}

refresh();
//setTimeout(drawConnections,300);
//window.addEventListener("resize", drawConnections);
setInterval(refresh,3000);