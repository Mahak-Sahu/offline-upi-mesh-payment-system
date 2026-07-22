
function log(msg) {
    const el = document.getElementById('log');
    el.textContent = '[' + new Date().toLocaleTimeString() + '] ' + msg + '\n' + el.textContent;
}

async function refresh() {
    // Mesh state
    const m = await fetch('/api/mesh/state').then(r => r.json());
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
    refresh();
}

async function gossip() {
    const r = await fetch('/api/mesh/gossip', {method: 'POST'}).then(r => r.json());
    log(`🔄 Gossip: ${r.transfers} transfer(s) — ${JSON.stringify(r.deviceCounts)}`);
    refresh();
}

async function flushBridges() {
    const r = await fetch('/api/mesh/flush', {method: 'POST'}).then(r => r.json());
    log(`📡 ${r.uploadsAttempted} bridge upload(s):`);
    r.results.forEach(res => {
        log(`   ${res.bridgeNode} packet ${res.packetId} → ${res.outcome}` +
            (res.reason ? ` (${res.reason})` : ''));
    });
    refresh();
}

async function resetMesh() {
    await fetch('/api/mesh/reset', {method: 'POST'});
    log('🗑 mesh + idempotency cache cleared');
    refresh();
}

refresh();
setInterval(refresh, 3000);
