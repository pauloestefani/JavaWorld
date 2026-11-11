// Ponte entre a tela do Tátão (a mesma do PC) e o JavaWorld no Android.
// A tela chama window.electronAPI; aqui cada chamada vira uma mensagem pro app (TataoNative.post)
// e as respostas voltam por window.__tn(...).
(function () {
  if (!window.TataoNative) return;
  var seq = 0, wait = {}, cbs = {};
  var P = function (v) { return Promise.resolve(v); };
  function post(o) { try { window.TataoNative.post(JSON.stringify(o)); } catch (e) {} }
  function ask(o) { return new Promise(function (res) { var id = ++seq; wait[id] = res; o.id = id; post(o); }); }
  function b64ToU8(s) { var b = atob(s), u = new Uint8Array(b.length); for (var i = 0; i < b.length; i++) u[i] = b.charCodeAt(i); return u; }
  function u8ToB64(u) { var s = ""; for (var i = 0; i < u.length; i += 8192) s += String.fromCharCode.apply(null, u.subarray(i, i + 8192)); return btoa(s); }
  window.__tn = function (m) {
    if (!m) return;
    if (m.ev === "reply") { var f = wait[m.id]; delete wait[m.id]; if (f) f(m.data); }
    else if (m.ev === "world") { if (cbs.world) cbs.world({ mine: true, port: m.port, motd: m.motd }); }
    else if (m.ev === "tunData") { try { if (cbs.tunData) cbs.tunData(m.sid, b64ToU8(m.b64)); } finally { post({ op: "ack", sid: m.sid }); } }
    else if (m.ev === "tunClose") { if (cbs.tunClose) cbs.tunClose(m.sid); }
  };
  var no = function () {}, noLan = "A rede LAN virtual só existe no Tátão do Windows. No celular, o servidor é compartilhado direto pela sala.";
  window.electronAPI = {
    isElectron: true, isMobile: true,
    getSettings: function () { return ask({ op: "getCfg" }).then(function (s) { try { return s ? JSON.parse(s) : {}; } catch (e) { return {}; } }); },
    saveSettings: function (c) { post({ op: "saveCfg", json: JSON.stringify(c) }); return P(true); },
    startServer: function () { return P({ ok: false }); }, stopServer: function () { return P(); },
    lanInfo: function () { return P([]); }, lanListen: function () { return P({ ok: true }); }, lanProbe: function () { return P({ ok: false }); },
    lanStart: function () { return P({ ok: false, error: noLan }); }, lanStop: function () { return P({ ok: true }); }, lanElevate: function () { return P(); },
    lanWrite: no, onLanIn: no, onLanStatus: no, onLanResume: no,
    onPickSource: no, pickSource: no, setShareOptions: no,
    onUpdateAvailable: no, onUpdateProgress: no, onUpdateReady: no, onUpdateError: no, onUpdateStatus: no,
    checkUpdate: function () { return P(); }, applyUpdate: function () { return P(); },
    openExternal: function (u) { post({ op: "open", url: String(u || "") }); return P(); },
    ytTitle: function () { return P(""); },
    // o servidor do JavaWorld ligado aparece pra sala como um mundo aberto
    mcWatch: function (on) { post({ op: "mcWatch", on: !!on }); }, mcShare: no,
    onMcWorld: function (cb) { cbs.world = cb; },
    // túnel: quem está na sala chega no servidor deste celular
    tunListen: function () { return P({ ok: false }); }, tunUnlisten: function () { return P(true); },
    tunConnect: function (port) { return ask({ op: "tunConnect", port: port }); },
    tunWrite: function (sid, u8) { post({ op: "tunWrite", sid: sid, b64: u8ToB64(u8) }); },
    tunPause: function (sid, on) { post({ op: "tunPause", sid: sid, on: !!on }); },
    tunEnd: function (sid) { post({ op: "tunEnd", sid: sid }); },
    onTunIncoming: no,
    onTunData: function (cb) { cbs.tunData = cb; },
    onTunClose: function (cb) { cbs.tunClose = cb; }
  };
  post({ op: "hello" });
})();
