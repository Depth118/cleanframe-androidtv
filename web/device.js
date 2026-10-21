/* Android TV adapter. Replaces CleanFrame's Tizen device.js; same window.FrameDevice API.
   Calls go through the AndroidTV bridge injected by MainActivity. */
(function () {
  'use strict';
  // Brand -> candidate Android packages, first installed one wins.
  var PACKAGES = {
    stremio: ['com.stremio.one'],
    netflix: ['com.netflix.ninja', 'com.netflix.mediaclient'],
    disneyplus: ['com.disney.disneyplus'],
    youtube: ['com.google.android.youtube.tv', 'com.google.android.youtube.googletv', 'com.google.android.youtube'],
    appletv: ['com.apple.atve.androidtv.appletv', 'com.apple.atve.amazon.appletv'],
    primevideo: ['com.amazon.amazonvideo.livingroom', 'com.amazon.avod.thirdpartyclient'],
    abciview: ['au.net.abc.iview'],
    sbs: ['com.sbs.ondemand.tv', 'com.sbs.ondemand.android'],
    applemusic: ['com.apple.android.music']
  };
  var EXTRA_BRANDS = {
    'com.spotify.tv.android': 'spotify', 'com.wbd.stream': 'hbomax', 'com.hbo.hbonow': 'hbomax',
    'com.tubitv': 'tubi', 'com.tubitv.ott': 'tubi'
  };
  // Saved preferences from the Tizen build may still hold Tizen IDs.
  var TIZEN = {
    'IkWsFHtOY9.Stremio': 'stremio', 'org.tizen.netflix-app': 'netflix', 'MCmYXNxgcu.DisneyPlus': 'disneyplus',
    '9Ur5IzDKqV.TizenYouTube': 'youtube', 'com.samsung.tv.aria-video': 'appletv', 'org.tizen.primevideo': 'primevideo',
    'mWK87Xghap.ABCiview': 'abciview', 'OyE7OuxlUi.SBSOnDemand': 'sbs', 'org.tizen.apple.apple-music': 'applemusic'
  };
  var ORDER = Object.keys(PACKAGES);
  function bridge() { return window.AndroidTV; }
  function unavailable(done) {
    setTimeout(function () {
      done({ message: 'Open CleanFrame on the TV to use this control.', detail: 'The web preview cannot control the TV.' });
    }, 0);
  }
  // Bridge methods return '' on success or an error message.
  function call(done, run) {
    if (!bridge()) { unavailable(done); return; }
    setTimeout(function () {
      var err;
      try { err = run(bridge()); } catch (e) { err = e && e.message || 'Bridge error'; }
      if (err) done({ message: 'The TV could not open this screen.', detail: err });
      else done(null);
    }, 0);
  }
  function brandFor(pkg) {
    for (var i = 0; i < ORDER.length; i++) if (PACKAGES[ORDER[i]].indexOf(pkg) >= 0) return ORDER[i];
    return '';
  }
  function resolve(id) {
    var brand = TIZEN[id] || (PACKAGES[id] ? id : ''), list, i;
    if (!brand) return id;
    list = PACKAGES[brand];
    for (i = 0; i < list.length; i++) if (bridge().isInstalled(list[i])) return list[i];
    return list[0];
  }
  function launchApp(id, done) {
    call(done, function (b) { return b.launch(resolve(id)); });
  }
  function launch(brand, done) {
    if (!PACKAGES[brand]) { setTimeout(function () { done({ message: 'This app has no launch target.' }); }, 0); return; }
    launchApp(brand, done);
  }
  function art(done) { call(done, function (b) { return b.screensaver(); }); }
  function home(done) { call(done, function (b) { return b.systemHome(); }); }
  function settings(done) { call(done, function (b) { return b.settings(); }); }
  function apps(done) {
    if (!bridge()) { unavailable(done); return; }
    setTimeout(function () {
      var list, seen = {}, result = [];
      try { list = JSON.parse(bridge().apps()); }
      catch (e) { done({ message: 'The TV could not list its apps.', detail: e && e.message || '' }); return; }
      list.forEach(function (a) {
        if (!a.id || !a.name || seen[a.id]) return;
        seen[a.id] = true;
        var brand = brandFor(a.id), rank = brand ? ORDER.indexOf(brand) : (EXTRA_BRANDS[a.id] ? 20 : 30);
        result.push({ appId: a.id, name: a.name, brand: brand || EXTRA_BRANDS[a.id] || '', icon: '/icon/' + a.id + '.png', rank: rank });
      });
      result.sort(function (a, b) { return a.rank - b.rank || a.name.toLowerCase().localeCompare(b.name.toLowerCase()); });
      done(null, result);
    }, 0);
  }
  window.FrameDevice = { launch: launch, launchApp: launchApp, art: art, home: home, settings: settings, apps: apps };
}());
