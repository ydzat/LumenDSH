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
      // The bottom status bar becomes a drawer: the composer dock holds the
      // official context meter and any plugin pills, which do not fit on a
      // phone. Collapsed, the bar is replaced by one small handle button; open,
      // the whole dock reflows into a scrollable sheet above the composer. This
      // is generic — it never targets one plugin's element.
      style.textContent = [
        '[data-composer-dock]{display:none!important}',
        'body.lumendsh-dock-open [data-composer-dock]{display:flex!important;',
        'position:fixed!important;left:8px!important;right:8px!important;',
        'bottom:calc(172px + env(safe-area-inset-bottom))!important;',
        'max-height:45vh!important;overflow-y:auto!important;flex-wrap:wrap!important;',
        'align-items:center!important;gap:10px 16px!important;padding:12px 14px!important;',
        'background:rgba(18,18,22,.97)!important;',
        'border:1px solid rgba(127,127,127,.28)!important;border-radius:14px!important;',
        'box-shadow:0 12px 32px rgba(0,0,0,.45)!important;z-index:70!important;',
        'font-size:12px!important;line-height:18px!important}',
        'body.lumendsh-dock-open [data-composer-dock]>*{flex:0 0 auto!important;min-width:max-content!important}',
        '#lumendsh-dock-toggle{position:fixed!important;right:10px!important;',
        'bottom:calc(128px + env(safe-area-inset-bottom))!important;z-index:71!important;',
        'height:28px!important;padding:0 12px!important;border-radius:14px!important;',
        'border:1px solid rgba(127,127,127,.3)!important;background:rgba(24,24,28,.92)!important;',
        'color:#cfd3dc!important;font-size:12px!important;display:flex!important;',
        'align-items:center!important;gap:6px!important;user-select:none!important}',
        '#lumendsh-dock-toggle:active{transform:scale(.97)}',
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
          if (document.getElementById('lumendsh-dock-toggle') === null) {
            const toggle = document.createElement('button')
            toggle.id = 'lumendsh-dock-toggle'
            toggle.type = 'button'
            toggle.textContent = '状态 ▲'
            toggle.addEventListener('click', () => {
              const open = document.body.classList.toggle('lumendsh-dock-open')
              toggle.textContent = open ? '状态 ▼' : '状态 ▲'
            })
            document.body.appendChild(toggle)
          }
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
