/* Las Flores PWA. Cambia VERSION al publicar cambios de recursos estáticos. */
const VERSION = '2.5.1.migration';
const CACHE = 'las-flores-static-' + VERSION;
const APP_SHELL = ['./', './index.html', './manifest.webmanifest', './migration/portable.js', './icons/icon-192.png', './icons/icon-512.png'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(APP_SHELL)));
  // No activar una actualización sobre una sesión abierta: esperar confirmación.
});
self.addEventListener('message', event => {
  if(event.data?.type === 'SKIP_WAITING') self.skipWaiting();
});
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('las-flores-static-') && key !== CACHE).map(key => caches.delete(key))))
    .then(() => self.clients.claim())
  );
});
self.addEventListener('fetch', event => {
  const request = event.request;
  if(request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;
  // Mantener la versión activa hasta que el usuario autorice actualizar; también funciona offline.
  if(request.mode === 'navigate') {
    event.respondWith(caches.match('./index.html').then(cached => cached || fetch(request)));
    return;
  }
  // Solo almacenar ficheros conocidos de la aplicación, nunca registros de personal.
  const path=new URL(request.url).pathname;
  if(!(/\/(?:manifest\.webmanifest|sw\.js|icons\/icon-(?:192|512)\.png)$/.test(path))) return;
  event.respondWith(caches.match(request).then(cached=>cached||fetch(request)));
});

