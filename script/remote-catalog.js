/* Pull the public catalogue and feedback maintained from the admin panel. */
const MR_PHONE_API = 'https://mr-phone-api.onrender.com';
(async () => {
  try {
    const products = await fetch(MR_PHONE_API + '/api/products').then(r => r.ok ? r.json() : []);
    const reviews = await fetch(MR_PHONE_API + '/api/feedback').then(r => r.ok ? r.json() : []);
    if (Array.isArray(products) && products.length) localStorage.mrProducts = JSON.stringify(products);
    if (Array.isArray(reviews) && reviews.length) localStorage.mrReviews = JSON.stringify(reviews);
    if ((products?.length || reviews?.length) && !sessionStorage.mrRemoteRefresh) { sessionStorage.mrRemoteRefresh = '1'; location.reload(); }
  } catch (_) {}
})();
