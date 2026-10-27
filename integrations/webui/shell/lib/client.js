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

    function ShellControls() {
      const api = typeof window !== 'undefined' ? window.LumenShell : undefined
      if (!api) return null
      return React.createElement('div', {
        style: {position: 'fixed', right: '14px', bottom: '112px', display: 'flex', flexDirection: 'column', gap: '10px', zIndex: 30},
      },
        React.createElement('button', {style: buttonStyle, title: '返回角色空间', onClick: () => api.home()}, '⌂'),
        React.createElement('button', {style: buttonStyle, title: '语音输入', onClick: () => api.voice()}, '🎤'),
      )
    }

    function apply(ctx) {
      const slots = ctx.get('slots')
      if (slots === undefined) return
      slots.inject('shell.overlay', () =>
        slots.register({name: 'shell.overlay', id: 'lumendsh-shell', order: 40}, ShellControls),
      )
    }

    exports.apply = apply
    exports.inject = inject
    return module.exports
  },
})
