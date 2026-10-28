with open('index.html', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Update Grid.raycast
old_ray = """      raycast(r, c, dirName) {
        const dir = DIRECTIONS[dirName];
        if (!dir) return { clear: false, blocker: null };
        let currR = r + dir.dy;
        let currC = c + dir.dx;
        while (currR >= 0 && currR < this.rows && currC >= 0 && currC < this.cols) {
          if (this.cells[currR][currC] !== null) {
            return { clear: false, blocker: this.cells[currR][currC] };
          }
          currR += dir.dy;
          currC += dir.dx;
        }
        return { clear: true, blocker: null };
      }"""

new_ray = """      raycast(r, c, dirName, capsuleColor = null) {
        const dir = DIRECTIONS[dirName];
        if (!dir) return { clear: false, blocker: null };
        let currR = r + dir.dy;
        let currC = c + dir.dx;
        while (currR >= 0 && currR < this.rows && currC >= 0 && currC < this.cols) {
          const item = this.cells[currR][currC];
          if (item !== null) {
            if (item.type === 'GATE') {
              if (capsuleColor && item.gateColor === capsuleColor) {
                // Allowed to pass through matching Color-Lock Gate!
              } else {
                return { clear: false, blocker: item };
              }
            } else {
              return { clear: false, blocker: item };
            }
          }
          currR += dir.dy;
          currC += dir.dx;
        }
        return { clear: true, blocker: null };
      }"""

assert old_ray in content, 'old_ray not found'
content = content.replace(old_ray, new_ray, 1)

# 2. Update LevelGenerator.generateLevel grid size
old_gen = """      static generateLevel(levelNum) {
        let rows = 6, cols = 6;
        if (levelNum >= 8) { rows = 7; cols = 6; }
        if (levelNum >= 14) { rows = 7; cols = 7; }
        if (levelNum >= 20) { rows = 8; cols = 7; }"""

new_gen = """      static generateLevel(levelNum) {
        // Increase grid size to 8x8 starting from Level 3
        let rows = 6, cols = 6;
        if (levelNum >= 3) { rows = 8; cols = 8; }"""

assert old_gen in content, 'old_gen not found'
content = content.replace(old_gen, new_gen, 1)

# 3. Add Color-Lock Gates in LevelGenerator
old_obs = """        for (let attempt = 0; attempt < 30 && placedObstacles < numObstacles; attempt++) {
          const r = 1 + Math.floor(Math.random() * (rows - 2));
          const c = 1 + Math.floor(Math.random() * (cols - 2));
          if (!gridObj.cells[r][c]) {
            gridObj.cells[r][c] = { type: 'OBSTACLE', id: `obs_${r}_${c}`, r, c };
            placedObstacles++;
          }
        }"""

new_obs = """        for (let attempt = 0; attempt < 30 && placedObstacles < numObstacles; attempt++) {
          const r = 1 + Math.floor(Math.random() * (rows - 2));
          const c = 1 + Math.floor(Math.random() * (cols - 2));
          if (!gridObj.cells[r][c]) {
            gridObj.cells[r][c] = { type: 'OBSTACLE', id: `obs_${r}_${c}`, r, c };
            placedObstacles++;
          }
        }

        // Color-Lock Gates: only allow capsules of matching color to pass through
        const gateCount = levelNum >= 2 ? Math.min(3, Math.floor(levelNum / 2)) : 0;
        let gatesPlaced = 0;
        for (let attempt = 0; attempt < 40 && gatesPlaced < gateCount; attempt++) {
          const r = 1 + Math.floor(Math.random() * (rows - 2));
          const c = 1 + Math.floor(Math.random() * (cols - 2));
          if (!gridObj.cells[r][c] && !gridObj.isConveyor(r, c)) {
            const gColor = availableColors[Math.floor(Math.random() * availableColors.length)];
            gridObj.cells[r][c] = {
              type: 'GATE',
              id: `gate_${r}_${c}`,
              r,
              c,
              gateColor: gColor
            };
            gatesPlaced++;
          }
        }"""

assert old_obs in content, 'old_obs not found'
content = content.replace(old_obs, new_obs, 1)

# 4. Inward candidate raycast through Color-Lock Gates
old_slide = """            for (let e = 0; e < edgeStarts.length; e++) {
              let cr = edgeStarts[e].r;
              let cc = edgeStarts[e].c;
              let depth = 0;
              while (gridObj.inBounds(cr, cc) && gridObj.cells[cr][cc] === null) {
                candidates.push({ r: cr, c: cc, exitDir, depth });
                cr -= vec.dy;
                cc -= vec.dx;
                depth++;
              }
            }"""

new_slide = """            for (let e = 0; e < edgeStarts.length; e++) {
              let cr = edgeStarts[e].r;
              let cc = edgeStarts[e].c;
              let depth = 0;
              while (gridObj.inBounds(cr, cc)) {
                const cell = gridObj.cells[cr][cc];
                if (cell === null) {
                  candidates.push({ r: cr, c: cc, exitDir, depth });
                  cr -= vec.dy;
                  cc -= vec.dx;
                  depth++;
                } else if (cell.type === 'GATE' && cell.gateColor === colorKey) {
                  // Ray passes through matching Color-Lock Gate!
                  cr -= vec.dy;
                  cc -= vec.dx;
                  depth++;
                } else {
                  break;
                }
              }
            }"""

assert old_slide in content, 'old_slide not found'
content = content.replace(old_slide, new_slide, 1)

# 5. Update state upgrades and move limit
old_state = """      upgrades: {
        coinMultiplier: 1,
        streakBonus: 0,
        prismRadius: 1
      },"""

new_state = """      upgrades: {
        coinMultiplier: 1,
        streakBonus: 0,
        bufferCapacity: 0
      },
      movesRemaining: 30,
      maxMoves: 30,"""

assert old_state in content, 'old_state not found'
content = content.replace(old_state, new_state, 1)

with open('index.html', 'w', encoding='utf-8') as f:
    f.write(content)

print('Patch applied successfully!')
