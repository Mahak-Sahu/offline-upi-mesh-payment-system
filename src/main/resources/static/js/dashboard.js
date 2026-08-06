
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

    const hops = window.meshState.routes[packetId];

    if (!hops || hops.length === 0) {
        return;
    }

    for (const hop of hops) {

        await animateHop(
            hop.fromNode,
            hop.toNode
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
    movePacket("alice-node","stranger1-node");
    refresh();
}

async function gossip() {
    const r = await fetch('/api/mesh/gossip', {method: 'POST'}).then(r => r.json());
    log(`🔄 Gossip: ${r.transfers} transfer(s) — ${JSON.stringify(r.deviceCounts)}`);
    movePacket("stranger1-node","stranger2-node");

setTimeout(()=>{

   movePacket("stranger2-node","bridge-node");

},900);
    refresh();
}

async function flushBridges() {
    const r = await fetch('/api/mesh/flush', {method: 'POST'}).then(r => r.json());
    log(`📡 ${r.uploadsAttempted} bridge upload(s):`);
    r.results.forEach(res => {
        if(res.outcome==="RETRY_REQUIRED"){

    log("⚠ Another transaction modified this account.");

    log("🔁 Please retry this payment.");

}
        log(`   ${res.bridgeNode} packet ${res.packetId} → ${res.outcome}` +
            (res.reason ? ` (${res.reason})` : ''));
    });
    movePacket("bridge-node","internet-node");
    refresh();
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