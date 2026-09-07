/* ============================================================================
   Quill — site interactions: theme toggle, mobile nav, scroll reveals,
   and the living bubble + ink-meter demos.
   ============================================================================ */

(function () {
  "use strict";

  function prefersReducedMotion() {
    return window.matchMedia &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  /* --- Theme toggle ------------------------------------------------------- */
  function initThemeToggle() {
    document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
      btn.setAttribute("aria-label", "Toggle colour theme");
      btn.addEventListener("click", function () {
        window.QuillTheme && window.QuillTheme.toggle();
      });
    });
  }

  /* --- Mobile navigation -------------------------------------------------- */
  function initMobileNav() {
    var trigger = document.querySelector("[data-nav-toggle]");
    var links = document.querySelector("[data-nav-links]");
    if (!trigger || !links) return;

    var open = false;
    function setOpen(v) {
      open = v;
      links.classList.toggle("mobile-open", v);
      trigger.setAttribute("aria-expanded", String(v));
      trigger.setAttribute("aria-label", v ? "Close menu" : "Open menu");
    }
    trigger.addEventListener("click", function () { setOpen(!open); });
    links.addEventListener("click", function (e) {
      if (e.target.closest("a")) setOpen(false);
    });
    window.addEventListener("resize", function () {
      if (window.innerWidth > 860) setOpen(false);
    });
  }

  /* --- Scroll reveals ----------------------------------------------------- */
  function initReveals() {
    var els = document.querySelectorAll(".reveal");
    if (!els.length) return;
    if (!("IntersectionObserver" in window)) {
      els.forEach(function (el) { el.classList.add("is-visible"); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -8% 0px" });
    els.forEach(function (el) { io.observe(el); });
  }

  /* --- Living ink meter --------------------------------------------------- */
  // Animates a set of .ink-meter__bar to a gentle "voice" pattern.
  function animateMeter(meter, active) {
    if (!meter) return;
    var bars = meter.querySelectorAll(".ink-meter__bar");
    if (prefersReducedMotion()) {
      bars.forEach(function (b, i) {
        b.style.transform = "scaleY(" + (active ? 0.5 + ((i % 5) * 0.1) : 0.12) + ")";
      });
      return;
    }
    var running = true;
    var t = 0;
    function frame() {
      if (!running) return;
      t += 0.16;
      bars.forEach(function (b, i) {
        if (!active) {
          b.style.transform = "scaleY(0.12)";
          return;
        }
        // Smooth pseudo-random heights so strokes read as ink, not noise.
        var wave = 0.5 + 0.5 * Math.sin(t * 1.8 + i * 0.72);
        var jitter = 0.12 * Math.sin(t * 3.3 + i * 1.9);
        var h = Math.max(0.14, Math.min(1, wave + jitter));
        b.style.transform = "scaleY(" + h.toFixed(3) + ")";
      });
      requestAnimationFrame(frame);
    }
    frame();
    return function stop() { running = false; };
  }

  /* --- Living bubble demo ------------------------------------------------ */
  // Cycles the bubble through its states; animates a paired meter while
  // "recording". Respects reduced motion (freezes on idle).
  var BUBBLE_CYCLE = [
    "idle", "recording", "transcribing", "idle",
  ];
  var BUBBLE_DWELL = [1400, 2200, 1800, 900]; // ms per state

  function initBubbleDemos() {
    document.querySelectorAll("[data-bubble-demo]").forEach(function (demo) {
      var bubble = demo.querySelector(".bubble");
      var meter = demo.querySelector(".ink-meter");
      if (!bubble) return;
      if (prefersReducedMotion()) {
        bubble.setAttribute("data-state", "idle");
        if (meter) animateMeter(meter, false);
        return;
      }
      var stopMeter = null;
      var i = 0;
      var timer = null;
      function step() {
        var state = BUBBLE_CYCLE[i % BUBBLE_CYCLE.length];
        bubble.setAttribute("data-state", state);
        var ribbon = demo.querySelector("[data-desktop-ribbon]");
        if (ribbon) {
          var ribbonState = state === "recording" ? "listening" : state === "transcribing" ? "transcribing" : "ready";
          var labels = ribbonState === "listening" ? ["Listening", "Alt+Q to finish"] :
            ribbonState === "transcribing" ? ["Transcribing", "Your words stay on your chosen provider"] : ["Ready", "Press Alt+Q to dictate"];
          ribbon.setAttribute("data-state", ribbonState);
          ribbon.querySelector("[data-ribbon-title]").textContent = labels[0];
          ribbon.querySelector("[data-ribbon-detail]").textContent = labels[1];
        }
        if (meter && state === "recording" && !stopMeter) {
          stopMeter = animateMeter(meter, true);
        } else if (meter && state !== "recording" && stopMeter) {
          stopMeter(); stopMeter = null;
          animateMeter(meter, false);
        }
        i++;
        timer = setTimeout(step, BUBBLE_DWELL[(i - 1) % BUBBLE_DWELL.length]);
      }
      step();

      // Clean up if the element is removed.
      if ("MutationObserver" in window) {
        new MutationObserver(function (_, obs) {
          if (!document.body.contains(demo)) {
            clearTimeout(timer);
            if (stopMeter) stopMeter();
            obs.disconnect();
          }
        }).observe(document.body, { childList: true, subtree: true });
      }
    });
  }

  /* --- Standalone meter demos (always lightly "breathing" when in view) --- */
  function initStandaloneMeters() {
    document.querySelectorAll("[data-meter-demo]").forEach(function (meter) {
      var active = meter.getAttribute("data-meter-demo") === "live";
      if (!("IntersectionObserver" in window)) {
        animateMeter(meter, active);
        return;
      }
      var started = false;
      var stop = null;
      var io = new IntersectionObserver(function (entries) {
        if (entries[0].isIntersecting && !started) {
          started = true;
          stop = animateMeter(meter, active);
        } else if (!entries[0].isIntersecting && started && stop) {
          stop(); stop = null; started = false;
        }
      }, { threshold: 0.3 });
      io.observe(meter);
    });
  }

  /* --- Platform detection + native-button highlight --------------------- */
  function detectOs() {
    var ua = navigator.userAgent || "";
    if (navigator.userAgentData && navigator.userAgentData.platform) {
      var p = navigator.userAgentData.platform.toLowerCase();
      if (p.indexOf("android") !== -1) return "android";
      if (p.indexOf("win") !== -1) return "windows";
    }
    if (/android/i.test(ua)) return "android";
    if (/windows|win32|win64|wow64/i.test(ua)) return "windows";
    return "other";
  }

  function initPlatformHighlight() {
    var os = detectOs();
    document.documentElement.setAttribute("data-os", os);

    // Native CTA promotion only applies where a per-OS CTA button exists
    // (android/windows). Other visitors keep the default button styling.
    if (os === "android" || os === "windows") {
      document.querySelectorAll("[data-platform-cta]").forEach(function (el) {
        var nativeTarget = el.getAttribute("data-platform-cta") === os;
        el.classList.toggle("is-native-platform", nativeTarget);
        el.classList.toggle("btn--primary", nativeTarget);
        el.classList.toggle("btn--ghost", !nativeTarget);
      });
    }

    // Highlight the visitor's platform wherever it's tagged — cards and store badges.
    if (os === "other") return;
    document.querySelectorAll('[data-platform="' + os + '"]').forEach(function (el) {
      el.classList.add("is-native-platform");
      var chip = document.createElement("span");
      chip.className = "native-chip";
      chip.textContent = "Your device";
      el.appendChild(chip);
      // On role="img" badges the injected chip text is not announced, so fold
      // the cue into the element's own accessible name.
      var label = el.getAttribute("aria-label");
      if (label && label.toLowerCase().indexOf("your device") === -1) {
        el.setAttribute("aria-label", label + " — your device");
      }
    });
  }

  function applyPlatformPreview(platform) {
    document.documentElement.setAttribute("data-platform-preview", platform);
    document.querySelectorAll("[data-platform-copy]").forEach(function (el) {
      var value = el.getAttribute("data-" + platform);
      if (value) el.textContent = value;
    });
    document.querySelectorAll(".platform-preview__button[data-platform-preview]").forEach(function (button) {
      var active = button.getAttribute("data-platform-preview") === platform;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
  }

  function initPlatformCopy() {
    var os = detectOs();
    var platform = os === "windows" ? "windows" : "android";
    applyPlatformPreview(platform);
    document.querySelectorAll(".platform-preview__button[data-platform-preview]").forEach(function (button) {
      button.addEventListener("click", function () {
        applyPlatformPreview(button.getAttribute("data-platform-preview"));
      });
    });
  }

  /* --- Boot ---------------------------------------------------------------- */
  function boot() {
    initThemeToggle();
    initMobileNav();
    initReveals();
    initBubbleDemos();
    initStandaloneMeters();
    initPlatformHighlight();
    initPlatformCopy();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
