// nDashMo's About content and status checks, shared by lite.html and glass.html (Controls > About card and the
// About page). Each page brings its own CSS for the same markup. Version only, no change list (user, 2026-10-29); the
// app's own version (Bridge.version) wins over the one here.
window.APPINFO = {
  name: 'nDashMo', tagline: 'Neue Dashboard in Motion', version: '1.0',
  sampleAssistOn: false,   // browser prototype: accessibility shown off, like right after an install
  blurb: 'Map, music, clock and car in one dashboard for an Android tablet as your car head unit.',
  creator: { handle: '@faizald.jo', url: 'https://www.threads.com/@faizald.jo' },
  credits: 'Map data OpenStreetMap contributors, OpenMapTiles, OpenFreeMap. MapLibre GL. Weather Open-Meteo, icons Weatherglass. ' +
    'Icons Phosphor. Speed limits Overpass API.',
};

// Drive log (DriveLog.java via Bridge.log): the pages' own events, one line each. Nothing in a browser.
window.dlog = m => { try { if (window.Android && Android.log) Android.log(m); } catch (e) { } };
addEventListener('error', e => dlog(`ERROR ${(e.filename || '').split('/').pop()}:${e.lineno}: ${e.message}`));
addEventListener('unhandledrejection', e => dlog(`ERROR (promise): ${e.reason && e.reason.message || e.reason}`));

(() => {
  const A = window.Android, I = window.APPINFO;
  const call = (f, dflt) => { try { return A && A[f] ? A[f]() : dflt; } catch (e) { return dflt; } };
  // What nDashMo needs from Android, why, and where to turn it on. Browser prototype: all on.
  const checks = () => [
    { k: 'assist', name: 'Accessibility', why: 'Unplug sleep, split screen, recent apps', on: call('assistOn', I.sampleAssistOn), open: () => A.openAssistSettings() },
    { k: 'media', name: 'Notification access', why: 'Song, controls and turn info', on: call('mediaAccess', true), open: () => A.openPanel('media') },
    { k: 'location', name: 'Location', why: 'Speed, map and weather', on: call('locationOn', true), open: () => A.openPanel('location') },
    { k: 'home', name: 'Default home app', why: 'The Home button opens nDashMo', on: call('isHome', true), open: () => A.openPanel('home') },
  ];
  const esc = s => String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
  const ver = () => `Version ${call('version', '') || I.version}`;
  const row = (c, why) => `<button class="chk-row${c.on ? '' : ' off press'}" data-chk="${c.k}"${c.on ? ' tabindex="-1"' : ''}><i class="ph-fill ph-${c.on ? 'check-circle' : 'warning-circle'}"></i>` +
    `<span>${esc(c.name)}${why ? `<em>${esc(c.why)}</em>` : ''}</span><small>${c.on ? 'On' : 'Turn on'}</small></button>`;
  // The creator's Threads link; in the app a tapped outside link opens the Threads app or the browser (MainActivity).
  const creator = () => `<a class="creator press" href="${I.creator.url}" target="_blank" rel="noopener"><i class="ph-fill ph-threads-logo"></i>` +
    `<span>Made by <b>${esc(I.creator.handle)}</b></span></a>`;
  const set = (id, prop, v) => { const el = document.getElementById(id); if (el) el[prop] = v; };
  // The About page's right column shows either what nDashMo needs or the drive log (Drive log button).
  let showLog = false;
  const SAMPLE = '10-29 07:58:02  PLUGGED IN (car on): screen was off, waking\n10-29 07:58:05  resume: play sent\n' +
    '10-29 08:04:40  page: driving (32 km/h)\n10-29 08:09:41  page: screensaver: idle 5 min, 41 km/h\n' +
    '10-29 08:31:12  UNPLUGGED (car off): music playing, pause sent, accessibility OFF so no sleep (screen times out)';
  const logHtml = () => {
    const text = A && A.driveLog ? A.driveLog(300) : SAMPLE;
    const lines = text ? text.split('\n').reverse() : [];
    if (!lines.length) return '<div>Nothing yet. Events show here as they happen: power in and out, the screensaver, driving.</div>';
    return lines.map(l => { const t = l.slice(0, 14), m = l.slice(16);
      return `<div${/OFF|FAILED|ERROR|couldn't/.test(m) ? ' class="hot"' : ''}><b>${esc(t)}</b>${esc(m)}</div>`; }).join('');
  };

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
      set('abFix', 'hidden', showLog || all.every(c => c.on));
      set('abChk', 'hidden', showLog);
      set('abLog', 'hidden', !showLog);
      set('abNeed', 'textContent', showLog ? 'Drive log, newest first' : `What ${I.name} needs`);
      if (showLog) set('abLog', 'innerHTML', logHtml());
      const b = document.getElementById('abLogBtn');
      if (b) b.innerHTML = `<i class="ph-fill ph-${showLog ? 'check-circle' : 'clock-counter-clockwise'}"></i><span>${showLog ? 'What it needs' : 'Drive log'}</span>`;
      set('abCred', 'textContent', I.credits);
    },
    reset() { showLog = false; },
    // Taps inside the card or the page: a check row (or Open settings) opens its setting in Android.
    click(e) {
      const c = e.target.closest('[data-chk]');
      // Only a check that is off opens Android's settings: opening it for one already on led to switching it off there
      // (2026-10-30: accessibility went off twice, ~10 s after its settings screen opened from an "On" row).
      if (c) { const k = checks().find(x => x.k === c.dataset.chk); if (k && !k.on && A) k.open(); return true; }
      if (e.target.closest('#abFix')) { const k = checks().find(x => !x.on); if (k && A) k.open(); return true; }
      if (e.target.closest('#abLogBtn')) { showLog = !showLog; AppInfo.renderPage(); return true; }
      return false;
    },
  };
})();
