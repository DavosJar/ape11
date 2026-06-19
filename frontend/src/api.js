const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8085';

export async function getStatus() {
  const res = await fetch(`${API_BASE}/api/status`);
  if (!res.ok) throw new Error(`Status error: ${res.status}`);
  return res.json();
}

export async function executeRound(ronda, byzantine) {
  const res = await fetch(`${API_BASE}/api/round`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ronda, byzantine }),
  });
  if (!res.ok) throw new Error(`Round error: ${res.status}`);
  return res.json();
}

export async function getResults() {
  const res = await fetch(`${API_BASE}/api/results`);
  if (!res.ok) throw new Error(`Results error: ${res.status}`);
  return res.json();
}
