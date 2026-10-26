// BitCraft crafting planner UI.
(function () {
  const data = window.BITCRAFT_DATA;
  const { UI, translateItemName } = window.I18N;
  const planner = window.createPlanner(data);
  const STORAGE_KEY = "bitcraft-planner-v1";
  const EXAMPLE = [{ k: findByName("Ferralith Pickaxe"), q: 1 }].filter((t) => t.k);

  const state = Object.assign(
    { lang: navigator.language && !navigator.language.startsWith("de") ? "en" : "de", targets: EXAMPLE, choices: {}, sort: "order" },
    loadState()
  );

  const $ = (sel) => document.querySelector(sel);
  const el = (tag, attrs, ...children) => {
    const node = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null || v === false) continue;
      if (k === "class") node.className = v;
      else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
      else node.setAttribute(k, v === true ? "" : v);
    }
    for (const c of children.flat()) if (c != null && c !== false) node.append(c.nodeType ? c : String(c));
    return node;
  };

  function findByName(name) {
    return Object.keys(data.items).find((k) => data.items[k][0] === name);
  }

  function loadState() {
    try {
      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
      if (!saved) return {};
      saved.targets = (saved.targets || []).filter((t) => data.items[t.k]);
      return saved;
    } catch (e) {
      return {};
    }
  }

  function saveState() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch (e) {
      /* storage unavailable: the planner still works for this visit */
    }
  }

  const t = (key, ...args) => {
    const v = UI[state.lang][key];
    return typeof v === "function" ? v(...args) : v;
  };
  const enName = (k) => data.items[k][0];
  const name = (k) => (state.lang === "de" ? translateItemName(enName(k)) : enName(k));
  const tier = (k) => data.items[k][1];
  const building = (r) => (r.b ? window.I18N.building(data.buildings[r.b] || "?", state.lang) : t("anyStation"));
  const stationLabel = (r) => building(r) + (r.b && r.bt > 0 ? " " + t("tier") + r.bt : "");
  const toolLabel = ([id, lvl]) => window.I18N.tool(data.tools[id] || "?", state.lang) + " " + t("tier") + lvl;
  const skillLabel = ([id, lvl]) => window.I18N.skill(data.skills[id] || "?", state.lang) + " " + t("level") + " " + lvl;
  const verb = (v) => window.I18N.verb(v, state.lang);

  function fmtQty(q) {
    const rounded = Math.round(q);
    if (Math.abs(q - rounded) < 0.01) return rounded.toLocaleString(state.lang);
    return "≈" + (Math.ceil(q * 10) / 10).toLocaleString(state.lang);
  }

  function itemName(k, cls) {
    return el(
      "span",
      { class: "item-name " + (cls || "") },
      el("span", { class: "tier-badge", title: "Tier " + tier(k) }, tier(k) > 0 ? "T" + tier(k) : "–"),
      el("span", { class: "names" }, el("span", { class: "primary" }, name(k)), state.lang === "de" && name(k) !== enName(k) ? el("span", { class: "secondary" }, enName(k)) : null)
    );
  }

  // ---------- search ----------
  const norm = (s) => s.toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/ß/g, "ss");
  const craftable = Object.keys(planner.byOutput).filter((k) => data.items[k]);
  let searchIndex = null;
  function buildSearchIndex() {
    searchIndex = craftable.map((k) => {
      const en = norm(enName(k));
      const de = norm(translateItemName(enName(k)));
      return { k, text: en + " " + de, words: Array.from(new Set((en + " " + de).split(/[\s\-:']+/).filter(Boolean))) };
    });
  }

  function lev(a, b) {
    if (Math.abs(a.length - b.length) > 2) return 3;
    const prev = Array.from({ length: b.length + 1 }, (_, i) => i);
    for (let i = 1; i <= a.length; i++) {
      let last = prev[0];
      prev[0] = i;
      for (let j = 1; j <= b.length; j++) {
        const tmp = prev[j];
        prev[j] = Math.min(prev[j] + 1, prev[j - 1] + 1, last + (a[i - 1] === b[j - 1] ? 0 : 1));
        last = tmp;
      }
    }
    return prev[b.length];
  }

  function tokenScore(token, entry) {
    if (entry.words.some((w) => w.startsWith(token))) return 3;
    if (entry.text.includes(token)) return 2;
    const allowed = token.length >= 7 ? 2 : token.length >= 4 ? 1 : 0;
    if (allowed && entry.words.some((w) => lev(token, w) <= allowed || lev(token, w.slice(0, token.length)) <= allowed)) return 1;
    return 0;
  }

  function search(query) {
    if (!searchIndex) buildSearchIndex();
    const tokens = norm(query).split(/[\s\-]+/).filter(Boolean);
    if (!tokens.length) return [];
    const hits = [];
    for (const entry of searchIndex) {
      let score = 0;
      for (const tok of tokens) {
        const s = tokenScore(tok, entry);
        if (!s) {
          score = -1;
          break;
        }
        score += s;
      }
      if (score > 0) hits.push({ k: entry.k, score });
    }
    hits.sort((a, b) => b.score - a.score || tier(a.k) - tier(b.k) || name(a.k).localeCompare(name(b.k)));
    return hits.slice(0, 40).map((h) => h.k);
  }

  let activeIndex = -1;
  let currentHits = [];
  function renderSearchResults() {
    const list = $("#search-results");
    const query = $("#search").value.trim();
    list.replaceChildren();
    if (!query) {
      list.hidden = true;
      $("#search").setAttribute("aria-expanded", "false");
      return;
    }
    currentHits = search(query);
    if (!currentHits.length) {
      list.append(el("li", { class: "empty" }, t("noResults")));
    }
    currentHits.forEach((k, i) => {
      list.append(
        el(
          "li",
          { role: "option", id: "opt-" + i, class: i === activeIndex ? "active" : "", "aria-selected": i === activeIndex ? "true" : "false", onmousedown: (e) => { e.preventDefault(); addTarget(k); } },
          itemName(k),
          el("span", { class: "tag" }, data.items[k][2])
        )
      );
    });
    list.hidden = false;
    $("#search").setAttribute("aria-expanded", "true");
  }

  function addTarget(k) {
    const existing = state.targets.find((x) => x.k === k);
    if (existing) existing.q += 1;
    else state.targets.push({ k, q: 1 });
    $("#search").value = "";
    activeIndex = -1;
    renderSearchResults();
    update();
    $("#search").focus();
  }

  // ---------- rendering ----------
  function renderStatic() {
    document.documentElement.lang = state.lang;
    document.title = t("title");
    document.querySelectorAll("[data-t]").forEach((n) => (n.textContent = t(n.dataset.t)));
    $("#search").placeholder = t("searchPlaceholder");
    document.querySelectorAll(".lang-toggle button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.lang === state.lang)));
    document.querySelectorAll(".sort-toggle button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.sort === state.sort)));
  }

  function renderTargets() {
    const list = $("#targets");
    list.replaceChildren();
    $("#targets-empty").hidden = state.targets.length > 0;
    $("#clear").hidden = state.targets.length === 0;
    state.targets.forEach((target, i) => {
      const input = el("input", { type: "number", min: "1", step: "1", value: target.q, id: "qty-" + target.k, "aria-label": t("qty") + ": " + name(target.k) });
      input.addEventListener("change", () => {
        target.q = Math.max(1, Math.floor(Number(input.value) || 1));
        update();
      });
      const step = (d) => () => {
        target.q = Math.max(1, target.q + d);
        update();
      };
      list.append(
        el(
          "li",
          { class: "target" },
          itemName(target.k),
          el(
            "div",
            { class: "qty" },
            el("button", { type: "button", class: "icon", "aria-label": "−1", onclick: step(-1) }, "−"),
            input,
            el("button", { type: "button", class: "icon", "aria-label": "+1", onclick: step(1) }, "+")
          ),
          el("button", { type: "button", class: "icon remove", title: t("remove"), "aria-label": t("remove") + ": " + name(target.k), onclick: () => { state.targets.splice(i, 1); update(); } }, "×")
        )
      );
    });
  }

  function requirementLine(r) {
    const parts = [];
    r.t.forEach((x) => parts.push(el("span", { class: "req tool" }, toolLabel(x))));
    r.s.forEach((x) => parts.push(el("span", { class: "req skill" }, skillLabel(x))));
    return parts.length ? el("div", { class: "reqs" }, parts) : null;
  }

  function sourceLine(src) {
    const verbText = window.I18N.verb(src.v, state.lang);
    const resName = state.lang === "de" ? translateItemName(src.r) : src.r;
    return el(
      "li",
      null,
      el("span", { class: "src-name" }, verbText + ": " + resName + (src.tier > 0 ? " (T" + src.tier + ")" : "")),
      src.t.map((x) => el("span", { class: "req tool" }, toolLabel(x))),
      src.s.map((x) => el("span", { class: "req skill" }, skillLabel(x)))
    );
  }

  function recipeSummary(idx) {
    const r = data.recipes[idx];
    return stationLabel(r) + ": " + r.in.map(([k, q]) => fmtQty(q) + "× " + name(k)).join(", ");
  }

  function recipeSelect(k, currentIdx) {
    const alts = planner.recipesFor(k);
    if (!alts.length) return null;
    const select = el("select", { id: "recipe-" + k, "aria-label": t("recipe") + ": " + name(k) });
    alts.forEach((idx, i) => select.append(el("option", { value: idx, selected: idx === currentIdx }, (alts.length > 1 ? t("recipeOption", i + 1) + " · " : "") + recipeSummary(idx))));
    select.append(el("option", { value: -1, selected: currentIdx === -1 }, t("farmInstead")));
    select.addEventListener("change", () => {
      state.choices[k] = Number(select.value);
      update();
    });
    return el("label", { class: "recipe-select" }, el("span", null, t("recipe")), select);
  }

  function renderFarm(result) {
    const body = $("#farm-list");
    body.replaceChildren();
    $("#farm-count").textContent = result.raw.length;
    result.raw.forEach((row) => {
      const srcs = data.sources[row.k] || [];
      const shown = srcs.slice(0, 2);
      const rest = srcs.slice(2);
      body.append(
        el(
          "li",
          { class: "farm-row" },
          el("span", { class: "amount", title: Math.abs(row.q - Math.round(row.q)) >= 0.01 ? t("expected") : null }, fmtQty(row.q)),
          el(
            "div",
            { class: "farm-main" },
            itemName(row.k),
            srcs.length
              ? el("ul", { class: "sources" }, shown.map(sourceLine), rest.length ? el("li", { class: "more" }, el("details", null, el("summary", null, t("moreSources", rest.length)), el("ul", { class: "sources" }, rest.map(sourceLine)))) : null)
              : el("p", { class: "no-source" }, row.cycle ? t("cycle") : t("noSource")),
            row.canCraft
              ? el("button", { type: "button", class: "link", onclick: () => { state.choices[row.k] = planner.recipesFor(row.k)[0]; update(); } }, t("craftInstead"))
              : null
          )
        )
      );
    });
  }

  function stepCard(step, number) {
    const r = data.recipes[step.recipe];
    return el(
      "li",
      { class: "step" + (step.isTarget ? " is-target" : "") },
      el("span", { class: "step-no", "aria-hidden": "true" }, number),
      el(
        "div",
        { class: "step-body" },
        el("div", { class: "step-head" }, el("span", { class: "station" }, stationLabel(r)), el("span", { class: "crafts" }, t("crafts", step.crafts))),
        el("div", { class: "step-title" }, el("span", { class: "verb" }, verb(r.v)), el("span", { class: "amount" }, fmtQty(step.made) + "×"), itemName(step.k)),
        el("ul", { class: "inputs", "aria-label": t("needs") }, r.in.map(([k, q]) => el("li", null, el("span", { class: "amount" }, fmtQty(q * step.crafts) + "×"), " ", name(k)))),
        requirementLine(r),
        recipeSelect(step.k, step.recipe)
      )
    );
  }

  function renderSteps(result) {
    const list = $("#steps");
    list.replaceChildren();
    $("#steps-count").textContent = result.steps.length;
    if (state.sort === "station") {
      const groups = new Map();
      result.steps.forEach((s, i) => {
        const label = stationLabel(data.recipes[s.recipe]);
        if (!groups.has(label)) groups.set(label, []);
        groups.get(label).push([s, i + 1]);
      });
      for (const [label, entries] of groups) {
        list.append(el("li", { class: "group" }, el("h3", null, label), el("ol", { class: "steps" }, entries.map(([s, n]) => stepCard(s, n)))));
      }
    } else {
      result.steps.forEach((s, i) => list.append(stepCard(s, i + 1)));
    }
  }

  function renderOverview(result) {
    const stations = new Map();
    const tools = new Map();
    result.steps.forEach((s) => {
      const r = data.recipes[s.recipe];
      const label = stationLabel(r);
      stations.set(label, (stations.get(label) || 0) + 1);
      r.t.forEach(([id, lvl]) => {
        const key = id;
        if (!tools.has(key) || tools.get(key)[1] < lvl) tools.set(key, [id, lvl]);
      });
    });
    result.raw.forEach((row) => {
      const src = (data.sources[row.k] || [])[0];
      if (src) src.t.forEach(([id, lvl]) => { if (!tools.has(id) || tools.get(id)[1] < lvl) tools.set(id, [id, lvl]); });
    });
    $("#stations").replaceChildren(...Array.from(stations, ([label, n]) => el("li", { class: "chip" }, label, el("span", { class: "chip-count" }, n))));
    $("#tools").replaceChildren(...Array.from(tools.values(), (x) => el("li", { class: "chip" }, toolLabel(x))));
  }

  function update() {
    saveState();
    renderStatic();
    renderTargets();
    const hasTargets = state.targets.length > 0;
    $("#results").hidden = !hasTargets;
    if (!hasTargets) return;
    const result = planner.plan(state.targets, state.choices);
    renderFarm(result);
    renderOverview(result);
    renderSteps(result);
  }

  // ---------- events ----------
  $("#search").addEventListener("input", () => {
    activeIndex = -1;
    renderSearchResults();
  });
  $("#search").addEventListener("keydown", (e) => {
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      e.preventDefault();
      if (!currentHits.length) return;
      activeIndex = (activeIndex + (e.key === "ArrowDown" ? 1 : -1) + currentHits.length) % currentHits.length;
      renderSearchResults();
      $("#search").setAttribute("aria-activedescendant", "opt-" + activeIndex);
      const opt = document.getElementById("opt-" + activeIndex);
      if (opt) opt.scrollIntoView({ block: "nearest" });
    } else if (e.key === "Enter") {
      e.preventDefault();
      const k = currentHits[activeIndex >= 0 ? activeIndex : 0];
      if (k) addTarget(k);
    } else if (e.key === "Escape") {
      $("#search").value = "";
      renderSearchResults();
    }
  });
  $("#search").addEventListener("blur", () => setTimeout(() => ($("#search-results").hidden = true), 120));
  $("#search").addEventListener("focus", renderSearchResults);
  document.querySelectorAll(".lang-toggle button").forEach((b) =>
    b.addEventListener("click", () => {
      state.lang = b.dataset.lang;
      update();
      renderSearchResults();
    })
  );
  document.querySelectorAll(".sort-toggle button").forEach((b) =>
    b.addEventListener("click", () => {
      state.sort = b.dataset.sort;
      update();
    })
  );
  $("#clear").addEventListener("click", () => {
    state.targets = [];
    state.choices = {};
    update();
  });
  $("#example").addEventListener("click", () => {
    state.targets = ["Ferralith Pickaxe", "Ferralith Axe", "Ferralith Hammer"].map(findByName).filter(Boolean).map((k) => ({ k, q: 1 }));
    update();
  });

  update();
})();
