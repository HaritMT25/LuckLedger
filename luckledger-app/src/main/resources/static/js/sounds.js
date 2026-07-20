/* Pure WebAudio sound effects for LuckLedger — zero audio assets, every sound is synthesised on the
 * fly. Matches the app's helper style (a single module object, like Api). Sound is INDEPENDENT of
 * prefers-reduced-motion: reduced motion gates visuals only, so a player who dislikes animation can
 * still hear the card. The toggle state persists in localStorage. */
const Sounds = (() => {
    const STORAGE_KEY = 'luckledger.sound';
    let ctx = null;
    let on = load();

    // Default ON; honour any stored preference.
    function load() {
        const v = localStorage.getItem(STORAGE_KEY);
        return v === null ? true : v === 'on';
    }

    // Lazily create the AudioContext. Browsers block it until a user gesture, and every sound here is
    // triggered by one (scratching, a reveal, the toggle click). Resume it if the tab suspended it.
    function audio() {
        if (!ctx) {
            const AC = window.AudioContext || window.webkitAudioContext;
            if (!AC) return null;
            try { ctx = new AC(); } catch (e) { return null; }
        }
        if (ctx.state === 'suspended') ctx.resume().catch(() => {});
        return ctx;
    }

    // A short tone: an oscillator through an exponential gain envelope so it never clicks.
    function tone(freq, delay, dur, peak, type) {
        const ac = audio();
        if (!ac) return;
        const t0 = ac.currentTime + delay;
        const osc = ac.createOscillator();
        const gain = ac.createGain();
        osc.type = type || 'sine';
        osc.frequency.setValueAtTime(freq, t0);
        gain.gain.setValueAtTime(0.0001, t0);
        gain.gain.exponentialRampToValueAtTime(peak, t0 + 0.012);
        gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
        osc.connect(gain).connect(ac.destination);
        osc.start(t0);
        osc.stop(t0 + dur + 0.03);
    }

    // A burst of filtered white noise — the scratch texture and the muted lose thud.
    function noise(dur, peak, filterType, filterFreq, q) {
        const ac = audio();
        if (!ac) return;
        const t0 = ac.currentTime;
        const frames = Math.max(1, Math.floor(ac.sampleRate * dur));
        const buffer = ac.createBuffer(1, frames, ac.sampleRate);
        const data = buffer.getChannelData(0);
        for (let i = 0; i < frames; i++) data[i] = Math.random() * 2 - 1;
        const src = ac.createBufferSource();
        src.buffer = buffer;
        const filter = ac.createBiquadFilter();
        filter.type = filterType;
        filter.frequency.value = filterFreq;
        if (q) filter.Q.value = q;
        const gain = ac.createGain();
        gain.gain.setValueAtTime(peak, t0);
        gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
        src.connect(filter).connect(gain).connect(ac.destination);
        src.start(t0);
        src.stop(t0 + dur);
    }

    let lastScratch = 0;

    return {
        /** Throttled bandpass white-noise burst — coating lifting under the coin. Throttled so a flurry
         *  of pointer strokes doesn't stack into a roar. */
        scratch() {
            if (!on) return;
            const now = Date.now();
            if (now - lastScratch < 100) return;
            lastScratch = now;
            noise(0.07, 0.05, 'bandpass', 1700, 0.8);
        },
        /** Soft two-note ping when a zone's coating fully clears. */
        zone() {
            if (!on) return;
            tone(880, 0, 0.18, 0.09, 'sine');
            tone(1320, 0.02, 0.16, 0.05, 'sine');
        },
        /** Short rising fanfare on a win (four ascending notes). */
        win() {
            if (!on) return;
            const notes = [523.25, 659.25, 783.99, 1046.5];
            notes.forEach((f, i) => tone(f, i * 0.09, 0.24, 0.12, 'triangle'));
        },
        /** Muted low thud on a loss. */
        lose() {
            if (!on) return;
            tone(150, 0, 0.28, 0.09, 'sine');
            noise(0.2, 0.03, 'lowpass', 320);
        },
        /** Flip sound on/off, persist it, and return the new state. A confirming ping doubles as the
         *  user gesture that unlocks the AudioContext when turning sound on. */
        toggle() {
            on = !on;
            localStorage.setItem(STORAGE_KEY, on ? 'on' : 'off');
            if (on) { audio(); this.zone(); }
            return on;
        },
        /** Whether sound is currently enabled. */
        enabled() { return on; },
    };
})();
