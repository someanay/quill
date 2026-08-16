/* ============================================================================
   Quill — theme management (light / dark / system, persisted, no FOUC)
   ============================================================================ */

(function () {
  var STORAGE_KEY = "quill-theme";
  var root = document.documentElement;

  function storedTheme() {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch (e) {
      return null;
    }
  }

  function systemPrefersDark() {
    return window.matchMedia &&
      window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  function apply(theme) {
    root.setAttribute("data-theme", theme);
  }

  // Resolve "light" | "dark" (system already resolved at read time).
  function resolve() {
    var t = storedTheme();
    if (t === "light" || t === "dark") return t;
    return systemPrefersDark() ? "dark" : "light";
  }

  window.QuillTheme = {
    get: resolve,
    set: function (theme) {
      try {
        localStorage.setItem(STORAGE_KEY, theme);
      } catch (e) {
        /* storage unavailable — still apply for the session */
      }
      apply(theme);
      // Enable smooth transitions only after the user has made a choice.
      root.classList.add("theme-ready");
    },
    toggle: function () {
      var next = resolve() === "dark" ? "light" : "dark";
      this.set(next);
      return next;
    },
  };

  // Follow OS changes when the user hasn't made an explicit choice.
  if (window.matchMedia) {
    var mq = window.matchMedia("(prefers-color-scheme: dark)");
    var onChange = function () {
      if (!storedTheme()) apply(systemPrefersDark() ? "dark" : "light");
    };
    if (mq.addEventListener) mq.addEventListener("change", onChange);
    else if (mq.addListener) mq.addListener(onChange);
  }
})();
