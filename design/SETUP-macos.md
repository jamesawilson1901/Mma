# MacBook setup — do this before you start building

Goal: get Claude Code + a way to run/test a web game + GitHub ready, so tomorrow's session
just works. ~10 minutes.

## 0. Prerequisite
You need a **Claude Pro or Max** plan. Claude Code does not work on the free plan.

## 1. Install Claude Code (native installer — no Node.js needed)
Open **Terminal** (Spotlight → "Terminal") and run:

```bash
curl -fsSL https://claude.ai/install.sh | bash
```

Then **open a new Terminal window** (so PATH updates) and verify:

```bash
claude --version
claude doctor      # health check; fixes most issues
```

If `command not found`, run `source ~/.zshrc` and try again.

## 2. Install Git + Node (Node powers a nice local dev server)
- **Git:** run `git --version`. If it prompts to install Xcode Command Line Tools, accept.
- **Node LTS (recommended):** download the LTS `.pkg` from https://nodejs.org and install,
  then verify `node --version` (want 18+). Node isn't required for Claude Code, but it gives
  you `npx serve` for live testing, and many web tools assume it.
  (You can skip Node — macOS ships `python3`, and `python3 -m http.server` also serves the game.)

## 3. Connect GitHub
- Sign in to https://github.com in your browser (you already have an account).
- First time Claude Code pushes, it'll walk you through auth. To pre-empt friction, install
  the **GitHub CLI** and log in once:
  ```bash
  brew install gh   # if you have Homebrew; else download from https://cli.github.com
  gh auth login     # choose GitHub.com → HTTPS → login via browser
  ```
  (If you don't have Homebrew, you can set this up during the build instead — not blocking.)

## 4. Make the project folder and launch
```bash
mkdir -p ~/wolf-knight && cd ~/wolf-knight
claude
```
On first launch: pick a theme, log in via the browser prompt.

## 5. Then paste the build prompt
With Claude running inside `~/wolf-knight`, paste the contents of **BUILD-PROMPT.md**.
Tip: press **Shift+Tab** to enter *plan mode* first if you want to review its plan before it
writes code. Keep the GDD handy to attach/paste if it asks for design specifics.

## 6. After the first deploy — turn on GitHub Pages (one-time, in the browser)
Once the repo is pushed: GitHub repo → **Settings → Pages** → Source: "Deploy from a branch"
→ Branch: `main`, folder: `/ (root)` → Save. Your game goes live at
`https://<your-username>.github.io/<repo-name>/` within a minute or two.

---

### Quick reference
- Resume yesterday's session: `claude --continue`
- Pick a past session: `claude --resume`
- Change model: `/model` (use the strongest available for architecture-heavy phases)
- Plan mode: **Shift+Tab**
