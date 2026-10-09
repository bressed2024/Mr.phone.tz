const API_URL = 'https://mr-phone-api.onrender.com';
const token = () => sessionStorage.getItem('mrPhoneAdminToken') || '';
const esc = (value) => String(value ?? '').replace(/[&<>'"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#039;', '"': '&quot;' }[c]));
const money = (value) => `TZS ${Number(value || 0).toLocaleString('en-TZ')}`;
let products = [], reviews = [];
async function api(path, options = {}) {
  const headers = { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(token() ? { Authorization: `Bearer ${token()}` } : {}) };
  const response = await fetch(`${API_URL}${path}`, { ...options, headers });
  if (!response.ok) throw new Error(`API ${response.status}`);
  return response.status === 204 ? null : response.json();
}
function imageSource(image) { const value = String(image || ''); return /^(https?:|data:image\/)/i.test(value) ? value : `../images/${encodeURIComponent(value)}`; }
function paint() {
  const list = document.querySelector('#adminList'), feedbackList = document.querySelector('#feedbackList');
  if (list) list.innerHTML = products.map((p) => `<div class="admin-item"><img src="${imageSource(p.image)}" alt="" loading="lazy"><div><b>${esc(p.name)}</b><small>${money(p.price)} · ${esc(p.brand)}</small></div><button class="delete" data-product="${esc(p.id)}">Delete</button></div>`).join('') || '<p class="muted">Hakuna bidhaa kwenye backend.</p>';
  if (feedbackList) feedbackList.innerHTML = reviews.map((r) => `<div class="admin-item"><div><b>${esc(r.name)}</b><small>${esc(r.city)} · ${esc(r.text)}</small></div></div>`).join('') || '<p class="muted">Hakuna feedback bado.</p>';
  const count = document.querySelector('#feedbackCount'); if (count) count.textContent = reviews.length;
  document.querySelectorAll('[data-product]').forEach((button) => button.onclick = async () => { if (!confirm('Futa bidhaa hii kwenye vifaa vyote?')) return; try { await api(`/api/products?id=${encodeURIComponent(button.dataset.product)}`, { method: 'DELETE' }); await loadRemoteAdmin(); alert('Bidhaa imefutwa.'); } catch (_) { alert('Imeshindikana kufuta bidhaa.'); } });
}
async function loadRemoteAdmin() {
  try { const [remoteProducts, remoteReviews] = await Promise.all([api('/api/products'), api('/api/feedback')]); products = Array.isArray(remoteProducts) ? remoteProducts : []; reviews = Array.isArray(remoteReviews) ? remoteReviews : []; localStorage.setItem('mrProducts', JSON.stringify(products)); localStorage.setItem('mrReviews', JSON.stringify(reviews)); paint(); } catch (_) { paint(); alert('Backend haipatikani kwa sasa.'); }
}
window.loadRemoteAdmin = loadRemoteAdmin;
function fileToDataUrl(file) { return new Promise((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result); reader.onerror = reject; reader.readAsDataURL(file); }); }
function setupImagePicker() {
  const field = document.querySelector('input[name="image"]'); if (!field || field.dataset.ready) return; field.dataset.ready = '1'; field.placeholder = 'Image URL au chagua picha hapa chini';
  const picker = document.createElement('input'); picker.type = 'file'; picker.accept = 'image/jpeg,image/png,image/webp'; picker.className = 'image-picker';
  const drop = document.createElement('div'); drop.className = 'dropzone'; drop.textContent = '📷 Chagua picha / drag & drop (JPG, PNG, WEBP hadi 5MB)';
  field.parentNode.insertBefore(drop, field.nextSibling); field.parentNode.insertBefore(picker, drop.nextSibling);
  const read = async (file) => { if (!file || !['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) { alert('Tumia JPG, PNG au WEBP chini ya 5MB.'); return; } field.value = await fileToDataUrl(file); drop.textContent = `✓ ${file.name}`; };
  drop.onclick = () => picker.click(); picker.onchange = () => read(picker.files[0]); drop.ondragover = (event) => { event.preventDefault(); drop.classList.add('dragover'); }; drop.ondragleave = () => drop.classList.remove('dragover'); drop.ondrop = (event) => { event.preventDefault(); drop.classList.remove('dragover'); read(event.dataTransfer.files[0]); };
}
function setup() {
  setupImagePicker();
  document.querySelector('#productForm').onsubmit = async (event) => { event.preventDefault(); const form = new FormData(event.target), image = String(form.get('image') || '').trim(); if (!image) { alert('Chagua picha au weka image URL.'); return; } try { await api('/api/products', { method: 'POST', body: JSON.stringify({ name: form.get('name'), brand: form.get('brand'), price: Number(form.get('price')), image, meta: 'Available now', tag: 'NEW' }) }); event.target.reset(); document.querySelector('.dropzone').textContent = '📷 Chagua picha / drag & drop (JPG, PNG, WEBP hadi 5MB)'; await loadRemoteAdmin(); alert('Bidhaa imeongezwa kwa vifaa vyote.'); } catch (_) { alert('Imeshindikana ku-save bidhaa.'); } };
  document.querySelectorAll('[data-tab]').forEach((button) => button.onclick = () => { document.querySelectorAll('[data-tab]').forEach((item) => item.classList.remove('active')); button.classList.add('active'); document.querySelector('#productsTab').classList.toggle('hidden', button.dataset.tab !== 'products'); document.querySelector('#feedbackTab').classList.toggle('hidden', button.dataset.tab !== 'feedback'); });
  loadRemoteAdmin();
}
window.addEventListener('mr-phone-admin-ready', setup); if (sessionStorage.getItem('mrPhoneAdminToken')) setup();
