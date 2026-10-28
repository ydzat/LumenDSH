/**
 * @lumendsh/webui-shell
 *
 * Renders the native-only controls (return to habitat, voice capture) on top of
 * the official page, replacing the Android overlays that used to float there.
 * Falls back to nothing when the page is not hosted by the LumenDSH app.
 */
window.__ModuleLoader__.load({
  id: '@lumendsh/webui-shell',
  factory: (require) => {
    var module = { exports: {} }
    var exports = module.exports
    const React = require('react')
    const inject = ['slots']

    const buttonStyle = {
      width: '48px', height: '48px', borderRadius: '24px', border: '1px solid rgba(255,255,255,.18)',
      background: 'rgba(12,20,32,.82)', color: '#e6ecf6', fontSize: '20px', lineHeight: '46px',
      textAlign: 'center', cursor: 'pointer', padding: 0, backdropFilter: 'blur(6px)',
    }

    function installStyles() {
      if (typeof document === 'undefined' || document.getElementById('lumendsh-shell-css')) return
      const style = document.createElement('style')
      style.id = 'lumendsh-shell-css'
      // The composer dock carries the official context meter and any plugin
      // pills (Magic Context). On a phone they do not fit side by side, so the
      // row scrolls horizontally instead of overlapping; the gesture is a
      // horizontal swipe inside the page, never the system bottom gesture.
      style.textContent = [
        '[data-composer-dock]{overflow-x:auto!important;overflow-y:hidden!important;',
        'flex-wrap:nowrap!important;scrollbar-width:none!important;',
        '-webkit-overflow-scrolling:touch!important;overscroll-behavior-x:contain!important}',
        '[data-composer-dock]::-webkit-scrollbar{display:none!important;height:0!important}',
        '[data-composer-dock]>*{flex:0 0 auto!important;min-width:max-content!important}',
        '[data-composer-dock] [data-mc-surface]{position:fixed!important;right:10px!important;bottom:150px!important;z-index:45!important}',
      ].join('')
      document.head.appendChild(style)
    }

    // The tide capsule duplicates the habitat's period display and collides with
    // the console header on narrow screens; keep the console for precise work.
    function hideTideCapsule() {
      if (typeof document === 'undefined') return
      const util = document.querySelector('[class*="headerUtilities"]')
      if (!util) return
      for (const child of util.children) {
        if (/梁文[峰谷]|距[峰谷]/.test(child.textContent || '')) child.style.display = 'none'
      }
    }

    function apply(ctx) {
      installStyles()
      if (typeof document !== 'undefined') {
        const start = () => {
          hideTideCapsule()
          const observer = new MutationObserver(() => hideTideCapsule())
          observer.observe(document.body, {childList: true, subtree: true})
        }
        if (document.body) start()
        else document.addEventListener('DOMContentLoaded', start, {once: true})
      }
      // No floating client controls on the official page: the Android back
      // gesture returns to the habitat and the habitat owns voice.
    }

    exports.apply = apply
    exports.inject = inject
    return module.exports
  },
})
