/**
 * ChromaFlow: Liquid Jam - Procedural ASMR Sound Synthesizer
 * Built using native Web Audio API with zero external audio assets.
 */
class SoundEngine {
  constructor() {
    this.ctx = null;
    this.masterGain = null;
    this.enabled = true;
    this.hapticsEnabled = true;
    this._initialized = false;
  }

  init() {
    if (this._initialized) return;
    try {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      if (!AudioContext) return;
      this.ctx = new AudioContext();
      this.masterGain = this.ctx.createGain();
      this.masterGain.gain.setValueAtTime(0.7, this.ctx.currentTime);
      this.masterGain.connect(this.ctx.destination);
      this._initialized = true;

      // Resume context if suspended
      if (this.ctx.state === 'suspended') {
        const unlock = () => {
          if (this.ctx && this.ctx.state === 'suspended') {
            this.ctx.resume();
          }
          window.removeEventListener('pointerdown', unlock);
          window.removeEventListener('touchstart', unlock);
        };
        window.addEventListener('pointerdown', unlock, { once: true });
        window.addEventListener('touchstart', unlock, { once: true });
      }
    } catch (e) {
      console.warn('AudioContext init error:', e);
    }
  }

  ensureContext() {
    if (!this._initialized) this.init();
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
  }

  vibrate(ms = 20) {
    if (!this.hapticsEnabled) return;
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.vibrate === 'function') {
        window.AndroidBridge.vibrate(ms);
      } else if (navigator.vibrate) {
        navigator.vibrate(ms);
      }
    } catch (e) {}
  }

  // 1. playTapGlass(pitch): Sine + Triangle oscillator with fast exponential decay (crystalline clink)
  playTapGlass(pitchMultiplier = 1.0) {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    this.vibrate(15);

    const now = this.ctx.currentTime;
    const baseFreq = 880 * pitchMultiplier;

    // Primary crystalline sine
    const osc1 = this.ctx.createOscillator();
    const gain1 = this.ctx.createGain();
    osc1.type = 'sine';
    osc1.frequency.setValueAtTime(baseFreq, now);
    osc1.frequency.exponentialRampToValueAtTime(baseFreq * 0.95, now + 0.12);

    gain1.gain.setValueAtTime(0.45, now);
    gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.14);

    // Overtone triangle for glass body shimmer
    const osc2 = this.ctx.createOscillator();
    const gain2 = this.ctx.createGain();
    osc2.type = 'triangle';
    osc2.frequency.setValueAtTime(baseFreq * 2.75, now);
    osc2.frequency.exponentialRampToValueAtTime(baseFreq * 2.7, now + 0.08);

    gain2.gain.setValueAtTime(0.2, now);
    gain2.gain.exponentialRampToValueAtTime(0.001, now + 0.09);

    // High harmonic ping
    const osc3 = this.ctx.createOscillator();
    const gain3 = this.ctx.createGain();
    osc3.type = 'sine';
    osc3.frequency.setValueAtTime(baseFreq * 4.1, now);
    gain3.gain.setValueAtTime(0.15, now);
    gain3.gain.exponentialRampToValueAtTime(0.001, now + 0.06);

    osc1.connect(gain1);
    osc2.connect(gain2);
    osc3.connect(gain3);
    gain1.connect(this.masterGain);
    gain2.connect(this.masterGain);
    gain3.connect(this.masterGain);

    osc1.start(now);
    osc2.start(now);
    osc3.start(now);
    osc1.stop(now + 0.15);
    osc2.stop(now + 0.1);
    osc3.stop(now + 0.07);
  }

  // 2. playLiquidPour(fillLevel): Filtered bubble noise + pitch-rising sine sweep
  playLiquidPour(fillLevel = 1) {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    this.vibrate(25);

    const now = this.ctx.currentTime;
    const baseFreq = 340 + fillLevel * 140;

    // Pitch-rising sine wave (glug/slosh)
    const osc = this.ctx.createOscillator();
    const oscGain = this.ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(baseFreq, now);
    osc.frequency.exponentialRampToValueAtTime(baseFreq * 1.6, now + 0.22);
    osc.frequency.exponentialRampToValueAtTime(baseFreq * 1.3, now + 0.32);

    oscGain.gain.setValueAtTime(0.01, now);
    oscGain.gain.linearRampToValueAtTime(0.4, now + 0.06);
    oscGain.gain.exponentialRampToValueAtTime(0.001, now + 0.35);

    // Filtered liquid noise (water spray/splash)
    const bufferSize = this.ctx.sampleRate * 0.25;
    const noiseBuffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
    const output = noiseBuffer.getChannelData(0);
    for (let i = 0; i < bufferSize; i++) {
      output[i] = Math.random() * 2 - 1;
    }

    const whiteNoise = this.ctx.createBufferSource();
    whiteNoise.buffer = noiseBuffer;

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.setValueAtTime(1200 + fillLevel * 400, now);
    filter.Q.setValueAtTime(4.0, now);

    const noiseGain = this.ctx.createGain();
    noiseGain.gain.setValueAtTime(0.2, now);
    noiseGain.gain.exponentialRampToValueAtTime(0.001, now + 0.25);

    osc.connect(oscGain);
    oscGain.connect(this.masterGain);
    whiteNoise.connect(filter);
    filter.connect(noiseGain);
    noiseGain.connect(this.masterGain);

    osc.start(now);
    whiteNoise.start(now);
    osc.stop(now + 0.36);
    whiteNoise.stop(now + 0.26);
  }

  // 3. playPrismMerge(): Major triad arpeggio with crystalline shimmer delay
  playPrismMerge() {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    this.vibrate(45);

    const now = this.ctx.currentTime;
    const notes = [523.25, 659.25, 783.99, 1046.5]; // C5, E5, G5, C6 (Major triad chord)

    notes.forEach((freq, idx) => {
      const delay = idx * 0.055;
      const noteTime = now + delay;

      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, noteTime);
      osc.frequency.exponentialRampToValueAtTime(freq * 1.02, noteTime + 0.28);

      gain.gain.setValueAtTime(0.01, noteTime);
      gain.gain.linearRampToValueAtTime(0.35, noteTime + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, noteTime + 0.3);

      // Shimmer overtone
      const shimmer = this.ctx.createOscillator();
      const shimmerGain = this.ctx.createGain();
      shimmer.type = 'triangle';
      shimmer.frequency.setValueAtTime(freq * 2, noteTime);
      shimmerGain.gain.setValueAtTime(0.12, noteTime);
      shimmerGain.gain.exponentialRampToValueAtTime(0.001, noteTime + 0.18);

      osc.connect(gain);
      gain.connect(this.masterGain);
      shimmer.connect(shimmerGain);
      shimmerGain.connect(this.masterGain);

      osc.start(noteTime);
      shimmer.start(noteTime);
      osc.stop(noteTime + 0.32);
      shimmer.stop(noteTime + 0.2);
    });
  }

  // 4. playBlocked(): 90Hz triangle wave low bass thud with dampening
  playBlocked() {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    this.vibrate(35);

    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'triangle';
    osc.frequency.setValueAtTime(110, now);
    osc.frequency.exponentialRampToValueAtTime(65, now + 0.16);

    gain.gain.setValueAtTime(0.5, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.18);

    const lowpass = this.ctx.createBiquadFilter();
    lowpass.type = 'lowpass';
    lowpass.frequency.setValueAtTime(220, now);

    osc.connect(lowpass);
    lowpass.connect(gain);
    gain.connect(this.masterGain);

    osc.start(now);
    osc.stop(now + 0.19);
  }

  // 5. playVialComplete(): Sparkling pentatonic chime cascade + cork pop
  playVialComplete() {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    this.vibrate(60);

    const now = this.ctx.currentTime;

    // Cork Pop
    const popOsc = this.ctx.createOscillator();
    const popGain = this.ctx.createGain();
    popOsc.type = 'sine';
    popOsc.frequency.setValueAtTime(180, now);
    popOsc.frequency.exponentialRampToValueAtTime(600, now + 0.04);
    popOsc.frequency.exponentialRampToValueAtTime(120, now + 0.08);

    popGain.gain.setValueAtTime(0.55, now);
    popGain.gain.exponentialRampToValueAtTime(0.001, now + 0.09);

    popOsc.connect(popGain);
    popGain.connect(this.masterGain);
    popOsc.start(now);
    popOsc.stop(now + 0.1);

    // Pentatonic sparkle cascade (G5, A5, C6, D6, E6, G6)
    const pitches = [783.99, 880.00, 1046.50, 1174.66, 1318.51, 1567.98];
    pitches.forEach((freq, i) => {
      const delay = 0.06 + i * 0.045;
      const noteTime = now + delay;

      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, noteTime);

      gain.gain.setValueAtTime(0.01, noteTime);
      gain.gain.linearRampToValueAtTime(0.3, noteTime + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.001, noteTime + 0.25);

      osc.connect(gain);
      gain.connect(this.masterGain);

      osc.start(noteTime);
      osc.stop(noteTime + 0.28);
    });
  }

  // 6. Booster activation sound
  playBooster() {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    this.vibrate(30);

    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(300, now);
    osc.frequency.exponentialRampToValueAtTime(1200, now + 0.25);

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(800, now);
    filter.frequency.exponentialRampToValueAtTime(3200, now + 0.25);

    gain.gain.setValueAtTime(0.3, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.32);

    osc.connect(filter);
    filter.connect(gain);
    gain.connect(this.masterGain);

    osc.start(now);
    osc.stop(now + 0.33);
  }

  // 7. Victory Fanfare
  playVictory() {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    this.vibrate(80);

    const now = this.ctx.currentTime;
    const chords = [
      { delay: 0.0, freqs: [523.25, 659.25, 783.99] }, // C5
      { delay: 0.16, freqs: [587.33, 739.99, 880.00] }, // D5
      { delay: 0.32, freqs: [659.25, 830.61, 987.77] }, // E5
      { delay: 0.50, freqs: [783.99, 987.77, 1174.66, 1567.98] } // G5-G6 Grand
    ];

    chords.forEach(c => {
      const chordTime = now + c.delay;
      c.freqs.forEach(freq => {
        const osc = this.ctx.createOscillator();
        const gain = this.ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, chordTime);

        gain.gain.setValueAtTime(0.01, chordTime);
        gain.gain.linearRampToValueAtTime(0.28, chordTime + 0.02);
        gain.gain.exponentialRampToValueAtTime(0.001, chordTime + 0.45);

        osc.connect(gain);
        gain.connect(this.masterGain);
        osc.start(chordTime);
        osc.stop(chordTime + 0.48);
      });
    });
  }

  // 8. Defeat sound
  playDefeat() {
    if (!this.enabled) return;
    this.ensureContext();
    if (!this.ctx) return;
    this.vibrate(60);

    const now = this.ctx.currentTime;
    const freqs = [440, 392, 349.23, 293.66];
    freqs.forEach((freq, idx) => {
      const t = now + idx * 0.12;
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq, t);

      gain.gain.setValueAtTime(0.3, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + 0.22);

      osc.connect(gain);
      gain.connect(this.masterGain);
      osc.start(t);
      osc.stop(t + 0.25);
    });
  }

  toggleSound() {
    this.enabled = !this.enabled;
    return this.enabled;
  }

  toggleHaptics() {
    this.hapticsEnabled = !this.hapticsEnabled;
    return this.hapticsEnabled;
  }
}

window.soundEngine = new SoundEngine();
