// Crafting planner: expands wanted items into raw materials and ordered crafting steps.
(function (root) {
  const EPS = 1e-9;

  function createPlanner(data) {
    const { items, recipes, sources } = data;

    // Recipes indexed by the items they mainly produce (ignores small bonus outputs like
    // "High Quality ..." variants that only drop with a few percent chance).
    const byOutput = {};
    recipes.forEach((r, idx) => {
      r.out.forEach(([k, q], i) => {
        if (i === 0 || q >= 0.5) (byOutput[k] = byOutput[k] || []).push(idx);
      });
    });

    const outputQty = (r, k) => {
      const o = r.out.find((x) => x[0] === k);
      return o ? o[1] : 0;
    };
    const isDeadEnd = (k) => !sources[k] && !(byOutput[k] && byOutput[k].length);
    const hasSource = (k) => !!sources[k];

    // Default recipe per item: farmable items are farmed, others use the recipe that avoids
    // cycles and unobtainable inputs and needs the fewest inputs.
    const defaults = {};
    function defaultRecipe(k, visiting) {
      if (k in defaults) return defaults[k];
      if (hasSource(k) || !byOutput[k]) return (defaults[k] = -1);
      visiting = visiting || new Set();
      visiting.add(k);
      let best = -1;
      let bestScore = Infinity;
      for (const idx of byOutput[k]) {
        const r = recipes[idx];
        if (r.in.some(([i]) => i === k || visiting.has(i))) continue;
        let score = 0;
        for (const [i, q] of r.in) {
          if (isDeadEnd(i)) score += 100;
          else if (!hasSource(i) && defaultRecipe(i, visiting) === -1) score += 50;
          score += 1 + q / 1000;
        }
        score -= Math.min(outputQty(r, k), 10) * 0.01;
        if (score < bestScore) {
          bestScore = score;
          best = idx;
        }
      }
      visiting.delete(k);
      return (defaults[k] = best);
    }

    function recipesFor(k) {
      return byOutput[k] || [];
    }

    // targets: [{k, q}], choices: {itemKey: recipeIndex | -1 for "farm/buy"}
    function plan(targets, choices) {
      choices = choices || {};
      const chosen = {};
      const cycles = new Set();
      const order = []; // post-order: inputs before the items made from them
      const state = {}; // 1 = visiting, 2 = done

      function choose(k) {
        if (k in choices && (choices[k] === -1 || recipesFor(k).includes(choices[k]))) return choices[k];
        return defaultRecipe(k);
      }

      function visit(k) {
        if (state[k] === 2) return;
        if (state[k] === 1) {
          cycles.add(k);
          return;
        }
        state[k] = 1;
        const idx = choose(k);
        chosen[k] = idx;
        if (idx >= 0) for (const [i] of recipes[idx].in) visit(i);
        state[k] = 2;
        order.push(k);
      }
      targets.forEach((t) => visit(t.k));

      // Items in a cycle are bought/farmed instead to keep the plan finite.
      cycles.forEach((k) => (chosen[k] = -1));

      // Propagate demand from the wanted items down to raw materials (reverse post-order
      // guarantees that every consumer is processed before the items it consumes).
      const demand = {};
      targets.forEach((t) => (demand[t.k] = (demand[t.k] || 0) + t.q));
      const steps = [];
      const raw = [];
      const byproducts = {};
      for (let n = order.length - 1; n >= 0; n--) {
        const k = order[n];
        const need = demand[k] || 0;
        if (need <= EPS) continue;
        const idx = chosen[k];
        if (idx < 0) {
          raw.push({ k, q: need, cycle: cycles.has(k), canCraft: recipesFor(k).length > 0, farmedByChoice: k in choices && choices[k] === -1 });
          continue;
        }
        const r = recipes[idx];
        const perCraft = outputQty(r, k);
        const crafts = Math.ceil(need / perCraft - EPS);
        for (const [i, q] of r.in) demand[i] = (demand[i] || 0) + q * crafts;
        for (const [o, q] of r.out) if (o !== k) byproducts[o] = (byproducts[o] || 0) + q * crafts;
        steps.push({ k, need, crafts, made: perCraft * crafts, recipe: idx, alternatives: recipesFor(k), isTarget: targets.some((t) => t.k === k) });
      }
      // Crafting order: deepest intermediates first.
      steps.reverse();
      raw.sort((a, b) => (items[a.k][1] - items[b.k][1]) || items[a.k][0].localeCompare(items[b.k][0]));
      return { steps, raw, byproducts, demand };
    }

    return { plan, recipesFor, defaultRecipe, byOutput };
  }

  root.createPlanner = createPlanner;
  if (typeof module !== "undefined") module.exports = { createPlanner };
})(typeof window !== "undefined" ? window : globalThis);
