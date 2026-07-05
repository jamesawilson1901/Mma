# Roll & Roar

A DKC-style 2D platformer for mobile browsers (PWA). Built from
`roll-and-roar-build-spec.md` — that file is the single source of truth.

## Quick start (dev)

```bash
cd roll-and-roar
npm install
npm run dev        # vite --host: prints a LAN URL
```

Open the printed `http://<your-lan-ip>:5173` URL on a phone that's on the
same Wi-Fi network. You should see the boot scene: title, bouncing square
(render loop), tap counter (touch input), FPS readout.

## Fetch the asset packs

```bash
./tools/fetch-assets.sh
```

Downloads and verifies every pack from the spec's manifest into
`assets/raw/` and writes the on-page licences into `CREDITS.md`. Needs
`curl` and `unzip`, and network access to opengameart.org. The Arks dino
pack (itch.io) needs one manual download — the script prints the
instruction.

## Documents

- `CREDITS.md` — every asset pack, author, licence, source
- `DECISIONS.md` — substitutions and judgement calls per milestone
