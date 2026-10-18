/* F1 Race Strategy — web client. Same visual language as dashboard/index.html. */
"use strict";

const $ = id => document.getElementById(id);
const COL = {SOFT:"#E5484D", MEDIUM:"#F2C94C", HARD:"#E8EEF6", INTERMEDIATE:"#56C288", WET:"#5A9BD8"};

const state = {races:[], race:null, drivers:[], driver:null, plans:null, sim:null, analysis:null};

/* ---------------------------------------------------------------- helpers */
const fmtTotal = t => `${Math.floor(t/3600)}:${String(Math.floor(t%3600/60)).padStart(2,"0")}:${(t%60).toFixed(1).padStart(4,"0")}`;
const fmtMs = ms => ms>0 ? `${Math.floor(ms/60000)}:${(ms%60000/1000).toFixed(1).padStart(4,"0")}` : "—";
const fmtMsTotal = ms => {
  if (ms<=0) return "—";
  const h=Math.floor(ms/3600000), m=Math.floor(ms%3600000/60000), s=Math.floor(ms%60000/1000);
  return `${h}:${String(m).padStart(2,"0")}:${String(s).padStart(2,"0")}`;
};
const seq = s => s.stints.map(x=>x.compound[0]).join(" > ");
const bar = stints => stints.map(x=>`<i class="c-${x.compound}" style="flex:${x.laps}" title="${x.compound} × ${x.laps}">${x.laps}</i>`).join("");

async function api(path, {method="GET", body}={}) {
  // staged retry like the old JavaFX ApiClient (0/800/2000/4000 ms)
  const delays=[0,800,2000,4000]; let err;
  for (const d of delays) {
    if (d) await new Promise(r=>setTimeout(r,d));
    try {
      const res = await fetch(path, {method,
        headers: body ? {"Content-Type":"application/json"} : undefined,
        body: body ? JSON.stringify(body) : undefined});
      const text = await res.text();
      const data = text ? JSON.parse(text) : null;
      if (!res.ok) throw new Error((data && (data.detail||data.error)) || res.statusText);
      return data;
    } catch (e) { err = e; }
  }
  throw err;
}

function setStatus(el, msg, cls) { el.textContent = msg||""; el.className = "status" + (cls?" "+cls:""); }

/* generic multi-series line chart — same SVG recipe as the static dashboard */
function lineChart(el, series, {xLabel="", xMax=null}={}) {
  const W=600,H=280,L=48,R=12,T=12,B=36;
  const pts = series.flatMap(s=>s.values);
  if (!pts.length) { el.innerHTML=""; return; }
  const lo=Math.floor(Math.min(...pts)), hi=Math.ceil(Math.max(...pts));
  const n = Math.max(...series.map(s=>s.values.length));
  const xMaxV = xMax ?? n-1;
  const x = i => L + (xMaxV? i/xMaxV : 0)*(W-L-R);
  const y = v => T + (hi-v)/((hi-lo)||1)*(H-T-B);
  let g="";
  for (let v=lo; v<=hi; v+=Math.max(1,Math.ceil((hi-lo)/5)))
    g+=`<line class="axis" x1="${L}" x2="${W-R}" y1="${y(v)}" y2="${y(v)}"/><text x="${L-8}" y="${y(v)+4}" text-anchor="end">${v}</text>`;
  const tick = Math.max(1, Math.round(xMaxV/8));
  for (let a=0; a<=xMaxV; a+=tick) g+=`<text x="${x(a)}" y="${H-14}" text-anchor="middle">${a}</text>`;
  if (xLabel) g+=`<text x="${(W+L)/2}" y="${H}" text-anchor="middle">${xLabel}</text>`;
  for (const s of series)
    g+=`<polyline fill="none" stroke="${s.color}" stroke-width="2.5" points="${s.values.map((v,i)=>x(i)+","+y(v)).join(" ")}"/>`;
  el.innerHTML = `<svg viewBox="0 0 ${W} ${H}" role="img">${g}</svg>`;
}

/* ---------------------------------------------------------------- tabs */
document.querySelectorAll("#tabs button").forEach(b => b.onclick = () => {
  document.querySelectorAll("#tabs button").forEach(x=>x.setAttribute("aria-selected", x===b));
  document.querySelectorAll(".tab").forEach(t=>t.hidden = t.id !== "tab-"+b.dataset.tab);
});

/* ---------------------------------------------------------------- health */
async function checkHealth() {
  const set=(el,ok)=>{el.className="dot "+(ok?"ok":"down")};
  try { const h = await api("/api/engine/health"); set($("dotEngine"), h.status==="ok"); }
  catch { set($("dotEngine"), false); }
  try { await api("/api/races"); set($("dotApi"), true); }
  catch { set($("dotApi"), false); }
}

/* ---------------------------------------------------------------- races */
/* Spring returns JPA entities in camelCase; normalize to the engine's snake_case */
const normRace = r => ({race_id: r.race_id ?? r.raceId, year: r.year, round: r.round,
  name: r.name, circuit: r.circuit, total_laps: r.total_laps ?? r.totalLaps});
const normDriver = d => ({driver_id: d.driver_id ?? d.driverId, full_name: d.full_name ?? d.fullName,
  code: d.code, team: d.team});

async function loadRaces() {
  try {
    state.races = ((await api("/api/races")) || []).map(normRace);
    $("raceRows").innerHTML = state.races.length ? state.races.map(r=>`
      <tr class="selectable ${state.race&&state.race.race_id===r.race_id?"selected":""}" data-id="${r.race_id}">
      <td>${r.round}</td><td>${r.name}</td><td>${r.circuit||"—"}</td><td>${r.total_laps}</td><td>${r.year}</td></tr>`).join("")
      : `<tr><td colspan="5" class="empty">No races yet — import one above.</td></tr>`;
  } catch (e) { $("raceRows").innerHTML = `<tr><td colspan="5" class="empty">Backend unreachable: ${e.message}</td></tr>`; }
}

$("raceRows").addEventListener("click", e => {
  const tr = e.target.closest("tr.selectable"); if (!tr) return;
  selectRace(state.races.find(r=>r.race_id===tr.dataset.id));
});

function selectRace(race) {
  state.race = race; state.driver = null; state.plans = null; state.sim = null; state.analysis = null;
  $("race").textContent = `${race.race_id} · ${race.name}, ${race.total_laps} laps`;
  document.querySelectorAll("#raceRows tr").forEach(tr=>tr.classList.toggle("selected", tr.dataset.id===race.race_id));
  $("drvRaceHint").textContent = `of ${race.name}`;
  $("driverList").innerHTML = `<div class="empty">Loading…</div>`;
  loadDrivers();
  resetStrategyTab(); resetSimTab(); resetAnalyzeTab();
  fillRivalSelect();
}

$("btnRefresh").onclick = loadRaces;

$("btnImport").onclick = async () => {
  const year=$("impYear").value.trim(), round=$("impRound").value.trim();
  if (!/^\d{4}$/.test(year)) return setStatus($("impStatus"), "Season must be a 4-digit year.", "error");
  if (!/^\d+$/.test(round)) return setStatus($("impStatus"), "Round must be a number.", "error");
  const source = $("impSource").value;
  const body = source==="demo" ? {year:+year, round:+round, demo:true}
                               : {year:+year, round:+round, source};
  setStatus($("impStatus"), "Importing… this can take a while for real data.");
  try {
    const res = await api("/api/ingest", {method:"POST", body});
    setStatus($("impStatus"), `Imported ${res.name}: ${res.laps_stored} laps, ${res.drivers_stored} drivers.`, "ok");
    await loadRaces();
  } catch (e) { setStatus($("impStatus"), "Import failed: "+e.message, "error"); }
};

/* ---------------------------------------------------------------- drivers */
async function loadDrivers() {
  if (!state.race) return setStatus($("drvStatus"), "Select a race on the Races tab first.", "error");
  try {
    state.drivers = ((await api(`/api/races/${state.race.race_id}/drivers`)) || []).map(normDriver);
    $("driverList").innerHTML = state.drivers.length ? state.drivers.map(d=>`
      <button class="drv" data-id="${d.driver_id}" aria-pressed="false">
        <span><span class="code">${d.code||d.driver_id.toUpperCase()}</span>&nbsp; ${d.full_name}</span>
        <span class="team">${d.team||""}</span></button>`).join("")
      : `<div class="empty">No drivers with lap data for this race.</div>`;
    setStatus($("drvStatus"), "");
  } catch (e) { $("driverList").innerHTML = `<div class="empty">${e.message}</div>`; }
  fillRivalSelect();
}

$("btnDrivers").onclick = loadDrivers;

$("driverList").addEventListener("click", e => {
  const b = e.target.closest("button.drv"); if (!b) return;
  selectDriver(b.dataset.id);
});

function selectDriver(id) {
  state.driver = id;
  document.querySelectorAll("#driverList .drv").forEach(x=>x.setAttribute("aria-pressed", x.dataset.id===id));
  const d = state.drivers.find(x=>x.driver_id===id);
  $("race").textContent = `${state.race.race_id} · ${state.race.name}, ${state.race.total_laps} laps — ${id.toUpperCase()} ${d?d.full_name:""}`;
  resetAnalyzeTab(); resetSimTab();
  loadPlans();
}

function fillRivalSelect() {
  const sel = $("duelRival");
  sel.innerHTML = (state.drivers||[]).map(d=>`<option value="${d.driver_id}">${d.code||d.driver_id.toUpperCase()} — ${d.full_name}</option>`).join("")
    || `<option value="">— load drivers —</option>`;
  if (state.driver) sel.value = state.drivers.map(d=>d.driver_id).find(x=>x!==state.driver) || "";
}

/* ---------------------------------------------------------------- analyze */
function resetAnalyzeTab() {
  ["stBest","stAvg","stFuel","stLaps"].forEach(id=>$(id).textContent="—");
  $("lapChart").innerHTML = `<div class="empty">Run “Analyze driver”.</div>`;
  $("stintBar").innerHTML = `<i style="flex:1"></i>`;
  $("stintLegend").innerHTML = "";
  $("pitLine").textContent = "Actual pit stops: —";
  $("mlMeta").textContent = ""; $("mlPreds").innerHTML = "";
  $("anaRaw").textContent = "";
  setStatus($("anaStatus"),""); setStatus($("mlStatus"),"");
}

$("btnAnalyze").onclick = async () => {
  if (!state.race || !state.driver) return setStatus($("anaStatus"), "Select a race and a driver first.", "error");
  setStatus($("anaStatus"), "Analyzing…");
  try {
    const rep = await api(`/api/analyze/${state.race.race_id}/${state.driver}`);
    state.analysis = rep;
    $("stBest").textContent = fmtMs(rep.best_lap_ms);
    $("stAvg").textContent = fmtMs(rep.avg_lap_ms);
    $("stFuel").textContent = rep.fuel_effect_s_per_lap==null ? "—" : rep.fuel_effect_s_per_lap.toFixed(3);
    $("stLaps").textContent = rep.pace_trend.length;
    lineChart($("lapChart"),
      [{color:COL.MEDIUM, values:rep.pace_trend.map(p=>p.lap_time_ms/1000)}],
      {xLabel:"Lap", xMax:rep.pace_trend.length});
    const stints = rep.stints||[];
    $("stintBar").innerHTML = stints.map(s=>`<i class="c-${s.compound}" style="flex:${Math.max(1,s.end_lap-s.start_lap+1)}" title="${s.compound} L${s.start_lap}–${s.end_lap}">${s.compound[0]}</i>`).join("") || `<i style="flex:1"></i>`;
    $("stintLegend").innerHTML = stints.map(s=>`<span><b class="c-${s.compound}"></b>${s.compound} L${s.start_lap}–${s.end_lap}</span>`).join("");
    const pits = rep.pit_stops||[];
    $("pitLine").textContent = "Actual pit stops: " + (pits.length
      ? pits.map(p=>`Lap ${p.lap}${p.duration_ms?` (${fmtMs(p.duration_ms)})`:""}`).join(" · ")
      : "none recorded");
    $("anaRaw").textContent = JSON.stringify(rep, null, 2);
    setStatus($("anaStatus"), "");
  } catch (e) { setStatus($("anaStatus"), e.message, "error"); }
};

$("btnPredict").onclick = async () => {
  if (!state.race || !state.driver) return setStatus($("mlStatus"), "Select a race and a driver first.", "error");
  setStatus($("mlStatus"), "Training model…");
  try {
    const p = await api("/api/predict", {method:"POST", body:{race_id:state.race.race_id, driver_id:state.driver, model:$("mlModel").value}});
    $("mlMeta").textContent = `Model MAE: ${p.mae_ms} ms · tyre degradation: ${p.tyre_degradation_ms_per_lap} ms/lap`;
    $("mlPreds").innerHTML = p.predictions.map(x=>`<span>L${x.lap}→${fmtMs(x.predicted_lap_time_ms)}</span>`).join("");
    setStatus($("mlStatus"), "");
  } catch (e) { setStatus($("mlStatus"), e.message, "error"); }
};

/* ---------------------------------------------------------------- strategy */
function resetStrategyTab() {
  $("recTitle").textContent = "—";
  $("recSub").textContent = "Select a race, then load the strategy plans.";
  $("recBar").innerHTML = "";
  $("planList").innerHTML = `<div class="empty">Load the plans for the selected race.</div>`;
  $("degrChart").innerHTML = `<div class="empty">Load the plans for the selected race.</div>`;
  $("searchModel").textContent = "";
  $("recVerdict").textContent = ""; $("recVerdict").className = "verdict";
  $("recConfRow").hidden = true; $("recScenarios").innerHTML = "";
  $("duelVerdict").textContent = ""; $("duelVerdict").className = "verdict";
  $("duelScenarios").innerHTML = "";
  setStatus($("recStatus"),""); setStatus($("duelStatus"),""); setStatus($("calStatus"),"");
}

async function loadPlans() {
  if (!state.race || !state.driver) return;
  setStatus($("calStatus"), "Loading strategy plans…");
  try {
    state.plans = await api("/api/strategy/search", {method:"POST", body:{race_id:state.race.race_id}});
    renderPlans(state.plans);
    setStatus($("calStatus"), "");
  } catch (e) { setStatus($("calStatus"), e.message, "error"); }
}

function renderPlans(D) {
  const select = i => {
    const s = D.strategies[i];
    $("recTitle").textContent = `${s.stops}-stop: ${seq(s)}`;
    $("recSub").textContent = s.delta===0 ? `Fastest option, race time ${fmtTotal(s.time)}`
                                          : `+${s.delta.toFixed(1)} s vs. fastest, race time ${fmtTotal(s.time)}`;
    $("recBar").innerHTML = bar(s.stints);
    document.querySelectorAll("#planList button.row").forEach((b,j)=>b.setAttribute("aria-pressed", j===i));
  };
  $("planList").innerHTML = D.strategies.map((s,i)=>`
    <button class="row" data-i="${i}"><div class="meta"><span>${s.stops} stop${s.stops>1?"s":""}</span>
    <span>${s.delta===0?"Fastest":"+"+s.delta.toFixed(1)+" s"}</span></div>
    <div class="bar">${bar(s.stints)}</div></button>`).join("");
  $("planList").onclick = e => { const b=e.target.closest("button.row"); if (b) select(+b.dataset.i); };
  select(0);
  $("searchModel").textContent = D.model==="calibrated" ? "calibrated race model" : "built-in default model";
  lineChart($("degrChart"),
    Object.entries(D.degradation).map(([c,v])=>({color:COL[c]||"#888", values:v})),
    {xLabel:"Tyre age (laps)", xMax:40});
}

$("btnRecal").onclick = async () => {
  if (!state.race) return setStatus($("calStatus"), "Select a race first.", "error");
  setStatus($("calStatus"), "Calibrating on this race's clean laps…");
  try {
    const out = await api(`/api/calibrate/${state.race.race_id}`, {method:"POST"});
    setStatus($("calStatus"), `Calibrated: rmse ${out.rmse}s over ${out.n_laps} laps.`, "ok");
    await loadPlans();
  } catch (e) { setStatus($("calStatus"), e.message, "error"); }
};

function scenRow(sc) {
  return `<div class="scen"><b>Pit lap ${sc.pit_lap} on <span class="chip c-${sc.compound}">${sc.compound}</span></b>
    <span>P${sc.finish_position} · ${fmtMsTotal(sc.total_time_ms)}</span></div>`;
}

$("btnRecommend").onclick = async () => {
  if (!state.race || !state.driver) return setStatus($("recStatus"), "Select a race and a driver first.", "error");
  setStatus($("recStatus"), "Simulating scenarios…");
  try {
    const r = await api("/api/strategy/recommend", {method:"POST", body:{
      race_id:state.race.race_id, driver_id:state.driver,
      current_lap:+$("recLap").value||1, current_compound:$("recComp").value}});
    $("recVerdict").textContent = `PIT lap ${r.recommended_pit_lap} → ${r.recommended_compound} · expected P${r.expected_finish_position}`;
    $("recVerdict").className = "verdict good";
    if (r.rain_ahead && r.rain_onset_lap) $("recVerdict").textContent += `   🌧 rain from lap ${r.rain_onset_lap}`;
    $("recConfRow").hidden = false;
    $("recConfFill").style.width = `${Math.round(r.confidence*100)}%`;
    $("recConfPct").textContent = `${Math.round(r.confidence*100)}%`;
    $("recScenarios").innerHTML = r.scenarios.map(scenRow).join("");
    setStatus($("recStatus"), "");
  } catch (e) { setStatus($("recStatus"), e.message, "error"); }
};

$("btnDuel").onclick = async () => {
  if (!state.race || !state.driver) return setStatus($("duelStatus"), "Select a race and a driver first.", "error");
  const rival = $("duelRival").value;
  if (!rival || rival===state.driver) return setStatus($("duelStatus"), "Pick a rival different from your driver.", "error");
  setStatus($("duelStatus"), "Racing the duel…");
  try {
    const r = await api("/api/strategy/duel", {method:"POST", body:{
      race_id:state.race.race_id, driver_id:state.driver, rival_id:rival,
      current_lap:+$("duelLap").value||1, current_compound:$("recComp").value}});
    $("duelVerdict").textContent = r.beats_rival
      ? `${r.best_option} ✓ beats ${r.rival_id.toUpperCase()} (pit L${r.best_pit_lap} → ${r.best_compound})`
      : `${r.best_option} ✗ cannot beat ${r.rival_id.toUpperCase()} (we P${r.scenarios[0].my_finish_position} · rival P${r.scenarios[0].rival_finish_position})`;
    $("duelVerdict").className = "verdict " + (r.beats_rival ? "good" : "bad");
    $("duelScenarios").innerHTML = r.scenarios.map(s=>`
      <div class="scen"><b>${s.option.split(" — ")[0]} — pit L${s.pit_lap} on <span class="chip c-${s.compound}">${s.compound}</span></b>
      <span class="${s.beats_rival?"win":""}">we P${s.my_finish_position} · rival P${s.rival_finish_position}</span></div>`).join("");
    setStatus($("duelStatus"), "");
  } catch (e) { setStatus($("duelStatus"), e.message, "error"); }
};

/* ---------------------------------------------------------------- simulate */
function resetSimTab() {
  $("simVerdict").textContent = ""; $("simVerdict").className = "verdict";
  $("simRows").innerHTML = `<tr><td colspan="5" class="empty">No simulation yet.</td></tr>`;
  $("btnExport").disabled = true; state.sim = null;
  setStatus($("simStatus"),"");
}

$("btnSimulate").onclick = async () => {
  if (!state.race || !state.driver) return setStatus($("simStatus"), "Select a race and a driver first.", "error");
  const pits = $("simPits").value.split(",").map(x=>parseInt(x,10)).filter(x=>Number.isFinite(x)&&x>0);
  setStatus($("simStatus"), "Simulating 20 cars…");
  try {
    const r = await api("/api/simulate", {method:"POST", body:{
      race_id:state.race.race_id, driver_id:state.driver,
      start_compound:$("simComp").value, pit_laps:pits}});
    state.sim = r;
    $("simVerdict").textContent = `Finish P${r.finish_position} · race time ${fmtMsTotal(r.total_time_ms)}`;
    $("simVerdict").className = "verdict " + (r.finish_position<=3 ? "podium" : "");
    $("simRows").innerHTML = r.timeline.map(t=>`
      <tr><td>${t.lap}</td><td><span class="chip c-${t.compound}">${t.compound}</span></td>
      <td>${t.tyre_age}</td><td>P${t.position}</td>
      <td>${t.pit?`<span class="chip chip-pit">PIT</span>`:""}</td></tr>`).join("");
    $("btnExport").disabled = false;
    setStatus($("simStatus"), "");
  } catch (e) { setStatus($("simStatus"), e.message, "error"); }
};

$("btnExport").onclick = () => {
  if (!state.sim) return;
  const blob = new Blob([JSON.stringify(state.sim, null, 2)], {type:"application/json"});
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `simulation_${state.race.race_id}_${state.driver}.json`;
  a.click();
  URL.revokeObjectURL(a.href);
};

/* ---------------------------------------------------------------- init */
checkHealth();
loadRaces();
setInterval(checkHealth, 15000);
