// nDashMo's About content and status checks, shared by lite.html and glass.html (Controls > About card and the
// About page). Each page brings its own CSS for the same markup. Version only, no change list (user, 2026-10-29); the
// app's own version (Bridge.version) wins over the one here.
window.APPINFO = {
  name: 'nDashMo', tagline: 'Dashboard meets Motion.', version: '1.0',
  blurb: 'Map, music, clock and car in one dashboard for the Galaxy Tab A7 Lite.',
  creator: { handle: '@faizald.jo', url: 'https://www.threads.com/@faizald.jo' },
  credits: 'Map data OpenStreetMap contributors, OpenMapTiles, OpenFreeMap. MapLibre GL. Weather Open-Meteo, icons Weatherglass. ' +
    'Icons Phosphor. Speed limits Overpass API.',
};

(() => {
  const A = window.Android, I = window.APPINFO;
  const call = (f, dflt) => { try { return A && A[f] ? A[f]() : dflt; } catch (e) { return dflt; } };
  // What nDashMo needs from Android, why, and where to turn it on. Browser prototype: all on.
  const checks = () => [
    { k: 'assist', name: 'Accessibility', why: 'Unplug sleep, split screen, recent apps', on: call('assistOn', true), open: () => A.openAssistSettings() },
    { k: 'media', name: 'Notification access', why: 'Song, controls and turn info', on: call('mediaAccess', true), open: () => A.openPanel('media') },
    { k: 'location', name: 'Location', why: 'Speed, map and weather', on: call('locationOn', true), open: () => A.openPanel('location') },
    { k: 'home', name: 'Default home app', why: 'The Home button opens nDashMo', on: call('isHome', true), open: () => A.openPanel('home') },
  ];
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const ver = () => `Version ${call('version', '') || I.version}`;
  const row = (c, why) => `<button class="chk-row${c.on ? '' : ' off'}" data-chk="${c.k}"><i class="ph-fill ph-${c.on ? 'check-circle' : 'warning-circle'}"></i>` +
    `<span>${esc(c.name)}${why ? `<em>${esc(c.why)}</em>` : ''}</span><small>${c.on ? 'On' : 'Turn on'}</small></button>`;
  // The creator's Threads link; in the app a tapped outside link opens the Threads app or the browser (MainActivity).
  const creator = () => `<a class="creator press" href="${I.creator.url}" target="_blank" rel="noopener"><i class="ph-fill ph-threads-logo"></i>` +
    `<span>Made by <b>${esc(I.creator.handle)}</b></span></a>`;
  const set = (id, prop, v) => { const el = document.getElementById(id); if (el) el[prop] = v; };

  window.AppInfo = {
    // The card in Controls: name, tagline, version, creator, and what it needs from Android.
    renderCard() {
      set('aboutTag', 'textContent', I.tagline);
      set('aboutVer', 'textContent', ver());
      set('aboutBy', 'innerHTML', creator());
      set('aboutChk', 'innerHTML', checks().map(c => row(c, false)).join(''));
    },
    // The full page: identity on the left, what it needs (with why) on the right, credits under it.
    renderPage() {
      const all = checks();
      set('abTag', 'textContent', I.tagline);
      set('abVer', 'textContent', ver());
      set('abBlurb', 'textContent', I.blurb);
      set('abBy', 'innerHTML', creator());
      set('abChk', 'innerHTML', all.map(c => row(c, true)).join(''));
      set('abFix', 'hidden', all.every(c => c.on));
      set('abCred', 'textContent', I.credits);
    },
    // Taps inside the card or the page: a check row (or Open settings) opens its setting in Android.
    click(e) {
      const c = e.target.closest('[data-chk]');
      if (c) { const k = checks().find(x => x.k === c.dataset.chk); if (k && A) k.open(); return true; }
      if (e.target.closest('#abFix')) { const k = checks().find(x => !x.on); if (k && A) k.open(); return true; }
      return false;
    },
  };
})();
