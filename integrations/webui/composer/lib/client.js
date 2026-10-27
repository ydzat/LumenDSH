/**
 * @lumendsh/webui-composer
 *
 * Registers into the official composer activity slot and polls the companion
 * host for text that should appear in the composer (share handoff, voice
 * transcripts, external tools). The official inputActions service owns the
 * draft, so insertion goes through React state instead of DOM events.
 */
window.__ModuleLoader__.load({
  id: '@lumendsh/webui-composer',
  factory: (require) => {
    var module = { exports: {} }
    var exports = module.exports
    const React = require('react')
    const inject = ['slots']

    function ComposerBridge(props) {
      const actions = props?.inputActions
      React.useEffect(() => {
        if (!actions || typeof actions.insertText !== 'function') return undefined
        let stopped = false
        let lastSeq = 0
        const pull = async () => {
          try {
            const response = await fetch('/sliver-wolf/companion/composer/pending', {
              credentials: 'same-origin', headers: {accept: 'application/json'},
            })
            if (!response.ok) return
            const value = await response.json()
            if (stopped || !value || typeof value.text !== 'string' || !value.text) return
            if (Number(value.seq) <= lastSeq) return
            lastSeq = Number(value.seq)
            const span = typeof actions.captureInsertion === 'function' ? actions.captureInsertion() : undefined
            actions.insertText(value.text, span)
          } catch { /* host is restarting; retry on the next tick */ }
        }
        pull()
        const timer = setInterval(pull, 1200)
        return () => { stopped = true; clearInterval(timer) }
      }, [actions])
      return null
    }

    function apply(ctx) {
      const slots = ctx.get('slots')
      if (slots === undefined) {
        console.warn('[lumendsh-composer] slots service unavailable')
        return
      }
      slots.inject('conversation.input.activity', () =>
        slots.register({
          name: 'conversation.input.activity',
          id: 'lumendsh-composer',
          order: 30,
        }, ComposerBridge),
      )
    }

    exports.apply = apply
    exports.inject = inject
    return module.exports
  },
})
