const ADMIN_API_URL = 'https://mr-phone-api.onrender.com';
(() => {
  const main = document.querySelector('.admin-main');
  if (!main) return;
  const gate = document.createElement('section');
  gate.id = 'loginGate'; gate.className = 'login-gate';
  gate.innerHTML = '<h2>Admin login</h2><p>Ingiza password ya admin kuendelea.</p><input id="adminPassword" type="password" autocomplete="current-password" placeholder="Admin password"><button id="loginButton" class="btn lime">Login ↗</button><p id="loginError" class="login-error" role="alert"></p>';
  main.prepend(gate);
  const protectedNodes = [...main.children].filter((node) => node !== gate);
  const setVisible = (visible) => protectedNodes.forEach((node) => { node.hidden = !visible; });
  setVisible(false);
  async function login() {
    const password = document.querySelector('#adminPassword').value;
    const error = document.querySelector('#loginError'); error.textContent = '';
    if (!password) { error.textContent = 'Andika password kwanza.'; return; }
    const button = document.querySelector('#loginButton'); button.disabled = true; button.textContent = 'Inaingia...';
    try {
      const response = await fetch(`${ADMIN_API_URL}/api/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ password }) });
      if (!response.ok) throw new Error('login');
      const data = await response.json(); if (!data.token) throw new Error('token');
      sessionStorage.setItem('mrPhoneAdminToken', data.token); unlock();
    } catch (_) { error.textContent = 'Login imeshindikana. Hakikisha password na backend ni sahihi.'; }
    finally { button.disabled = false; button.textContent = 'Login ↗'; }
  }
  function unlock() { gate.remove(); setVisible(true); window.dispatchEvent(new CustomEvent('mr-phone-admin-ready')); }
  document.querySelector('#loginButton').addEventListener('click', login);
  document.querySelector('#adminPassword').addEventListener('keydown', (event) => { if (event.key === 'Enter') login(); });
  if (sessionStorage.getItem('mrPhoneAdminToken')) unlock();
})();
