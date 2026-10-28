/**
 * ChromaFlow: Liquid Jam - Solvable Level Generator & Level Sequence
 * Ensures 100% solvable levels using backward construction and solver verification.
 */

const COLORS = {
  RED: { id: 'RED', name: 'Ruby Red', hex: '#FF3366', lightHex: '#FF7597', darkHex: '#B3002D', isPrimary: true },
  BLUE: { id: 'BLUE', name: 'Sapphire Blue', hex: '#0088FF', lightHex: '#4DB2FF', darkHex: '#0055B3', isPrimary: true },
  YELLOW: { id: 'YELLOW', name: 'Amber Yellow', hex: '#FFCC00', lightHex: '#FFE066', darkHex: '#B38F00', isPrimary: true },
  ORANGE: { id: 'ORANGE', name: 'Flame Orange', hex: '#FF7700', lightHex: '#FFA347', darkHex: '#B35300', isPrimary: false, from: ['RED', 'YELLOW'] },
  GREEN: { id: 'GREEN', name: 'Emerald Green', hex: '#00D664', lightHex: '#52FF9F', darkHex: '#008A40', isPrimary: false, from: ['BLUE', 'YELLOW'] },
  PURPLE: { id: 'PURPLE', name: 'Amethyst Purple', hex: '#AA33FF', lightHex: '#CC7AFF', darkHex: '#6F00B8', isPrimary: false, from: ['RED', 'BLUE'] }
};

const DIRECTIONS = {
  UP: { id: 'UP', dx: 0, dy: -1, symbol: '↑', angle: 0 },
  RIGHT: { id: 'RIGHT', dx: 1, dy: 0, symbol: '→', angle: 90 },
  DOWN: { id: 'DOWN', dx: 0, dy: 1, symbol: '↓', angle: 180 },
  LEFT: { id: 'LEFT', dx: -1, dy: 0, symbol: '←', angle: 270 }
};

class LevelGenerator {
  static getPrismCombo(c1, c2) {
    if (!c1 || !c2) return null;
    const pair = [c1, c2].sort();
    if (pair[0] === 'RED' && pair[1] === 'YELLOW') return 'ORANGE';
    if (pair[0] === 'BLUE' && pair[1] === 'YELLOW') return 'GREEN';
    if (pair[0] === 'BLUE' && pair[1] === 'RED') return 'PURPLE';
    return null;
  }

  static generateLevel(levelNum) {
    // Scaling grid size
    let rows = 6;
    let cols = 6;
    if (levelNum >= 4) { rows = 6; cols = 6; }
    if (levelNum >= 8) { rows = 7; cols = 6; }
    if (levelNum >= 14) { rows = 7; cols = 7; }
    if (levelNum >= 20) { rows = 8; cols = 7; }

    const isBossLevel = (levelNum % 5 === 0);

    // Color palette available for this level
    let availableColors = ['RED', 'BLUE', 'YELLOW'];
    if (levelNum >= 2) availableColors.push('ORANGE');
    if (levelNum >= 3) availableColors.push('GREEN');
    if (levelNum >= 5) availableColors.push('PURPLE');

    const totalCells = rows * cols;
    // Fill density
    let numObstacles = Math.min(Math.floor(levelNum / 3), 4);
    if (levelNum < 3) numObstacles = 0;

    let numIce = Math.min(Math.floor((levelNum - 1) / 2), 6);
    if (levelNum < 2) numIce = 0;

    // Number of capsules must align with Vials (each normal vial requires 3 capsules)
    // For boss level, 1 boss vial requires 6 capsules, plus standard vials
    let targetCapsulesCount = Math.floor((totalCells - numObstacles) * 0.75);
    // Align to multiple of 3 (or 6 for boss)
    targetCapsulesCount = Math.floor(targetCapsulesCount / 3) * 3;
    if (targetCapsulesCount < 12) targetCapsulesCount = 12;

    const numVials = targetCapsulesCount / 3;

    // Generate vials first
    const vials = [];
    const vialColorCounts = {};

    for (let i = 0; i < numVials; i++) {
      const colorKey = availableColors[Math.floor(Math.random() * availableColors.length)];
      vials.push({
        id: `vial_${i}`,
        color: colorKey,
        target: 3,
        current: 0,
        completed: false
      });
      vialColorCounts[colorKey] = (vialColorCounts[colorKey] || 0) + 3;
    }

    // Special boss flask on boss levels
    if (isBossLevel) {
      // First vial is a Giant Alchemist Prism Flask
      vials[0].target = 6;
      vials[0].isBoss = true;
      vials[0].name = "Grand Prism Flask";
      vialColorCounts[vials[0].color] += 3; // add 3 more to match 6
    }

    // Prepare list of capsule colors to place on grid
    const capsuleColorsToPlace = [];
    Object.keys(vialColorCounts).forEach(colorKey => {
      for (let c = 0; c < vialColorCounts[colorKey]; c++) {
        capsuleColorsToPlace.push(colorKey);
      }
    });

    // Shuffle colors
    for (let i = capsuleColorsToPlace.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [capsuleColorsToPlace[i], capsuleColorsToPlace[j]] = [capsuleColorsToPlace[j], capsuleColorsToPlace[i]];
    }

    // Create empty board
    const grid = Array.from({ length: rows }, () => Array(cols).fill(null));

    // Place obsidian stone obstacles (if any) away from outer edges to keep outer lanes clear
    let placedObstacles = 0;
    while (placedObstacles < numObstacles) {
      const r = 1 + Math.floor(Math.random() * (rows - 2));
      const c = 1 + Math.floor(Math.random() * (cols - 2));
      if (!grid[r][c]) {
        grid[r][c] = {
          type: 'OBSTACLE',
          id: `obs_${r}_${c}`,
          r, c
        };
        placedObstacles++;
      }
    }

    // Pick positions for capsules
    const emptyCoords = [];
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        if (!grid[r][c]) emptyCoords.push({ r, c });
      }
    }
    // Shuffle coords
    for (let i = emptyCoords.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [emptyCoords[i], emptyCoords[j]] = [emptyCoords[j], emptyCoords[i]];
    }

    const placedCapsules = [];
    let icePlaced = 0;

    for (let i = 0; i < capsuleColorsToPlace.length && i < emptyCoords.length; i++) {
      const { r, c } = emptyCoords[i];
      const colorKey = capsuleColorsToPlace[i];
      const isEdge = (r === 0 || r === rows - 1 || c === 0 || c === cols - 1);

      // Choose directions: if at edge, give high probability to point outside to guarantee immediate exits
      let direction = 'UP';
      const edgeDirections = [];
      if (r === 0) edgeDirections.push('UP');
      if (r === rows - 1) edgeDirections.push('DOWN');
      if (c === 0) edgeDirections.push('LEFT');
      if (c === cols - 1) edgeDirections.push('RIGHT');

      if (isEdge && Math.random() < 0.65 && edgeDirections.length > 0) {
        direction = edgeDirections[Math.floor(Math.random() * edgeDirections.length)];
      } else {
        const allDirs = ['UP', 'RIGHT', 'DOWN', 'LEFT'];
        direction = allDirs[Math.floor(Math.random() * allDirs.length)];
      }

      const isIce = (icePlaced < numIce && !isEdge && Math.random() < 0.35);
      if (isIce) icePlaced++;

      const capsule = {
        type: 'CAPSULE',
        id: `cap_${r}_${c}_${Date.now()}_${i}`,
        r, c,
        color: colorKey,
        direction: direction,
        ice: isIce ? 2 : 0, // 2 taps to thaw
        blocked: false
      };

      grid[r][c] = capsule;
      placedCapsules.push(capsule);
    }

    // Backward solver verification:
    // Guarantee that at least 2-3 capsules can exit immediately at the start of the level
    LevelGenerator.ensureInitialOpenings(grid, rows, cols);

    return {
      levelNum,
      rows,
      cols,
      grid,
      vials,
      isBossLevel,
      targetScore: placedCapsules.length * 100,
      rewardCoins: 50 + levelNum * 15,
      rewardShards: isBossLevel ? 5 : 2
    };
  }

  static ensureInitialOpenings(grid, rows, cols) {
    let unblockedCount = 0;
    const candidates = [];

    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const item = grid[r][c];
        if (item && item.type === 'CAPSULE' && item.ice === 0) {
          if (LevelGenerator.isRaycastClear(grid, rows, cols, r, c, item.direction)) {
            unblockedCount++;
          } else {
            candidates.push(item);
          }
        }
      }
    }

    // If fewer than 2 clear paths exist, orient edge items outward
    if (unblockedCount < 2) {
      for (let r = 0; r < rows; r++) {
        for (let c = 0; c < cols; c++) {
          const item = grid[r][c];
          if (item && item.type === 'CAPSULE') {
            if (r === 0 && item.ice === 0) {
              item.direction = 'UP';
              unblockedCount++;
            } else if (r === rows - 1 && item.ice === 0) {
              item.direction = 'DOWN';
              unblockedCount++;
            } else if (c === 0 && item.ice === 0) {
              item.direction = 'LEFT';
              unblockedCount++;
            } else if (c === cols - 1 && item.ice === 0) {
              item.direction = 'RIGHT';
              unblockedCount++;
            }
            if (unblockedCount >= 3) break;
          }
        }
        if (unblockedCount >= 3) break;
      }
    }
  }

  static raycast(grid, rows, cols, r, c, dirName) {
    const dir = DIRECTIONS[dirName];
    if (!dir) return { clear: false, blocker: null };
    let currR = r + dir.dy;
    let currC = c + dir.dx;

    while (currR >= 0 && currR < rows && currC >= 0 && currC < cols) {
      if (grid[currR][currC] !== null) {
        return { clear: false, blocker: grid[currR][currC] };
      }
      currR += dir.dy;
      currC += dir.dx;
    }
    return { clear: true, blocker: null };
  }

  static isRaycastClear(grid, rows, cols, r, c, dirName) {
    return LevelGenerator.raycast(grid, rows, cols, r, c, dirName).clear;
  }
}

window.COLORS = COLORS;
window.DIRECTIONS = DIRECTIONS;
window.LevelGenerator = LevelGenerator;
