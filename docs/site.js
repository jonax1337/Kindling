(() => {
  'use strict';
  document.documentElement.classList.add('js');
  const root = document.documentElement;
  const systemTheme = window.matchMedia('(prefers-color-scheme: dark)');
  const themeButton = document.querySelector('.theme-toggle');
  const updateThemeLabel = () => {
    const dark = root.dataset.theme ? root.dataset.theme === 'dark' : systemTheme.matches;
    themeButton.textContent = dark ? 'Light' : 'Dark';
    themeButton.setAttribute('aria-label', `Switch to ${dark ? 'light' : 'dark'} theme`);
  };
  themeButton.hidden = false;
  updateThemeLabel();
  systemTheme.addEventListener('change', updateThemeLabel);
  themeButton.addEventListener('click', () => {
    root.dataset.theme = (root.dataset.theme || (systemTheme.matches ? 'dark' : 'light')) === 'dark' ? 'light' : 'dark';
    try { localStorage.setItem('kindling-theme', root.dataset.theme); } catch { /* Private storage can be unavailable. */ }
    updateThemeLabel();
  });

  const header = document.querySelector('.site-header');
  const menuButton = document.querySelector('.menu-toggle');
  const setMenu = open => {
    header.dataset.menuOpen = String(open);
    menuButton.setAttribute('aria-expanded', String(open));
    menuButton.textContent = open ? 'Close' : 'Menu';
  };
  menuButton.addEventListener('click', () => setMenu(menuButton.getAttribute('aria-expanded') !== 'true'));
  document.querySelectorAll('#main-nav a').forEach(link => link.addEventListener('click', () => setMenu(false)));
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && header.dataset.menuOpen === 'true') { setMenu(false); menuButton.focus(); }
  });

  const tabs = [...document.querySelectorAll('[role="tab"]')];
  const selectTab = tab => {
    tabs.forEach(item => {
      const selected = item === tab;
      item.setAttribute('aria-selected', String(selected));
      item.tabIndex = selected ? 0 : -1;
      document.getElementById(item.getAttribute('aria-controls')).hidden = !selected;
    });
    document.querySelector('.copy-status').textContent = '';
  };
  selectTab(tabs[0]);
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectTab(tab));
    tab.addEventListener('keydown', event => {
      let next;
      if (event.key === 'ArrowRight') next = tabs[(index + 1) % tabs.length];
      if (event.key === 'ArrowLeft') next = tabs[(index - 1 + tabs.length) % tabs.length];
      if (event.key === 'Home') next = tabs[0];
      if (event.key === 'End') next = tabs.at(-1);
      if (next) { event.preventDefault(); selectTab(next); next.focus(); }
    });
  });
  const copyButton = document.querySelector('.copy-button');
  copyButton.hidden = false;
  copyButton.addEventListener('click', async () => {
    const code = document.querySelector('[role="tabpanel"]:not([hidden]) pre code');
    const command = code.textContent.trim();
    const status = document.querySelector('.copy-status');
    try {
      await navigator.clipboard.writeText(command);
      status.textContent = 'Command copied.';
    } catch {
      const range = document.createRange();
      range.selectNodeContents(code);
      const selection = window.getSelection();
      selection.removeAllRanges(); selection.addRange(range);
      status.textContent = 'Command selected. Use your keyboard to copy it.';
    }
  });

  if (!window.gsap || !window.ScrollTrigger) return;
  gsap.registerPlugin(ScrollTrigger);
  const media = gsap.matchMedia();
  media.add('(prefers-reduced-motion: no-preference)', () => {
    // Parallax separates the world from the foreground copy without taking over scrolling.
    gsap.fromTo('.hero-image', { yPercent: -3 }, { yPercent: 12, ease: 'none', scrollTrigger: { trigger: '.hero', start: 'top top', end: 'bottom top', scrub: 1 } });
    gsap.timeline({ defaults: { ease: 'power3.out', duration: .9 } })
      .from('.hero-line', { y: 32, autoAlpha: 0, stagger: .12 })
      .from('.hero-enter', { y: 16, autoAlpha: 0, stagger: .1 }, .2);
    gsap.to('.reading-progress', { scaleX: 1, ease: 'none', scrollTrigger: { trigger: document.body, start: 'top top', end: 'max', scrub: true } });
    gsap.fromTo('.world-image', { yPercent: -6 }, { yPercent: 6, ease: 'none', scrollTrigger: { trigger: '.feature-layout', start: 'top bottom', end: 'bottom top', scrub: 1 } });
    gsap.utils.toArray('.reveal').forEach(element => {
      gsap.from(element, { y: 24, autoAlpha: 0, immediateRender: false, duration: .75, ease: 'power2.out', scrollTrigger: { trigger: element, start: 'top 93%', once: true } });
    });
  });
  document.querySelectorAll('details').forEach(details => details.addEventListener('toggle', () => ScrollTrigger.refresh()));
  document.fonts.ready.then(() => ScrollTrigger.refresh());
  window.addEventListener('load', () => ScrollTrigger.refresh(), { once: true });
})();
