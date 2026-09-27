# SysPort MARJAN — Developer Wiki

The internal knowledge base for the **SysPort-MARJAN** transport management system. Built with
[Astro](https://astro.build) + [Starlight](https://starlight.astro.build). It runs **fully locally** —
no deployment required.

## Run it

```bash
cd webdocs
npm install
npm run dev        # http://localhost:4321
```

| Command | Action |
| --- | --- |
| `npm run dev` | Local dev server at `localhost:4321` |
| `npm run build` | Static build into `./dist/` |
| `npm run preview` | Preview the built site |
| `npm run astro check` | Type-check content |

## Deploy to GitHub Pages

The site is published by the workflow at
[`.github/workflows/deploy-docs.yml`](../.github/workflows/deploy-docs.yml) to
**<https://dev.aldrete.github.io/sysport-marjan/>**.

- Triggers on pushes to `main` that touch `webdocs/**` (and on manual dispatch).
- Builds with `GITHUB_PAGES=true`, which makes `astro.config.mjs` set
  `site: https://dev.aldrete.github.io` and `base: /sysport-marjan`.
- Deploys via the official Pages actions (`configure-pages` → `upload-pages-artifact` → `deploy-pages`).

### One-time setup

In the repository: **Settings → Pages → Build and deployment → Source: GitHub Actions**.
No `gh-pages` branch is needed.

### Deploying from another branch

The default trigger is `main`. To publish a preview from `develop`, run the workflow manually
(**Actions → Deploy docs to GitHub Pages → Run workflow**) and pick the branch.

> Locally, `base` is `/`, so the dev server stays at <http://localhost:4321/>. The `base` is only
> applied when `GITHUB_PAGES=true`.

## What's inside

Content lives in `src/content/docs/` as Markdown/MDX. The sidebar is defined in `astro.config.mjs`.

```text
src/content/docs/
├── index.mdx            splash home
├── start/               overview, local setup, first change, testing
├── architecture/        layers, database access, UI toolkit, conventions
├── database/            schema, functions, procedures, views, seed, migrations
├── domain/              lifecycles, business rules, assignment, costs, invoicing
├── features/            one guide per package
└── reference/           Java class map, glossary, troubleshooting
```

## Adding or editing a page

1. Create `src/content/docs/<group>/<page>.md` with frontmatter:

   ```markdown
   ---
   title: My page
   description: One sentence shown in search and previews.
   ---
   ```

2. Add it to the `sidebar` array in `astro.config.mjs`:

   ```js
   { label: 'My page', slug: 'group/page' },
   ```

3. `npm run build` to verify (it reports broken builds).

## Conventions for wiki pages

- Keep code snippets in sync with the real source — prefer linking to `file:line` where useful.
- Reference rule ids (`BR-xx`) and requirement ids (`FR-*`) so code and docs cross-link.
- Starlight asides: `:::note`, `:::tip`, `:::caution`, `:::danger`.

> This wiki documents the code **as it is**. Where `PRD.md` describes planned classes that were not
> built (rules live in the database, transactions live in procedures), the wiki describes reality.
