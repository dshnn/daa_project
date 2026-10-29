const API_BASE = 'http://localhost:8000/api';

let cy = null;
let simCy = null;
let currentNetworkId = null;
let networksCache = [];

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    initNavigation();
    initNetworkLab();
    initAlgorithmLab();
    initAttackSimulator();
    initHistory();
    loadDashboard();
});

// Navigation
function initNavigation() {
    const navBtns = document.querySelectorAll('.nav-btn');
    navBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const page = btn.getAttribute('data-page');
            navigateTo(page);
        });
    });
}

function navigateTo(pageName) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.querySelectorAll('.nav-btn').forEach(b => b.classList.remove('active'));
    
    document.getElementById(`page-${pageName}`).classList.add('active');
    document.querySelector(`.nav-btn[data-page="${pageName}"]`).classList.add('active');
    
    if (pageName === 'dashboard') loadDashboard();
    if (pageName === 'network-lab') loadSavedNetworks();
    if (pageName === 'algorithm-lab') loadAlgoNetworks();
    if (pageName === 'attack-simulator') loadSimNetworks();
    if (pageName === 'simulation-history') loadHistory();
}

// Dashboard
async function loadDashboard() {
    try {
        const [netsRes, simsRes] = await Promise.all([
            fetch(`${API_BASE}/networks`),
            fetch(`${API_BASE}/simulations`)
        ]);
        const nets = await netsRes.json();
        const sims = await simsRes.json();
        
        document.getElementById('stat-networks').textContent = nets.length;
        let totalDevices = 0;
        for (const n of nets) {
            try {
                const r = await fetch(`${API_BASE}/networks/${n.id}`);
                const data = await r.json();
                totalDevices += data.devices.length;
            } catch(e) {}
        }
        document.getElementById('stat-devices').textContent = totalDevices;
        document.getElementById('stat-sims').textContent = sims.length;
        if (sims.length > 0) {
            document.getElementById('stat-latest').textContent = `#${sims[0].id} (${sims[0].num_compromised} comp.)`;
        } else {
            document.getElementById('stat-latest').textContent = '-';
        }
    } catch(e) {
        console.error(e);
    }
}

// Network Lab
function initNetworkLab() {
    cy = cytoscape({
        container: document.getElementById('cy'),
        style: [
            {
                selector: 'node',
                style: {
                    'background-color': '#ffffff',
                    'border-color': '#000000',
                    'border-width': 2,
                    'label': 'data(label)',
                    'color': '#000000',
                    'text-valign': 'center',
                    'text-halign': 'center',
                    'font-size': '10px'
                }
            },
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': '#000000',
                    'target-arrow-color': '#000000',
                    'target-arrow-shape': 'triangle',
                    'curve-style': 'bezier',
                    'label': 'data(weight)',
                    'font-size': '8px',
                    'text-rotation': 'autorotate'
                }
            },
            {
                selector: '.compromised',
                style: { 'background-color': '#d97757', 'border-color': '#8b4513' }
            },
            {
                selector: '.safe',
                style: { 'background-color': '#90ee90', 'border-color': '#006400' }
            },
            {
                selector: '.exposed',
                style: { 'background-color': '#ffa500', 'border-color': '#b8860b' }
            },
            {
                selector: '.isolated',
                style: { 'background-color': '#808080', 'border-color': '#404040' }
            }
        ],
        layout: { name: 'grid' }
    });
    
    document.getElementById('btn-add-node').addEventListener('click', () => {
        const label = document.getElementById('device-label').value || `Device${cy.nodes().length + 1}`;
        const id = 'n' + Date.now();
        cy.add({ group: 'nodes', data: { id, label } });
        updateNetworkInfo();
    });
    
    document.getElementById('btn-add-edge').addEventListener('click', () => {
        const selected = cy.$(':selected');
        if (selected.length !== 2) {
            alert('Please select exactly 2 nodes to connect');
            return;
        }
        const weight = parseFloat(document.getElementById('edge-weight').value) || 1;
        const id = 'e' + Date.now();
        cy.add({ group: 'edges', data: { id, source: selected[0].id(), target: selected[1].id(), weight } });
        updateNetworkInfo();
    });
    
    document.getElementById('btn-delete').addEventListener('click', () => {
        cy.$(':selected').remove();
        updateNetworkInfo();
    });
    
    document.getElementById('btn-save-network').addEventListener('click', saveNetwork);
}

function updateNetworkInfo() {
    document.getElementById('info-devices').textContent = cy.nodes().length;
    document.getElementById('info-conns').textContent = cy.edges().length;
}

async function saveNetwork() {
    const name = document.getElementById('network-name').value || 'Untitled Network';
    try {
        const res = await fetch(`${API_BASE}/networks`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ name })
        });
        const net = await res.json();
        currentNetworkId = net.id;
        
        const devices = cy.nodes().map(n => ({ id: n.id(), label: n.data('label'), x: n.position('x'), y: n.position('y') }));
        const connections = cy.edges().map(e => ({ source: e.data('source'), target: e.data('target'), weight: e.data('weight') }));
        
        await fetch(`${API_BASE}/networks/${net.id}/graph`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ devices, connections })
        });
        alert('Network saved!');
        loadSavedNetworks();
        loadDashboard();
    } catch(e) {
        alert('Error saving network');
        console.error(e);
    }
}

async function loadSavedNetworks() {
    try {
        const res = await fetch(`${API_BASE}/networks`);
        networksCache = await res.json();
        const div = document.getElementById('saved-networks');
        div.innerHTML = '';
        for (const n of networksCache) {
            const item = document.createElement('div');
            item.className = 'list-item';
            item.innerHTML = `<span>${n.name} (ID: ${n.id})</span><button class="btn" style="padding:2px 8px;" onclick="loadNetwork(${n.id})">Load</button>`;
            div.appendChild(item);
        }
    } catch(e) {
        console.error(e);
    }
}

async function loadNetwork(id) {
    try {
        const res = await fetch(`${API_BASE}/networks/${id}`);
        const data = await res.json();
        currentNetworkId = id;
        cy.elements().remove();
        for (const d of data.devices) {
            cy.add({ group: 'nodes', data: { id: String(d.id), label: d.label }, position: { x: d.x || Math.random()*300, y: d.y || Math.random()*300 } });
        }
        for (const c of data.connections) {
            cy.add({ group: 'edges', data: { id: 'e'+c.id, source: String(c.source_id), target: String(c.target_id), weight: c.weight } });
        }
        updateNetworkInfo();
        document.getElementById('network-name').value = data.network.name;
        alert('Network loaded');
    } catch(e) {
        alert('Error loading network');
        console.error(e);
    }
}

// Algorithm Lab
async function loadAlgoNetworks() {
    try {
        const res = await fetch(`${API_BASE}/networks`);
        const nets = await res.json();
        const sel = document.getElementById('algo-network');
        sel.innerHTML = '';
        for (const n of nets) {
            const opt = document.createElement('option');
            opt.value = n.id;
            opt.textContent = n.name;
            sel.appendChild(opt);
        }
        sel.onchange = loadAlgoDevices;
        if (nets.length > 0) loadAlgoDevices();
    } catch(e) {}
}

async function loadAlgoDevices() {
    const nid = document.getElementById('algo-network').value;
    if (!nid) return;
    try {
        const res = await fetch(`${API_BASE}/networks/${nid}`);
        const data = await res.json();
        const devs = data.devices;
        ['bfs-start', 'dfs-start', 'dij-start', 'dij-end'].forEach(id => {
            const sel = document.getElementById(id);
            sel.innerHTML = '';
            for (const d of devs) {
                const opt = document.createElement('option');
                opt.value = d.id;
                opt.textContent = d.label;
                sel.appendChild(opt);
            }
        });
    } catch(e) {}
}

document.getElementById('btn-bfs').addEventListener('click', async () => {
    const nid = document.getElementById('algo-network').value;
    const start = document.getElementById('bfs-start').value;
    try {
        const res = await fetch(`${API_BASE}/algorithms/bfs`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ network_id: parseInt(nid), start: parseInt(start) })
        });
        const data = await res.json();
        document.getElementById('res-bfs').textContent = JSON.stringify(data, null, 2);
    } catch(e) {
        document.getElementById('res-bfs').textContent = 'Error: ' + e;
    }
});

document.getElementById('btn-dfs').addEventListener('click', async () => {
    const nid = document.getElementById('algo-network').value;
    const start = document.getElementById('dfs-start').value;
    try {
        const res = await fetch(`${API_BASE}/algorithms/dfs`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ network_id: parseInt(nid), start: parseInt(start) })
        });
        const data = await res.json();
        document.getElementById('res-dfs').textContent = JSON.stringify(data, null, 2);
    } catch(e) {
        document.getElementById('res-dfs').textContent = 'Error: ' + e;
    }
});

document.getElementById('btn-dij').addEventListener('click', async () => {
    const nid = document.getElementById('algo-network').value;
    const start = document.getElementById('dij-start').value;
    const end = document.getElementById('dij-end').value;
    try {
        const res = await fetch(`${API_BASE}/algorithms/dijkstra`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ network_id: parseInt(nid), start: parseInt(start), end: parseInt(end) })
        });
        const data = await res.json();
        document.getElementById('res-dij').textContent = JSON.stringify(data, null, 2);
    } catch(e) {
        document.getElementById('res-dij').textContent = 'Error: ' + e;
    }
});

document.getElementById('btn-prim').addEventListener('click', async () => {
    const nid = document.getElementById('algo-network').value;
    try {
        const res = await fetch(`${API_BASE}/algorithms/prim`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({ network_id: parseInt(nid) })
        });
        const data = await res.json();
        document.getElementById('res-prim').textContent = JSON.stringify(data, null, 2);
    } catch(e) {
        document.getElementById('res-prim').textContent = 'Error: ' + e;
    }
});

// Attack Simulator
function initAttackSimulator() {
    simCy = cytoscape({
        container: document.getElementById('sim-cy'),
        style: [
            {
                selector: 'node',
                style: {
                    'background-color': '#ffffff',
                    'border-color': '#000000',
                    'border-width': 2,
                    'label': 'data(label)',
                    'color': '#000000',
                    'text-valign': 'center',
                    'text-halign': 'center',
                    'font-size': '10px'
                }
            },
            {
                selector: 'edge',
                style: {
                    'width': 2,
                    'line-color': '#000000',
                    'target-arrow-shape': 'none',
                    'curve-style': 'bezier'
                }
            },
            {
                selector: '.compromised',
                style: { 'background-color': '#d97757', 'border-color': '#8b4513' }
            },
            {
                selector: '.safe',
                style: { 'background-color': '#ffffff', 'border-color': '#000000' }
            },
            {
                selector: '.exposed',
                style: { 'background-color': '#ffa500', 'border-color': '#b8860b' }
            },
            {
                selector: '.isolated',
                style: { 'background-color': '#808080', 'border-color': '#404040' }
            },
            {
                selector: '.recovered',
                style: { 'background-color': '#90ee90', 'border-color': '#006400' }
            }
        ],
        layout: { name: 'grid' }
    });
    
    document.getElementById('sim-strategy').addEventListener('change', (e) => {
        document.getElementById('manual-group').style.display = e.target.value === 'manual containment' ? 'block' : 'none';
    });
    
    document.getElementById('btn-run-sim').addEventListener('click', runSimulation);
    document.getElementById('btn-reset-sim').addEventListener('click', resetSimulation);
}

async function loadSimNetworks() {
    try {
        const res = await fetch(`${API_BASE}/networks`);
        const nets = await res.json();
        const sel = document.getElementById('sim-network');
        sel.innerHTML = '';
        for (const n of nets) {
            const opt = document.createElement('option');
            opt.value = n.id;
            opt.textContent = n.name;
            sel.appendChild(opt);
        }
        sel.onchange = loadSimDevices;
        if (nets.length > 0) loadSimDevices();
    } catch(e) {}
}

async function loadSimDevices() {
    const nid = document.getElementById('sim-network').value;
    if (!nid) return;
    try {
        const res = await fetch(`${API_BASE}/networks/${nid}`);
        const data = await res.json();
        const devs = data.devices;
        const sel = document.getElementById('sim-initial');
        sel.innerHTML = '';
        for (const d of devs) {
            const opt = document.createElement('option');
            opt.value = d.id;
            opt.textContent = d.label;
            sel.appendChild(opt);
        }
        simCy.elements().remove();
        for (const d of devs) {
            simCy.add({ group: 'nodes', data: { id: String(d.id), label: d.label }, position: { x: d.x || Math.random()*300, y: d.y || Math.random()*300 } });
        }
        for (const c of data.connections) {
            simCy.add({ group: 'edges', data: { source: String(c.source_id), target: String(c.target_id) } });
        }
    } catch(e) {}
}

async function runSimulation() {
    const nid = document.getElementById('sim-network').value;
    const initial = document.getElementById('sim-initial').value;
    const prob = parseFloat(document.getElementById('sim-prob').value) || 0.5;
    const seed = document.getElementById('sim-seed').value ? parseInt(document.getElementById('sim-seed').value) : null;
    const strategy = document.getElementById('sim-strategy').value;
    const manual = document.getElementById('manual-isolated').value.split(',').map(x => parseInt(x.trim())).filter(x => !isNaN(x));
    
    try {
        const res = await fetch(`${API_BASE}/simulations`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                network_id: parseInt(nid),
                initial_compromised_device_id: parseInt(initial),
                propagation_probability: prob,
                seed: seed,
                strategy: strategy,
                manual_isolated: manual
            })
        });
        const data = await res.json();
        document.getElementById('sim-comp').textContent = data.num_compromised;
        document.getElementById('sim-iso').textContent = data.num_isolated;
        document.getElementById('sim-rounds').textContent = data.rounds;
        document.getElementById('sim-time').textContent = data.execution_time_ms;
        
        // Update visualization
        simCy.nodes().removeClass('compromised safe exposed isolated recovered');
        for (const [nid, state] of Object.entries(data.states)) {
            simCy.getElementById(String(nid)).addClass(state.toLowerCase());
        }
        
        // Show events
        const evDiv = document.getElementById('sim-events');
        evDiv.innerHTML = '';
        for (const ev of data.events.slice(-20)) {
            const div = document.createElement('div');
            div.className = 'event-item';
            div.textContent = `R${ev.round_num}: Device ${ev.device_id} -> ${ev.new_state} (${ev.reason})`;
            evDiv.appendChild(div);
        }
        loadDashboard();
        loadHistory();
    } catch(e) {
        alert('Error running simulation');
        console.error(e);
    }
}

function resetSimulation() {
    simCy.nodes().removeClass('compromised safe exposed isolated recovered');
    simCy.nodes().addClass('safe');
    document.getElementById('sim-comp').textContent = '0';
    document.getElementById('sim-iso').textContent = '0';
    document.getElementById('sim-rounds').textContent = '0';
    document.getElementById('sim-time').textContent = '0';
    document.getElementById('sim-events').innerHTML = '';
}

// History
async function initHistory() {
    // nothing
}

async function loadHistory() {
    try {
        const res = await fetch(`${API_BASE}/simulations`);
        const sims = await res.json();
        const div = document.getElementById('history-list');
        div.innerHTML = '';
        for (const s of sims) {
            const item = document.createElement('div');
            item.className = 'list-item';
            item.innerHTML = `<span>#${s.id} - Net${s.network_id} - Comp:${s.num_compromised} Iso:${s.num_isolated} R:${s.rounds}</span><button class="btn" style="padding:2px 8px;" onclick="viewHistory(${s.id})">View</button>`;
            div.appendChild(item);
        }
    } catch(e) {}
}

async function viewHistory(id) {
    try {
        const res = await fetch(`${API_BASE}/simulations/${id}`);
        const data = await res.json();
        const s = data.simulation;
        let html = `<p><strong>Simulation #${s.id}</strong></p>`;
        html += `<p>Network: ${s.network_id} | Initial: ${s.initial_compromised_device_id} | Prob: ${s.propagation_probability} | Seed: ${s.seed} | Strategy: ${s.strategy}</p>`;
        html += `<p>Rounds: ${s.rounds} | Compromised: ${s.num_compromised} | Isolated: ${s.num_isolated} | Time: ${s.execution_time_ms}ms</p>`;
        html += `<h4>Events</h4><div class="events">`;
        for (const ev of data.events) {
            html += `<div class="event-item">R${ev.round_num}: Device ${ev.device_id} -> ${ev.new_state} (${ev.reason||''})</div>`;
        }
        html += `</div>`;
        document.getElementById('history-details').innerHTML = html;
    } catch(e) {
        document.getElementById('history-details').innerHTML = 'Error loading details';
    }
}
