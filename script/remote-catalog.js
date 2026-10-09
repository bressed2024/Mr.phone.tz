const REMOTE_API = 'https://mr-phone-api.onrender.com';
const remoteImage = (value) => { const image = String(value || ''); return /^(https?:|data:image\/)/i.test(image) ? image : `../images/${encodeURIComponent(image)}`; };
async function syncRemoteCatalog() {
  try {
    const response = await fetch(`${REMOTE_API}/api/products`);
    if (!response.ok) return;
    const remote = await response.json();
    if (!Array.isArray(remote) || !remote.length) return;
    localStorage.setItem('mrProducts', JSON.stringify(remote));
    const current = JSON.stringify(window.__mrPhoneLastRemote || []);
    if (current !== JSON.stringify(remote)) { window.__mrPhoneLastRemote = remote; location.reload(); return; }
  } catch (_) { return; }
}
function repairRemoteImages() {
  const products = JSON.parse(localStorage.getItem('mrProducts') || '[]');
  document.querySelectorAll('#products article').forEach((card) => {
    const name = card.querySelector('h3')?.textContent;
    const product = products.find((item) => item.name === name);
    const image = card.querySelector('img');
    if (product && image) image.src = remoteImage(product.image);
  });
}
syncRemoteCatalog().finally(() => setTimeout(repairRemoteImages, 50));
