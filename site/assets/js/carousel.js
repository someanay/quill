/* ============================================================================
   Quill — shared demo carousel engine

   Data-driven from window.QuillDemos (videos.js). Each page contributes the
   scene markup as <template data-scene="…"> elements; this engine builds the
   track, auto-advances on a timer, and wires up manual interaction.

   Behaviour:
   - timed auto-advance, continuous loop
   - progress indicator synced to the interval
   - prev/next arrows + direct slide selection (progress steps)
   - touch/swipe + mouse-drag + keyboard (left/right) + focus pause
   - resets the timer after manual interaction
   - pauses when the carousel leaves the viewport
   - respects prefers-reduced-motion (no auto-advance)
   ============================================================================ */

(function () {
  "use strict";

  var DEFAULT_INTERVAL = 6000;

  function prefersReducedMotion() {
    return window.matchMedia &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }

  function init(root) {
    var platform = (root.getAttribute("data-platform") || "android").toUpperCase();
    var config = window.QuillDemos && window.QuillDemos.forPage(platform);
    if (!config || !config.slides || !config.slides.length) return;

    var slides = config.slides;
    var reduced = prefersReducedMotion();
    var interval = parseInt(root.getAttribute("data-interval"), 10) || DEFAULT_INTERVAL;

    // --- build chrome ---
    var progress = document.createElement("div");
    progress.className = "carousel__progress";
    progress.setAttribute("role", "tablist");
    progress.setAttribute("aria-label", "Demos");

    var viewport = document.createElement("div");
    viewport.className = "carousel__viewport";
    viewport.setAttribute("tabindex", "0");
    viewport.setAttribute("aria-label", "Product demonstration");

    var track = document.createElement("div");
    track.className = "carousel__track";

    var caption = document.createElement("div");
    caption.className = "carousel__caption";
    caption.setAttribute("aria-live", "polite");

    var controls = document.createElement("div");
    controls.className = "carousel__controls";

    var prevBtn = makeArrow("Previous demo", "M15 6l-6 6 6 6");
    var nextBtn = makeArrow("Next demo", "M9 6l6 6-6 6");

    var counter = document.createElement("span");
    counter.className = "carousel__counter";

    controls.appendChild(prevBtn);
    controls.appendChild(counter);
    controls.appendChild(nextBtn);

    // Build slides from templates
    slides.forEach(function (slide, i) {
      var step = document.createElement("button");
      step.className = "carousel__progress-step";
      step.setAttribute("role", "tab");
      step.setAttribute("aria-label", (i + 1) + " of " + slides.length + ": " + slide.title);
      var fill = document.createElement("span");
      fill.className = "carousel__progress-fill";
      step.appendChild(fill);
      progress.appendChild(step);

      var el = document.createElement("div");
      el.className = "carousel__slide";
      el.setAttribute("role", "tabpanel");
      var scene = document.createElement("div");
      scene.className = "carousel__scene";
      var tpl = document.querySelector(
        'template[data-scene="' + slide.scene + '"]'
      );
      if (tpl) scene.appendChild(tpl.content.cloneNode(true));
      el.appendChild(scene);
      track.appendChild(el);
    });

    root.appendChild(progress);
    root.appendChild(viewport);
    viewport.appendChild(track);
    root.appendChild(caption);
    root.appendChild(controls);

    var stepEls = Array.prototype.slice.call(
      progress.querySelectorAll(".carousel__progress-step")
    );
    var slideEls = Array.prototype.slice.call(track.children);

    var index = 0;
    var timer = null;
    var visible = true;
    var pausedByUser = false;

    function setStepState(i, state) {
      stepEls.forEach(function (s, n) {
        s.classList.toggle("is-active", n === i && state === "active");
        s.classList.toggle("is-done", n < i);
        s.setAttribute("aria-selected", n === i ? "true" : "false");
      });
    }

    function render() {
      var n = slides.length;
      track.style.transform = "translateX(-" + index * 100 + "%)";
      caption.innerHTML =
        '<h3>' + escapeHtml(slides[index].title) + "</h3>" +
        "<p>" + escapeHtml(slides[index].description) + "</p>";
      counter.textContent = (index + 1) + " / " + n;
      setStepState(index, "active");
      prevBtn.setAttribute("aria-disabled", "false");
      nextBtn.setAttribute("aria-disabled", "false");
    }

    function goTo(i, user) {
      var n = slides.length;
      index = (i + n) % n;
      render();
      if (user) schedule();
    }

    function next(user) { goTo(index + 1, user); }
    function prev(user) { goTo(index - 1, user); }

    // Timer management. Reduced motion or user pause => never auto-advance.
    function clearTimer() {
      if (timer) { clearTimeout(timer); timer = null; }
      stepEls.forEach(function (s) {
        s.style.setProperty("--carousel-interval", interval + "ms");
      });
    }

    function schedule() {
      clearTimer();
      // Restart the fill animation on the active step.
      stepEls.forEach(function (s) {
        s.classList.remove("is-active");
        void s.offsetWidth; // reflow to restart CSS animation
      });
      setStepState(index, "active");

      if (reduced || pausedByUser || !visible) return;
      timer = setTimeout(function () {
        next(false);
        schedule();
      }, interval);
    }

    // --- interactions ---
    prevBtn.addEventListener("click", function () { prev(true); });
    nextBtn.addEventListener("click", function () { next(true); });

    stepEls.forEach(function (s, i) {
      s.addEventListener("click", function () { goTo(i, true); });
    });

    viewport.addEventListener("keydown", function (e) {
      if (e.key === "ArrowLeft") { e.preventDefault(); prev(true); }
      else if (e.key === "ArrowRight") { e.preventDefault(); next(true); }
      else if (e.key === "Home") { e.preventDefault(); goTo(0, true); }
      else if (e.key === "End") { e.preventDefault(); goTo(slides.length - 1, true); }
    });

    // Pause on hover / keyboard focus (meaningful user interaction).
    root.addEventListener("mouseenter", function () { pausedByUser = true; clearTimer(); });
    root.addEventListener("mouseleave", function () { pausedByUser = false; schedule(); });
    root.addEventListener("focusin", function () { pausedByUser = true; clearTimer(); });
    root.addEventListener("focusout", function (e) {
      if (!root.contains(e.relatedTarget)) { pausedByUser = false; schedule(); }
    });

    // Swipe / drag
    var startX = null, startY = null, dragging = false;
    viewport.addEventListener("pointerdown", function (e) {
      startX = e.clientX; startY = e.clientY; dragging = true;
      viewport.setPointerCapture && viewport.setPointerCapture(e.pointerId);
    });
    viewport.addEventListener("pointerup", function (e) {
      if (!dragging) return;
      dragging = false;
      var dx = e.clientX - startX;
      var dy = e.clientY - startY;
      if (Math.abs(dx) > 48 && Math.abs(dx) > Math.abs(dy)) {
        if (dx < 0) next(true); else prev(true);
      }
      startX = startY = null;
    });
    viewport.addEventListener("pointercancel", function () {
      dragging = false; startX = startY = null;
    });

    // Pause when off-screen
    if ("IntersectionObserver" in window) {
      var io = new IntersectionObserver(function (entries) {
        visible = entries[0].isIntersecting;
        if (visible) schedule(); else clearTimer();
      }, { threshold: 0.2 });
      io.observe(root);
    }

    render();
    schedule();
  }

  function makeArrow(label, path) {
    var b = document.createElement("button");
    b.className = "carousel__arrow";
    b.setAttribute("aria-label", label);
    b.innerHTML =
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="' + path + '"/></svg>';
    return b;
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function boot() {
    document.querySelectorAll(".carousel[data-platform]").forEach(init);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
