/**
 * ChromaFlow: Liquid Jam - Core Game Loop & Rendering Engine
 * High-DPI 2D HTML5 Canvas + Physics + WWDC25 Liquid Glass UI
 */

(function () {
  'use strict';

  // --- GAME STATE ---
  const state = {
    levelNum: 1,
    coins: 150,
    shards: 10,
    streak: 0,
    maxStreak: 0,
    currentTheme: 'crystal',
    bufferCapacity: 5,
    upgrades: {
      coinMultiplier: 1,
      streakBonus: 0,
      prismRadius: 1,
      boosterRecharge: 0
    },
    boosters: {
      magnet: 2,
      shuffle: 2,
      slot: 1
    },
    unlockedThemes: ['crystal', 'cyber'],
    activeMode: 'PLAY', // 'PLAY' | 'LAB' | 'THEMES'
    activeBooster: null, // 'magnet' | null
    levelData: null,
    boardOffset: { x: 0, y: 0, width: 0, height: 0, cellSize: 0 },
    bufferSlots: [], // array of { id, color, x, y }
    animatingCapsules: [], // flying capsules { x, y, startX, startY, targetX, targetY, t, duration, color, onComplete }
    particles: [], // visual particles
    shakingCapsules: new Map(), // id -> { offset, duration, timer }
    flashingObstacles: new Map(), // id -> timer
    mergingArcs: [], // active golden arcs { fromX, fromY, toX, toY, life }
    sloshingPhase: 0,
    isGameOver: false,
    isLevelWon: false,
    isPaused: false
  };

  // --- SAVE / LOAD SYSTEM (localStorage) ---
  const STORAGE_KEY = 'chromaflow_save_v1';
  function loadSavedData() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const data = JSON.parse(raw);
        if (data.levelNum) state.levelNum = data.levelNum;
        if (data.coins !== undefined) state.coins = data.coins;
        if (data.shards !== undefined) state.shards = data.shards;
        if (data.upgrades) Object.assign(state.upgrades, data.upgrades);
        if (data.boosters) Object.assign(state.boosters, data.boosters);
        if (data.unlockedThemes) state.unlockedThemes = data.unlockedThemes;
        if (data.currentTheme) state.currentTheme = data.currentTheme;
      }
    } catch (e) {
      console.warn('Load storage failed:', e);
    }
  }

  function saveGameData() {
    try {
      const data = {
        levelNum: state.levelNum,
        coins: state.coins,
        shards: state.shards,
        upgrades: state.upgrades,
        boosters: state.boosters,
        unlockedThemes: state.unlockedThemes,
        currentTheme: state.currentTheme
      };
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch (e) {
      console.warn('Save storage failed:', e);
    }
  }

  // --- DOM ELEMENTS ---
  const bgCanvas = document.getElementById('bgCanvas');
  const bgCtx = bgCanvas.getContext('2d');
  const gameCanvas = document.getElementById('gameCanvas');
  const ctx = gameCanvas.getContext('2d');

  const levelBadge = document.getElementById('level-badge');
  const streakText = document.getElementById('streak-text');
  const coinCounter = document.getElementById('coin-counter');
  const gemCounter = document.getElementById('gem-counter');
  const vialsContainer = document.getElementById('target-vials-tray');
  const bufferSlotsContainer = document.getElementById('buffer-slots');
  const bufferDockBox = document.getElementById('buffer-dock-container');
  const bufferCountText = document.getElementById('buffer-count');

  const magnetCountEl = document.getElementById('magnet-count');
  const shuffleCountEl = document.getElementById('shuffle-count');
  const slotCountEl = document.getElementById('slot-count');

  // Modals
  const victoryModal = document.getElementById('victory-modal');
  const overflowModal = document.getElementById('overflow-modal');
  const rewardedAdModal = document.getElementById('rewarded-ad-modal');
  const labModal = document.getElementById('lab-modal');
  const themesModal = document.getElementById('themes-modal');

  // Background light orbs
  const bgOrbs = [
    { x: 0.2, y: 0.25, r: 140, vx: 0.015, vy: 0.018, color: 'rgba(0, 229, 255, 0.22)' },
    { x: 0.8, y: 0.35, r: 180, vx: -0.012, vy: 0.015, color: 'rgba(255, 46, 147, 0.25)' },
    { x: 0.5, y: 0.75, r: 160, vx: 0.018, vy: -0.014, color: 'rgba(157, 78, 221, 0.20)' },
    { x: 0.3, y: 0.60, r: 130, vx: -0.014, vy: -0.016, color: 'rgba(255, 215, 0, 0.18)' }
  ];

  // --- CANVAS RESIZE & DPI SCALING ---
  let dpr = 1;
  let viewWidth = 0;
  let viewHeight = 0;

  function resizeCanvases() {
    const container = document.getElementById('app-container');
    const rect = container.getBoundingClientRect();
    viewWidth = rect.width;
    viewHeight = rect.height;
    dpr = window.devicePixelRatio || 1;

    bgCanvas.width = viewWidth * dpr;
    bgCanvas.height = viewHeight * dpr;
    bgCtx.scale(dpr, dpr);

    gameCanvas.width = viewWidth * dpr;
    gameCanvas.height = viewHeight * dpr;
    ctx.scale(dpr, dpr);

    updateBoardMetrics();
  }

  function updateBoardMetrics() {
    if (!state.levelData) return;
    const { rows, cols } = state.levelData;

    // Available space between top vials and bottom buffer dock
    const topMargin = 150;
    const bottomMargin = 175;
    const availWidth = viewWidth - 32;
    const availHeight = viewHeight - topMargin - bottomMargin;

    const cellWidth = Math.floor(availWidth / cols);
    const cellHeight = Math.floor(availHeight / rows);
    const cellSize = Math.min(cellWidth, cellHeight, 56);

    const boardWidth = cellSize * cols;
    const boardHeight = cellSize * rows;
    const startX = Math.floor((viewWidth - boardWidth) / 2);
    const startY = topMargin + Math.floor((availHeight - boardHeight) / 2);

    state.boardOffset = { x: startX, y: startY, width: boardWidth, height: boardHeight, cellSize };
  }

  // --- LEVEL INITIALIZATION ---
  function startLevel(num) {
    state.levelNum = num;
    state.streak = 0;
    state.isGameOver = false;
    state.isLevelWon = false;
    state.animatingCapsules = [];
    state.particles = [];
    state.bufferSlots = [];
    state.activeBooster = null;
    state.bufferCapacity = 5;

    state.levelData = window.LevelGenerator.generateLevel(num);
    updateBoardMetrics();
    renderVialsHUD();
    renderBufferHUD();
    updateHUDCounters();
    closeAllModals();

    // Check if buffer dock was danger pulsing, reset
    bufferDockBox.classList.remove('danger-pulse');
  }

  // --- RENDER HUD ELEMENTS ---
  function updateHUDCounters() {
    levelBadge.textContent = `LVL ${state.levelNum}`;
    streakText.textContent = `x${state.streak + 1 + state.upgrades.streakBonus}`;
    coinCounter.textContent = `${state.coins}`;
    gemCounter.textContent = `${state.shards}`;
    magnetCountEl.textContent = `${state.boosters.magnet}`;
    shuffleCountEl.textContent = `${state.boosters.shuffle}`;
    slotCountEl.textContent = `${state.boosters.slot}`;
    bufferCountText.textContent = `${state.bufferSlots.length}/${state.bufferCapacity}`;

    const dispFilter = document.getElementById('liquid-refract-map');
    if (state.bufferSlots.length >= state.bufferCapacity - 1) {
      bufferDockBox.classList.add('danger-pulse');
      if (dispFilter) dispFilter.setAttribute('scale', '45');
    } else {
      bufferDockBox.classList.remove('danger-pulse');
      if (dispFilter) dispFilter.setAttribute('scale', '24');
    }
  }

  function renderVialsHUD() {
    vialsContainer.innerHTML = '';
    if (!state.levelData) return;

    // Show up to 3 active uncompleted vials
    const activeVials = state.levelData.vials.filter(v => !v.completed).slice(0, 3);

    activeVials.forEach(vial => {
      const colorDef = window.COLORS[vial.color] || window.COLORS.RED;
      const pct = Math.min(100, Math.floor((vial.current / vial.target) * 100));

      const card = document.createElement('div');
      card.className = `vial-card ${vial.completed ? 'completed' : ''}`;
      card.id = `ui_${vial.id}`;

      card.innerHTML = `
        ${vial.completed ? '<div class="vial-cork"></div>' : ''}
        <div class="vial-liquid" style="height: ${pct}%; background: linear-gradient(180deg, ${colorDef.lightHex} 0%, ${colorDef.hex} 70%, ${colorDef.darkHex} 100%);">
          <div class="vial-liquid-wave"></div>
        </div>
        <div class="vial-graduations">
          <div class="vial-grad-mark"></div>
          <div class="vial-grad-mark"></div>
          <div class="vial-grad-mark"></div>
        </div>
        <div class="vial-info">
          <span class="vial-title" style="color: ${colorDef.lightHex};">${vial.isBoss ? '👑 ' : ''}${colorDef.name.split(' ')[0]}</span>
          <span class="vial-counter">${vial.current}/${vial.target}</span>
        </div>
      `;
      vialsContainer.appendChild(card);
    });
  }

  function renderBufferHUD() {
    bufferSlotsContainer.innerHTML = '';
    for (let i = 0; i < state.bufferCapacity; i++) {
      const slot = document.createElement('div');
      const item = state.bufferSlots[i];
      slot.className = `buffer-slot ${item ? 'filled' : ''}`;
      slot.id = `buffer_slot_${i}`;

      if (item) {
        const colorDef = window.COLORS[item.color] || window.COLORS.RED;
        slot.style.boxShadow = `0 0 12px ${colorDef.hex}, inset 0 0 8px ${colorDef.lightHex}`;
        slot.style.borderColor = colorDef.lightHex;
        slot.innerHTML = `
          <div style="width: 32px; height: 32px; border-radius: 50%; background: radial-gradient(circle at 35% 35%, ${colorDef.lightHex}, ${colorDef.hex} 60%, ${colorDef.darkHex}); box-shadow: 0 2px 6px rgba(0,0,0,0.6); border: 1.5px solid rgba(255,255,255,0.7); display:flex; align-items:center; justify-content:center;">
            <span style="font-size: 11px; font-weight: 900; color: #FFF; text-shadow: 0 1px 2px #000;">${item.color[0]}</span>
          </div>
        `;
      }
      bufferSlotsContainer.appendChild(slot);
    }
    updateHUDCounters();
  }

  // --- RAYCAST UNBLOCKING LOGIC ---
  function handleCellClick(gridR, gridC) {
    if (state.isGameOver || state.isLevelWon || !state.levelData) return;
    const grid = state.levelData.grid;
    const item = grid[gridR][gridC];
    if (!item) return;

    // Obstacle clicked
    if (item.type === 'OBSTACLE') {
      window.soundEngine.playBlocked();
      triggerCapsuleShake(item.id);
      return;
    }

    if (item.type !== 'CAPSULE') return;

    // Booster: Magnet Extract
    if (state.activeBooster === 'magnet') {
      state.activeBooster = null;
      document.getElementById('btn-magnet').classList.remove('active');
      window.soundEngine.playBooster();
      grid[gridR][gridC] = null;
      launchCapsule(item, true); // force extract
      return;
    }

    // Ice Capsule handling
    if (item.ice > 0) {
      item.ice--;
      window.soundEngine.playTapGlass(1.4);
      triggerCapsuleShake(item.id);
      spawnIceCrackParticles(gridR, gridC);
      if (item.ice === 0) {
        // Thawed!
        spawnShockwave(gridR, gridC, '#00E5FF');
      }
      return;
    }

    // Raycast in arrow direction to check if clear
    const ray = window.LevelGenerator.raycast(
      grid,
      state.levelData.rows,
      state.levelData.cols,
      gridR,
      gridC,
      item.direction
    );

    if (!ray.clear) {
      // Path is blocked! Play low bass thud, spring shake, flash red specular rim along blocking obstacle
      window.soundEngine.playBlocked();
      triggerCapsuleShake(item.id);
      if (ray.blocker) {
        state.flashingObstacles.set(ray.blocker.id, 0.28);
        triggerCapsuleShake(ray.blocker.id);
      }
      spawnBlockedSparks(gridR, gridC, item.direction);
    } else {
      // Clear path! Rocket out along direction vector
      window.soundEngine.playTapGlass(1.0);
      grid[gridR][gridC] = null;
      launchCapsule(item, false);
    }
  }

  function triggerCapsuleShake(id) {
    state.shakingCapsules.set(id, { timer: 0.18, maxOffset: 8 });
  }

  // --- CAPSULE LAUNCH & DESTINATION ROUTING ---
  function launchCapsule(capsule, forceDirect = false) {
    const { x, y, cellSize } = state.boardOffset;
    const startX = x + capsule.c * cellSize + cellSize / 2;
    const startY = y + capsule.r * cellSize + cellSize / 2;

    const dir = window.DIRECTIONS[capsule.direction] || window.DIRECTIONS.UP;

    // First leg: Rocket to board edge
    let edgeX = startX + dir.dx * (viewWidth * 0.45);
    let edgeY = startY + dir.dy * (viewHeight * 0.45);
    if (forceDirect) {
      edgeX = startX;
      edgeY = startY - 30;
    }

    // Check matching active vial
    const matchingVial = state.levelData.vials.find(v => !v.completed && v.color === capsule.color);

    state.animatingCapsules.push({
      id: capsule.id,
      color: capsule.color,
      x: startX,
      y: startY,
      startX,
      startY,
      edgeX,
      edgeY,
      t: 0,
      phase: 'rocket', // 'rocket' -> 'route'
      matchingVial: matchingVial || null,
      duration: 0.22
    });
  }

  function routeCapsuleToDestination(anim) {
    const matchingVial = anim.matchingVial;

    if (matchingVial) {
      // Route to top target vial!
      const vialEl = document.getElementById(`ui_${matchingVial.id}`);
      let targetX = viewWidth / 2;
      let targetY = 100;
      if (vialEl) {
        const rect = vialEl.getBoundingClientRect();
        targetX = rect.left + rect.width / 2;
        targetY = rect.top + rect.height / 2;
      }

      anim.phase = 'arc';
      anim.startX = anim.edgeX;
      anim.startY = anim.edgeY;
      anim.targetX = targetX;
      anim.targetY = targetY;
      anim.t = 0;
      anim.duration = 0.28;
      anim.onComplete = () => {
        fillTargetVial(matchingVial, anim.color);
      };
    } else {
      // Route to Bottom 5-Slot Buffer Dock!
      if (state.bufferSlots.length >= state.bufferCapacity) {
        // Buffer is completely full! Instant defeat trigger
        triggerBufferOverflow();
        return;
      }

      const nextSlotIdx = state.bufferSlots.length;
      const slotEl = document.getElementById(`buffer_slot_${nextSlotIdx}`);
      let targetX = viewWidth / 2;
      let targetY = viewHeight - 110;
      if (slotEl) {
        const rect = slotEl.getBoundingClientRect();
        targetX = rect.left + rect.width / 2;
        targetY = rect.top + rect.height / 2;
      }

      anim.phase = 'arc';
      anim.startX = anim.edgeX;
      anim.startY = anim.edgeY;
      anim.targetX = targetX;
      anim.targetY = targetY;
      anim.t = 0;
      anim.duration = 0.28;
      anim.onComplete = () => {
        addCapsuleToBuffer(anim.color);
      };
    }
  }

  // --- VIAL FILL & REWARD LOGIC ---
  function fillTargetVial(vial, colorKey) {
    vial.current++;
    window.soundEngine.playLiquidPour(vial.current);
    spawnSplashParticles(viewWidth / 2, 90, colorKey);

    if (vial.current >= vial.target) {
      // Vial completed!
      vial.completed = true;
      window.soundEngine.playVialComplete();
      state.streak++;
      if (state.streak > state.maxStreak) state.maxStreak = state.streak;

      const earnedCoins = (25 + state.streak * 5) * state.upgrades.coinMultiplier;
      state.coins += Math.floor(earnedCoins);
      saveGameData();

      spawnRainbowParticles(viewWidth / 2, 100);
      showFloatingText(`+${earnedCoins} COINS!`, viewWidth / 2, 120, '#FFD700');

      // Check if any buffered capsules match new upcoming vials!
      setTimeout(() => {
        renderVialsHUD();
        checkBufferForNewVials();
        checkLevelWin();
      }, 250);
    } else {
      renderVialsHUD();
    }
    updateHUDCounters();
  }

  // --- BUFFER DOCK & PRISM MERGE LOGIC ---
  function addCapsuleToBuffer(colorKey) {
    const newItem = {
      id: `buf_${Date.now()}_${Math.random()}`,
      color: colorKey
    };
    state.bufferSlots.push(newItem);
    renderBufferHUD();

    // Check for unique PRISM MERGE (Red+Yellow, Blue+Yellow, Red+Blue)
    const mergeOccurred = checkPrismMerge();

    if (!mergeOccurred) {
      // Check if buffer overflowed
      if (state.bufferSlots.length >= state.bufferCapacity) {
        // Can any move be made on board?
        checkLossCondition();
      }
    }
  }

  function checkPrismMerge() {
    // Scan buffer for primary color combinations
    for (let i = 0; i < state.bufferSlots.length; i++) {
      for (let j = i + 1; j < state.bufferSlots.length; j++) {
        const c1 = state.bufferSlots[i].color;
        const c2 = state.bufferSlots[j].color;
        const comboResult = window.LevelGenerator.getPrismCombo(c1, c2);

        if (comboResult) {
          // Trigger PRISM MERGE!
          executePrismMerge(i, j, comboResult);
          return true;
        }
      }
    }
    return false;
  }

  function executePrismMerge(idx1, idx2, newColor) {
    const slot1 = document.getElementById(`buffer_slot_${idx1}`);
    const slot2 = document.getElementById(`buffer_slot_${idx2}`);
    if (slot1) slot1.classList.add('prism-merging');
    if (slot2) slot2.classList.add('prism-merging');

    if (slot1 && slot2) {
      const r1 = slot1.getBoundingClientRect();
      const r2 = slot2.getBoundingClientRect();
      state.mergingArcs.push({
        x1: r1.left + r1.width / 2,
        y1: r1.top + r1.height / 2,
        x2: r2.left + r2.width / 2,
        y2: r2.top + r2.height / 2,
        life: 0.45,
        maxLife: 0.45
      });
    }

    window.soundEngine.playPrismMerge();
    showFloatingText(`PRISM MERGE! -> ${newColor}`, viewWidth / 2, viewHeight - 160, '#FFD700');

    setTimeout(() => {
      // Remove the two items, insert single newColor item
      const item2 = state.bufferSlots[idx2];
      const item1 = state.bufferSlots[idx1];
      state.bufferSlots = state.bufferSlots.filter((_, idx) => idx !== idx1 && idx !== idx2);
      state.bufferSlots.push({ id: `prism_${Date.now()}`, color: newColor });

      renderBufferHUD();

      // Free buffer slot shockwave thaws 1 or more frozen ice blocks on board!
      thawIceWithShockwave();

      // Check if this newly merged secondary color matches an active vial!
      checkBufferForNewVials();
    }, 450);
  }

  function thawIceWithShockwave() {
    if (!state.levelData) return;
    const grid = state.levelData.grid;
    let thawed = 0;
    const maxThaw = state.upgrades.prismRadius;

    for (let r = 0; r < state.levelData.rows; r++) {
      for (let c = 0; c < state.levelData.cols; c++) {
        const item = grid[r][c];
        if (item && item.type === 'CAPSULE' && item.ice > 0) {
          item.ice = 0; // Thawed by shockwave!
          thawed++;
          spawnShockwave(r, c, '#FFD700');
          if (thawed >= maxThaw) break;
        }
      }
      if (thawed >= maxThaw) break;
    }
    if (thawed > 0) {
      showFloatingText(`ICE SHATTERED!`, viewWidth / 2, viewHeight / 2, '#00E5FF');
    }
  }

  function checkBufferForNewVials() {
    if (!state.levelData || state.bufferSlots.length === 0) return;

    for (let i = 0; i < state.bufferSlots.length; i++) {
      const item = state.bufferSlots[i];
      const matchingVial = state.levelData.vials.find(v => !v.completed && v.color === item.color);

      if (matchingVial) {
        // Remove from buffer and arc into matching vial!
        state.bufferSlots.splice(i, 1);
        renderBufferHUD();

        const slotEl = document.getElementById(`buffer_slot_${i}`);
        let startX = viewWidth / 2;
        let startY = viewHeight - 110;
        if (slotEl) {
          const rect = slotEl.getBoundingClientRect();
          startX = rect.left + rect.width / 2;
          startY = rect.top + rect.height / 2;
        }

        const vialEl = document.getElementById(`ui_${matchingVial.id}`);
        let targetX = viewWidth / 2;
        let targetY = 100;
        if (vialEl) {
          const rect = vialEl.getBoundingClientRect();
          targetX = rect.left + rect.width / 2;
          targetY = rect.top + rect.height / 2;
        }

        state.animatingCapsules.push({
          id: item.id,
          color: item.color,
          startX,
          startY,
          targetX,
          targetY,
          x: startX,
          y: startY,
          t: 0,
          phase: 'arc',
          duration: 0.28,
          onComplete: () => {
            fillTargetVial(matchingVial, item.color);
          }
        });
        return; // handle one at a time for smooth visuals
      }
    }
  }

  // --- WIN / LOSS CHECKS ---
  function checkLevelWin() {
    if (!state.levelData) return;
    const allCompleted = state.levelData.vials.every(v => v.completed);

    if (allCompleted && !state.isLevelWon) {
      state.isLevelWon = true;
      window.soundEngine.playVictory();

      // Bonus rewards
      const earnedCoins = state.levelData.rewardCoins;
      const earnedShards = state.levelData.rewardShards;
      state.coins += earnedCoins;
      state.shards += earnedShards;
      saveGameData();

      // Show Alchemist Victory Modal
      document.getElementById('win-coins-text').textContent = `+${earnedCoins} Coins`;
      document.getElementById('win-shards-text').textContent = `+${earnedShards} Shards`;

      setTimeout(() => {
        victoryModal.classList.add('open');
      }, 500);
    }
  }

  function checkLossCondition() {
    if (state.bufferSlots.length >= state.bufferCapacity && !state.isGameOver) {
      // Delay slightly to give player a moment to see buffer fill
      setTimeout(() => {
        if (state.bufferSlots.length >= state.bufferCapacity && !state.isGameOver && !state.isLevelWon) {
          triggerBufferOverflow();
        }
      }, 350);
    }
  }

  function triggerBufferOverflow() {
    state.isGameOver = true;
    window.soundEngine.playDefeat();
    overflowModal.classList.add('open');
  }

  // --- REWARDED AD RECOVERY SIMULATION ---
  function watchRewardedAdRecovery() {
    overflowModal.classList.remove('open');
    rewardedAdModal.classList.add('open');

    const progressBar = document.getElementById('ad-progress-bar');
    const adTimerText = document.getElementById('ad-timer-text');
    let timeLeft = 3;
    progressBar.style.width = '0%';

    const interval = setInterval(() => {
      timeLeft -= 0.1;
      const pct = Math.min(100, Math.floor(((3 - timeLeft) / 3) * 100));
      progressBar.style.width = `${pct}%`;
      adTimerText.textContent = `Resuming in ${Math.ceil(Math.max(0, timeLeft))}s...`;

      if (timeLeft <= 0) {
        clearInterval(interval);
        rewardedAdModal.classList.remove('open');

        // Apply Rewarded Perks:
        // 1. Expand Buffer capacity from 5 to 6
        state.bufferCapacity = 6;
        // 2. Clear 2 capsules from buffer!
        state.bufferSlots.splice(0, 2);
        state.isGameOver = false;

        window.soundEngine.playVictory();
        renderBufferHUD();
        showFloatingText(`BUFFER EXTENDED & CLEARED!`, viewWidth / 2, viewHeight / 2, '#00E5FF');
      }
    }, 100);
  }

  // --- BOOSTERS IMPLEMENTATION ---
  function useBooster(type) {
    if (state.isGameOver || state.isLevelWon) return;

    if (type === 'magnet') {
      if (state.boosters.magnet <= 0) return;
      state.boosters.magnet--;
      state.activeBooster = 'magnet';
      document.getElementById('btn-magnet').classList.add('active');
      window.soundEngine.playBooster();
      showFloatingText('TAP ANY CAPSULE TO EXTRACT!', viewWidth / 2, viewHeight / 2, '#00E5FF');
      updateHUDCounters();
      saveGameData();
    } else if (type === 'shuffle') {
      if (state.boosters.shuffle <= 0) return;
      state.boosters.shuffle--;
      window.soundEngine.playBooster();
      shuffleBoardDirections();
      showFloatingText('PRISM SHUFFLE!', viewWidth / 2, viewHeight / 2, '#FF2E93');
      updateHUDCounters();
      saveGameData();
    } else if (type === 'slot') {
      if (state.boosters.slot <= 0) return;
      state.boosters.slot--;
      state.bufferCapacity = 6;
      window.soundEngine.playBooster();
      renderBufferHUD();
      showFloatingText('+1 BUFFER SLOT UNLOCKED!', viewWidth / 2, viewHeight / 2, '#FFD700');
      updateHUDCounters();
      saveGameData();
    }
  }

  function shuffleBoardDirections() {
    if (!state.levelData) return;
    const grid = state.levelData.grid;
    const allDirs = ['UP', 'RIGHT', 'DOWN', 'LEFT'];

    for (let r = 0; r < state.levelData.rows; r++) {
      for (let c = 0; c < state.levelData.cols; c++) {
        const item = grid[r][c];
        if (item && item.type === 'CAPSULE') {
          item.direction = allDirs[Math.floor(Math.random() * allDirs.length)];
          triggerCapsuleShake(item.id);
        }
      }
    }
    // Guarantee outward openings
    window.LevelGenerator.ensureInitialOpenings(grid, state.levelData.rows, state.levelData.cols);
  }

  // --- PARTICLES & FLOATING COMBAT TEXT ---
  function spawnSplashParticles(x, y, colorKey) {
    const colorDef = window.COLORS[colorKey] || window.COLORS.RED;
    for (let i = 0; i < 18; i++) {
      const angle = Math.random() * Math.PI * 2;
      const speed = 40 + Math.random() * 90;
      state.particles.push({
        x, y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        r: 3 + Math.random() * 4,
        color: Math.random() < 0.5 ? colorDef.lightHex : colorDef.hex,
        alpha: 1,
        life: 0.45
      });
    }
  }

  function spawnRainbowParticles(x, y) {
    const rainbow = ['#FF3366', '#0088FF', '#FFCC00', '#00D664', '#AA33FF', '#00E5FF'];
    for (let i = 0; i < 30; i++) {
      const angle = Math.random() * Math.PI * 2;
      const speed = 60 + Math.random() * 120;
      state.particles.push({
        x, y,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed - 20,
        r: 4 + Math.random() * 5,
        color: rainbow[Math.floor(Math.random() * rainbow.length)],
        alpha: 1,
        life: 0.65
      });
    }
  }

  function spawnBlockedSparks(r, c, dirName) {
    const { x, y, cellSize } = state.boardOffset;
    const dir = window.DIRECTIONS[dirName];
    const px = x + c * cellSize + cellSize / 2 + dir.dx * (cellSize * 0.4);
    const py = y + r * cellSize + cellSize / 2 + dir.dy * (cellSize * 0.4);

    for (let i = 0; i < 12; i++) {
      state.particles.push({
        x: px, y: py,
        vx: (Math.random() - 0.5) * 80,
        vy: (Math.random() - 0.5) * 80,
        r: 2.5 + Math.random() * 2,
        color: '#FF2E93',
        alpha: 1,
        life: 0.25
      });
    }
  }

  function spawnIceCrackParticles(r, c) {
    const { x, y, cellSize } = state.boardOffset;
    const px = x + c * cellSize + cellSize / 2;
    const py = y + r * cellSize + cellSize / 2;
    for (let i = 0; i < 14; i++) {
      state.particles.push({
        x: px, y: py,
        vx: (Math.random() - 0.5) * 60,
        vy: (Math.random() - 0.5) * 60,
        r: 2 + Math.random() * 3,
        color: '#00E5FF',
        alpha: 1,
        life: 0.3
      });
    }
  }

  function spawnShockwave(r, c, hex) {
    const { x, y, cellSize } = state.boardOffset;
    const px = x + c * cellSize + cellSize / 2;
    const py = y + r * cellSize + cellSize / 2;
    state.particles.push({
      x: px, y: py,
      r: 10,
      maxR: 90,
      color: hex,
      isRing: true,
      alpha: 1,
      life: 0.4
    });
  }

  function showFloatingText(text, x, y, color = '#FFF') {
    state.particles.push({
      x, y,
      text,
      color,
      vy: -60,
      alpha: 1,
      life: 0.85,
      isText: true
    });
  }

  // --- FIXED DELTA-TIME RENDERING & UPDATE LOOP ---
  let lastTimestamp = performance.now();

  function gameLoop(timestamp) {
    const rawDelta = (timestamp - lastTimestamp) / 1000;
    lastTimestamp = timestamp;
    // Clamped to 0.05s as required by strict engineering rules
    const dt = Math.min(rawDelta, 0.05);

    update(dt);
    renderBackground(dt);
    renderGame(dt);

    requestAnimationFrame(gameLoop);
  }

  function update(dt) {
    state.sloshingPhase += dt * 3.5;

    // Update camera shake timers
    for (const [id, shake] of state.shakingCapsules.entries()) {
      shake.timer -= dt;
      if (shake.timer <= 0) {
        state.shakingCapsules.delete(id);
      }
    }

    // Update flashing obstacle timers
    for (const [id, timer] of state.flashingObstacles.entries()) {
      const next = timer - dt;
      if (next <= 0) state.flashingObstacles.delete(id);
      else state.flashingObstacles.set(id, next);
    }

    // Update merging golden arcs
    for (let i = state.mergingArcs.length - 1; i >= 0; i--) {
      state.mergingArcs[i].life -= dt;
      if (state.mergingArcs[i].life <= 0) state.mergingArcs.splice(i, 1);
    }

    // Update animating flying capsules
    for (let i = state.animatingCapsules.length - 1; i >= 0; i--) {
      const anim = state.animatingCapsules[i];
      anim.t += dt / anim.duration;

      if (anim.phase === 'rocket') {
        const ease = anim.t * anim.t;
        anim.x = anim.startX + (anim.edgeX - anim.startX) * ease;
        anim.y = anim.startY + (anim.edgeY - anim.startY) * ease;

        // Emit glowing liquid droplet trail
        if (Math.random() < 0.6) {
          const colorDef = window.COLORS[anim.color] || window.COLORS.RED;
          state.particles.push({
            x: anim.x,
            y: anim.y,
            vx: (Math.random() - 0.5) * 20,
            vy: (Math.random() - 0.5) * 20,
            r: 3 + Math.random() * 2,
            color: colorDef.lightHex,
            alpha: 0.8,
            life: 0.2
          });
        }

        if (anim.t >= 1) {
          routeCapsuleToDestination(anim);
        }
      } else if (anim.phase === 'arc') {
        // Quadratic bezier arc trajectory
        const t = Math.min(1, anim.t);
        const midY = Math.min(anim.startY, anim.targetY) - 50;
        const midX = (anim.startX + anim.targetX) / 2;

        const invT = 1 - t;
        anim.x = invT * invT * anim.startX + 2 * invT * t * midX + t * t * anim.targetX;
        anim.y = invT * invT * anim.startY + 2 * invT * t * midY + t * t * anim.targetY;

        if (anim.t >= 1) {
          state.animatingCapsules.splice(i, 1);
          if (anim.onComplete) anim.onComplete();
        }
      }
    }

    // Update particles
    for (let i = state.particles.length - 1; i >= 0; i--) {
      const p = state.particles[i];
      p.life -= dt;
      if (p.life <= 0) {
        state.particles.splice(i, 1);
        continue;
      }
      p.alpha = Math.max(0, p.life / 0.5);

      if (p.isRing) {
        p.r += dt * 180;
      } else if (p.isText) {
        p.y += p.vy * dt;
      } else {
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        p.vy += 80 * dt; // gravity
      }
    }
  }

  // --- BACKGROUND RENDERING ---
  function renderBackground(dt) {
    bgCtx.clearRect(0, 0, viewWidth, viewHeight);

    // Update orbs
    bgOrbs.forEach(orb => {
      orb.x += orb.vx * dt;
      orb.y += orb.vy * dt;
      if (orb.x < 0.1 || orb.x > 0.9) orb.vx *= -1;
      if (orb.y < 0.1 || orb.y > 0.9) orb.vy *= -1;

      const px = orb.x * viewWidth;
      const py = orb.y * viewHeight;

      const grad = bgCtx.createRadialGradient(px, py, 10, px, py, orb.r);
      grad.addColorStop(0, orb.color);
      grad.addColorStop(1, 'rgba(0,0,0,0)');

      bgCtx.fillStyle = grad;
      bgCtx.beginPath();
      bgCtx.arc(px, py, orb.r, 0, Math.PI * 2);
      bgCtx.fill();
    });
  }

  // --- GAME BOARD RENDERING ---
  function renderGame() {
    ctx.clearRect(0, 0, viewWidth, viewHeight);
    if (!state.levelData) return;

    const { rows, cols, grid } = state.levelData;
    const { x, y, cellSize, width, height } = state.boardOffset;

    // Draw Glass Board Plate
    ctx.save();
    ctx.fillStyle = 'rgba(255, 255, 255, 0.03)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
    ctx.lineWidth = 2;
    drawRoundedRect(ctx, x - 8, y - 8, width + 16, height + 16, 18);
    ctx.fill();
    ctx.stroke();

    // Draw subtle grid cell outlines
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const cx = x + c * cellSize;
        const cy = y + r * cellSize;
        ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
        ctx.lineWidth = 1;
        drawRoundedRect(ctx, cx + 2, cy + 2, cellSize - 4, cellSize - 4, 10);
        ctx.stroke();
      }
    }
    ctx.restore();

    // Draw Capsules and Obstacles
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const item = grid[r][c];
        if (!item) continue;

        let shakeX = 0;
        if (state.shakingCapsules.has(item.id)) {
          const shake = state.shakingCapsules.get(item.id);
          shakeX = Math.sin(shake.timer * 60) * shake.maxOffset;
        }

        const cx = x + c * cellSize + shakeX;
        const cy = y + r * cellSize;

        if (item.type === 'OBSTACLE') {
          drawObsidianBlock(ctx, cx + 3, cy + 3, cellSize - 6);
        } else if (item.type === 'CAPSULE') {
          drawLiquidCapsule(ctx, cx + 3, cy + 3, cellSize - 6, item);
        }
      }
    }

    // Draw Flying Animating Capsules
    state.animatingCapsules.forEach(anim => {
      const colorDef = window.COLORS[anim.color] || window.COLORS.RED;
      const size = state.boardOffset.cellSize * 0.85;
      drawLiquidSphere(ctx, anim.x, anim.y, size / 2, colorDef);
    });

    // Draw Golden Merging Arcs
    state.mergingArcs.forEach(arc => {
      ctx.save();
      const alpha = Math.max(0, arc.life / arc.maxLife);
      ctx.strokeStyle = `rgba(255, 215, 0, ${alpha})`;
      ctx.shadowColor = '#FFD700';
      ctx.shadowBlur = 16;
      ctx.lineWidth = 3.5;
      ctx.beginPath();
      ctx.moveTo(arc.x1, arc.y1);
      const midX = (arc.x1 + arc.x2) / 2;
      const midY = Math.min(arc.y1, arc.y2) - 25 + (Math.random() - 0.5) * 8;
      ctx.quadraticCurveTo(midX, midY, arc.x2, arc.y2);
      ctx.stroke();
      ctx.restore();
    });

    // Draw Particles
    state.particles.forEach(p => {
      ctx.save();
      ctx.globalAlpha = p.alpha;
      if (p.isText) {
        ctx.font = 'bold 15px -apple-system, sans-serif';
        ctx.fillStyle = p.color;
        ctx.textAlign = 'center';
        ctx.shadowColor = 'rgba(0,0,0,0.8)';
        ctx.shadowBlur = 4;
        ctx.fillText(p.text, p.x, p.y);
      } else if (p.isRing) {
        ctx.strokeStyle = p.color;
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.stroke();
      } else {
        ctx.fillStyle = p.color;
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.restore();
    });
  }

  // --- PROCEDURAL LIQUID CAPSULE VISUALS ---
  function drawLiquidCapsule(ctx, x, y, size, capsule) {
    const colorDef = window.COLORS[capsule.color] || window.COLORS.RED;
    const radius = size * 0.28;

    ctx.save();

    // Outer Glass Capsule Body
    ctx.fillStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.35)';
    ctx.lineWidth = 1.5;
    drawRoundedRect(ctx, x, y, size, size, radius);
    ctx.fill();
    ctx.stroke();

    // Clip inner liquid area
    ctx.save();
    drawRoundedRect(ctx, x + 2, y + 2, size - 4, size - 4, radius - 2);
    ctx.clip();

    // Liquid fill with animated sine wave slosh
    const waveOffset = Math.sin(state.sloshingPhase + (capsule.r + capsule.c)) * 2.5;
    const liquidGrad = ctx.createLinearGradient(x, y, x, y + size);
    liquidGrad.addColorStop(0, colorDef.lightHex);
    liquidGrad.addColorStop(0.5, colorDef.hex);
    liquidGrad.addColorStop(1, colorDef.darkHex);

    ctx.fillStyle = liquidGrad;
    ctx.beginPath();
    ctx.moveTo(x + 2, y + size * 0.2 + waveOffset);
    ctx.bezierCurveTo(
      x + size * 0.35, y + size * 0.2 - waveOffset,
      x + size * 0.65, y + size * 0.2 + waveOffset,
      x + size - 2, y + size * 0.2 - waveOffset
    );
    ctx.lineTo(x + size - 2, y + size - 2);
    ctx.lineTo(x + 2, y + size - 2);
    ctx.closePath();
    ctx.fill();

    // Liquid meniscus surface glow
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.7)';
    ctx.lineWidth = 1.2;
    ctx.stroke();

    ctx.restore(); // end clip

    // Direction Chevron (Glowing White Specular Arrow)
    drawDirectionChevron(ctx, x + size / 2, y + size / 2, size * 0.3, capsule.direction);

    // Specular Rim Highlight (Top-left glass reflection)
    ctx.save();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.85)';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.arc(x + radius + 2, y + radius + 2, radius * 0.7, Math.PI, 1.5 * Math.PI);
    ctx.stroke();
    ctx.restore();

    // Frozen Ice State Overlay
    if (capsule.ice > 0) {
      ctx.save();
      ctx.fillStyle = 'rgba(0, 229, 255, 0.35)';
      ctx.strokeStyle = '#FFFFFF';
      ctx.lineWidth = 1.8;
      drawRoundedRect(ctx, x + 1, y + 1, size - 2, size - 2, radius);
      ctx.fill();
      ctx.stroke();

      // Frost Crack details
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.85)';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.moveTo(x + size * 0.2, y + size * 0.3);
      ctx.lineTo(x + size * 0.5, y + size * 0.5);
      ctx.lineTo(x + size * 0.8, y + size * 0.35);
      ctx.moveTo(x + size * 0.5, y + size * 0.5);
      ctx.lineTo(x + size * 0.4, y + size * 0.8);
      ctx.stroke();

      // Ice badge
      ctx.font = 'bold 10px -apple-system, sans-serif';
      ctx.fillStyle = '#FFFFFF';
      ctx.textAlign = 'center';
      ctx.fillText(`❄ ${capsule.ice}`, x + size / 2, y + size * 0.88);
      ctx.restore();
    // Red Specular Rim Highlight if this capsule is blocking another
    if (state.flashingObstacles.has(capsule.id)) {
      ctx.save();
      ctx.strokeStyle = '#FF1744';
      ctx.shadowColor = '#FF1744';
      ctx.shadowBlur = 14;
      ctx.lineWidth = 2.5;
      drawRoundedRect(ctx, x - 1, y - 1, size + 2, size + 2, radius + 1);
      ctx.stroke();
      ctx.restore();
    }

    ctx.restore();
  }

  function drawDirectionChevron(ctx, cx, cy, len, dirName) {
    const dir = window.DIRECTIONS[dirName] || window.DIRECTIONS.UP;
    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate((dir.angle * Math.PI) / 180);

    // Outer glow
    ctx.shadowColor = 'rgba(255, 255, 255, 0.9)';
    ctx.shadowBlur = 6;

    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    ctx.beginPath();
    ctx.moveTo(-len * 0.5, len * 0.2);
    ctx.lineTo(0, -len * 0.4);
    ctx.lineTo(len * 0.5, len * 0.2);
    ctx.stroke();

    ctx.restore();
  }

  function drawObsidianBlock(ctx, x, y, size) {
    ctx.save();
    const grad = ctx.createLinearGradient(x, y, x + size, y + size);
    grad.addColorStop(0, '#2D3748');
    grad.addColorStop(0.5, '#1A202C');
    grad.addColorStop(1, '#0F131A');

    ctx.fillStyle = grad;
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.2)';
    ctx.lineWidth = 2;
    drawRoundedRect(ctx, x, y, size, size, 8);
    ctx.fill();
    ctx.stroke();

    // Geometric facet lines
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.15)';
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x + size, y + size);
    ctx.moveTo(x + size, y);
    ctx.lineTo(x, y + size);
    ctx.stroke();

    // Lock icon / stone symbol
    ctx.font = '14px sans-serif';
    ctx.fillStyle = 'rgba(255,255,255,0.6)';
    ctx.textAlign = 'center';
    ctx.fillText('◆', x + size / 2, y + size / 2 + 5);

    // Red Specular Rim Highlight if this obsidian block is blocking a capsule
    if (state.flashingObstacles.has(item.id)) {
      ctx.save();
      ctx.strokeStyle = '#FF1744';
      ctx.shadowColor = '#FF1744';
      ctx.shadowBlur = 14;
      ctx.lineWidth = 2.5;
      drawRoundedRect(ctx, x - 1, y - 1, size + 2, size + 2, 9);
      ctx.stroke();
      ctx.restore();
    }

    ctx.restore();
  }

  function drawLiquidSphere(ctx, cx, cy, r, colorDef) {
    ctx.save();
    const grad = ctx.createRadialGradient(cx - r * 0.35, cy - r * 0.35, r * 0.1, cx, cy, r);
    grad.addColorStop(0, colorDef.lightHex);
    grad.addColorStop(0.65, colorDef.hex);
    grad.addColorStop(1, colorDef.darkHex);

    ctx.fillStyle = grad;
    ctx.shadowColor = colorDef.hex;
    ctx.shadowBlur = 12;
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.fill();

    // Specular glint
    ctx.fillStyle = '#FFFFFF';
    ctx.beginPath();
    ctx.arc(cx - r * 0.3, cy - r * 0.3, r * 0.25, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }

  function drawRoundedRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  // --- TOUCH & POINTER INPUT HANDLING ---
  function onPointerDown(e) {
    // Unlock Web Audio immediately
    window.soundEngine.ensureContext();

    const rect = gameCanvas.getBoundingClientRect();
    const clientX = e.clientX || (e.touches && e.touches[0].clientX);
    const clientY = e.clientY || (e.touches && e.touches[0].clientY);
    if (clientX === undefined || clientY === undefined) return;

    const x = clientX - rect.left;
    const y = clientY - rect.top;

    const { x: bx, y: by, cellSize } = state.boardOffset;
    if (!state.levelData || cellSize === 0) return;

    if (x >= bx && x <= bx + state.boardOffset.width &&
        y >= by && y <= by + state.boardOffset.height) {
      const col = Math.floor((x - bx) / cellSize);
      const row = Math.floor((y - by) / cellSize);
      if (row >= 0 && row < state.levelData.rows && col >= 0 && col < state.levelData.cols) {
        handleCellClick(row, col);
      }
    }
  }

  // --- MODALS & NAVIGATION ---
  function closeAllModals() {
    victoryModal.classList.remove('open');
    overflowModal.classList.remove('open');
    rewardedAdModal.classList.remove('open');
    labModal.classList.remove('open');
    themesModal.classList.remove('open');
  }

  function openTab(tabName) {
    closeAllModals();
    state.activeMode = tabName;

    document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
    document.getElementById(`tab-${tabName.toLowerCase()}`).classList.add('active');

    if (tabName === 'LAB') {
      renderLabScreen();
      labModal.classList.add('open');
    } else if (tabName === 'THEMES') {
      renderThemesScreen();
      themesModal.classList.add('open');
    }
  }

  function renderLabScreen() {
    document.getElementById('lab-coins-avail').textContent = `${state.coins} Coins`;
    document.getElementById('lvl-transmuter').textContent = `Lv. ${state.upgrades.coinMultiplier}`;
    document.getElementById('lvl-catalyst').textContent = `Lv. ${state.upgrades.streakBonus}`;
    document.getElementById('lvl-shockwave').textContent = `Lv. ${state.upgrades.prismRadius}`;
  }

  function buyUpgrade(type) {
    const costs = {
      coinMultiplier: 100 * state.upgrades.coinMultiplier,
      streakBonus: 120 * (state.upgrades.streakBonus + 1),
      prismRadius: 150 * state.upgrades.prismRadius
    };

    const cost = costs[type] || 100;
    if (state.coins >= cost) {
      state.coins -= cost;
      state.upgrades[type]++;
      window.soundEngine.playBooster();
      saveGameData();
      renderLabScreen();
      updateHUDCounters();
      showFloatingText(`UPGRADED!`, viewWidth / 2, viewHeight / 2, '#00E5FF');
    } else {
      window.soundEngine.playBlocked();
      showFloatingText(`NEED ${cost} COINS!`, viewWidth / 2, viewHeight / 2, '#FF2E93');
    }
  }

  function renderThemesScreen() {
    document.querySelectorAll('.theme-card').forEach(card => {
      const themeId = card.getAttribute('data-theme');
      if (themeId === state.currentTheme) {
        card.classList.add('active');
      } else {
        card.classList.remove('active');
      }
    });
  }

  function selectTheme(themeId) {
    state.currentTheme = themeId;
    document.body.setAttribute('data-theme', themeId);
    window.soundEngine.playTapGlass(1.2);
    saveGameData();
    renderThemesScreen();
  }

  // --- HARDWARE & RESUME LIFECYCLE HOOKS ---
  function setupHardwareHooks() {
    // Web Audio Unlock on touch
    window.addEventListener('touchstart', () => window.soundEngine.ensureContext(), { passive: true });
    window.addEventListener('pointerdown', () => window.soundEngine.ensureContext(), { passive: true });

    // WebGL / Canvas context lost recovery
    gameCanvas.addEventListener('webglcontextlost', (e) => {
      e.preventDefault();
      console.warn('Context lost, pausing');
    }, false);

    gameCanvas.addEventListener('webglcontextrestored', () => {
      console.info('Context restored, resuming');
      resizeCanvases();
    }, false);

    window.addEventListener('resize', resizeCanvases);
    gameCanvas.addEventListener('pointerdown', onPointerDown);
  }

  // --- EVENT LISTENERS INITIALIZATION ---
  function initEventBindings() {
    // Boosters
    document.getElementById('btn-magnet').addEventListener('click', () => useBooster('magnet'));
    document.getElementById('btn-shuffle').addEventListener('click', () => useBooster('shuffle'));
    document.getElementById('btn-slot').addEventListener('click', () => useBooster('slot'));

    // Navigation Tabs
    document.getElementById('tab-play').addEventListener('click', () => openTab('PLAY'));
    document.getElementById('tab-lab').addEventListener('click', () => openTab('LAB'));
    document.getElementById('tab-themes').addEventListener('click', () => openTab('THEMES'));

    // Victory Next Level
    document.getElementById('btn-next-level').addEventListener('click', () => {
      startLevel(state.levelNum + 1);
      saveGameData();
    });

    // Overflow / Loss Modals
    document.getElementById('btn-watch-ad').addEventListener('click', watchRewardedAdRecovery);
    document.getElementById('btn-restart-level').addEventListener('click', () => {
      startLevel(state.levelNum);
    });

    // Lab upgrade buttons
    document.getElementById('btn-up-transmuter').addEventListener('click', () => buyUpgrade('coinMultiplier'));
    document.getElementById('btn-up-catalyst').addEventListener('click', () => buyUpgrade('streakBonus'));
    document.getElementById('btn-up-shockwave').addEventListener('click', () => buyUpgrade('prismRadius'));

    // Lab close
    document.getElementById('btn-close-lab').addEventListener('click', () => openTab('PLAY'));
    document.getElementById('btn-close-themes').addEventListener('click', () => openTab('PLAY'));

    // Themes selection
    document.querySelectorAll('.theme-card').forEach(card => {
      card.addEventListener('click', () => {
        const themeId = card.getAttribute('data-theme');
        selectTheme(themeId);
      });
    });

    // Top buttons: Sound toggle & Restart
    document.getElementById('btn-sound-toggle').addEventListener('click', () => {
      const on = window.soundEngine.toggleSound();
      document.getElementById('btn-sound-toggle').textContent = on ? '🔊' : '🔇';
    });

    document.getElementById('btn-theme-quick').addEventListener('click', () => openTab('THEMES'));
  }

  // --- BOOTSTRAP GAME ---
  function init() {
    loadSavedData();
    document.body.setAttribute('data-theme', state.currentTheme);
    setupHardwareHooks();
    initEventBindings();
    resizeCanvases();
    startLevel(state.levelNum);

    requestAnimationFrame(gameLoop);
  }

  window.addEventListener('DOMContentLoaded', init);

  // Expose global for debugging if needed
  window.chromaGame = { state, startLevel };
})();
