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
      // Taskbar-style overflow for the bottom status bar: the bar keeps its
      // place and the official figures; a small chevron pinned to the bottom
      // right opens a flyout listing whatever did not fit (for example a plugin
      // pill). Nothing is targeted by plugin identity and no gesture is used.
      style.textContent = [
        '[data-composer-dock]{padding-right:34px!important;flex-wrap:nowrap!important;',
        'overflow:hidden!important;align-items:center!important}',
        '[data-composer-dock]>*{flex:0 0 auto!important}',
        '.lumendsh-dock-overflowed{display:none!important}',
        '#lumendsh-dock-chevron{position:fixed!important;right:6px!important;',
        'bottom:calc(8px + env(safe-area-inset-bottom))!important;z-index:72!important;',
        'width:26px!important;height:24px!important;padding:0!important;border:0!important;',
        'border-radius:8px!important;background:rgba(30,30,36,.9)!important;color:#cfd3dc!important;',
        'font-size:12px!important;line-height:24px!important;text-align:center!important;',
        'display:none!important;user-select:none!important}',
        '#lumendsh-dock-flyout{position:fixed!important;right:6px!important;',
        'bottom:calc(40px + env(safe-area-inset-bottom))!important;z-index:71!important;',
        'max-width:72vw!important;max-height:40vh!important;overflow-y:auto!important;',
        'padding:10px 12px!important;background:rgba(18,18,22,.98)!important;',
        'border:1px solid rgba(127,127,127,.3)!important;border-radius:12px!important;',
        'box-shadow:0 10px 28px rgba(0,0,0,.45)!important;color:#cfd3dc!important;',
        'font-size:12px!important;line-height:18px!important;white-space:pre-wrap!important;',
        'display:none!important}',
        '#lumendsh-dock-flyout.lumendsh-open{display:block!important}',
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


    // Windows-taskbar style overflow for the composer dock: keep the bar where it
    // is, hide the children that do not fit, and expose them through a chevron
    // pinned at the bottom right. Generic: any dock child can overflow.
    function installOverflow() {
      if (typeof document === 'undefined' || document.getElementById('lumendsh-dock-chevron') !== null) return
      const chevron = document.createElement('button')
      chevron.id = 'lumendsh-dock-chevron'
      chevron.type = 'button'
      chevron.textContent = '\u2303'
      const flyout = document.createElement('div')
      flyout.id = 'lumendsh-dock-flyout'
      chevron.addEventListener('click', () => {
        const open = flyout.classList.toggle('lumendsh-open')
        chevron.textContent = open ? '\u2304' : '\u2303'
      })
      document.body.appendChild(chevron)
      document.body.appendChild(flyout)

      const layout = () => {
        const dock = document.querySelector('[data-composer-dock]')
        if (dock === null) return
        const children = Array.from(dock.children)
        // The bar keeps the official figures. Anything a plugin contributes to
        // the dock identifies itself with a surface marker (`data-mc-surface`,
        // the shell contract plugin pills use); those items move to the tray so
        // they cannot sit on top of the official meter.
        const isPlugin = child => child.matches('[data-mc-surface]') === true
          || child.querySelector('[data-mc-surface]') !== null
        const overflowed = []
        for (const child of children) child.classList.remove('lumendsh-dock-overflowed')
        for (const child of children) {
          if (!isPlugin(child)) continue
          child.classList.add('lumendsh-dock-overflowed')
          const text = (child.textContent || '').trim()
          if (text !== '') overflowed.push(text)
        }
        if (overflowed.length === 0) {
          chevron.style.setProperty('display', 'none', 'important')
          flyout.classList.remove('lumendsh-open')
          chevron.textContent = '\u2303'
          flyout.textContent = ''
          return
        }
        chevron.style.setProperty('display', 'block', 'important')
        flyout.textContent = overflowed.map(line => '· ' + line).join('\n')
      }
      layout()
      window.addEventListener('resize', layout)
      const dockObserver = new MutationObserver(() => layout())
      const attach = () => {
        const dock = document.querySelector('[data-composer-dock]')
        if (dock === null) return false
        dockObserver.observe(dock, {childList: true, subtree: true, characterData: true})
        layout()
        return true
      }
      if (!attach()) {
        const bodyObserver = new MutationObserver(() => { if (attach()) bodyObserver.disconnect() })
        bodyObserver.observe(document.body, {childList: true, subtree: true})
      }
    }

    function apply(ctx) {
      installStyles()
      if (typeof document !== 'undefined') {
        const start = () => {
          hideTideCapsule()
          const observer = new MutationObserver(() => hideTideCapsule())
          observer.observe(document.body, {childList: true, subtree: true})
          installOverflow()
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
