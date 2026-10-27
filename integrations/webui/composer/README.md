# @lumendsh/webui-composer

Official-page composer bridge. Registers a headless component in
`conversation.input.activity`, receives the official `inputActions`, and polls
`/sliver-wolf/companion/composer/pending` for text to insert (share handoff,
voice transcripts, external tools). This is the stable alternative to injecting
DOM events into a React-controlled editor.

Install as a profile layer:

```sh
dsh plugin --profile web add "link:$PWD/integrations/webui/composer"
```

The companion host exposes:

- `POST /sliver-wolf/companion/composer` `{text}` — queue text.
- `GET /sliver-wolf/companion/composer/pending` — consume the queued text.
