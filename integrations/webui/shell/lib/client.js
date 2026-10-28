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
      // Taskbar-style expansion: the dock keeps its place at the bottom and its
      // own children (no clones, no text copies). A corner chevron lifts the
      // SAME dock into a floating panel above the composer, where its children
      // wrap and scroll; collapsed, plugin-contributed pills are hidden so they
      // cannot overlap the official meter.
      style.textContent = [
        '[data-composer-dock]{padding-right:34px!important;flex-wrap:nowrap!important;overflow:hidden!important}',
        '[data-composer-dock]>*{flex:0 0 auto!important}',
        '[data-composer-dock] [data-mc-surface]{display:none!important}',
        'body.lumendsh-dock-open [data-composer-dock]{',
        'position:fixed!important;left:8px!important;right:8px!important;',
        'bottom:calc(48px + env(safe-area-inset-bottom))!important;',
        'max-height:45vh!important;overflow-y:auto!important;display:flex!important;',
        'flex-wrap:wrap!important;align-items:center!important;gap:10px 16px!important;',
        'padding:12px 14px!important;background:rgba(18,18,22,.98)!important;',
        'border:1px solid rgba(127,127,127,.3)!important;border-radius:14px!important;',
        'box-shadow:0 12px 32px rgba(0,0,0,.45)!important;z-index:70!important;',
        'padding-right:14px!important;font-size:12px!important}',
        'body.lumendsh-dock-open [data-composer-dock] [data-mc-surface]{display:inline-flex!important}',
        'body.lumendsh-dock-open [data-composer-dock]>*{min-width:max-content!important}',
        '#lumendsh-dock-chevron{position:fixed!important;right:6px!important;',
        'bottom:calc(8px + env(safe-area-inset-bottom))!important;z-index:72!important;',
        'width:26px!important;height:24px!important;padding:0!important;border:0!important;',
        'border-radius:8px!important;background:rgba(30,30,36,.92)!important;color:#cfd3dc!important;',
        'font-size:12px!important;line-height:24px!important;text-align:center!important;',
        'user-select:none!important}',
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


    // The corner chevron lifts the real dock into a panel above the composer.
    function installDockToggle() {
      if (typeof document === 'undefined' || document.getElementById('lumendsh-dock-chevron') !== null) return
      const chevron = document.createElement('button')
      chevron.id = 'lumendsh-dock-chevron'
      chevron.type = 'button'
      chevron.textContent = '\u2303'
      chevron.addEventListener('click', () => {
        const open = document.body.classList.toggle('lumendsh-dock-open')
        chevron.textContent = open ? '\u2304' : '\u2303'
      })
      document.body.appendChild(chevron)
    }

    function apply(ctx) {
      installStyles()
      if (typeof document !== 'undefined') {
        const start = () => {
          hideTideCapsule()
          const observer = new MutationObserver(() => hideTideCapsule())
          observer.observe(document.body, {childList: true, subtree: true})
          installDockToggle()
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
