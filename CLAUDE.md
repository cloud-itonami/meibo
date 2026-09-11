# meibo 名簿 — verified legal-institution directory registry

**DID**: `did:web:etzhayyim.com:actor:meibo` (registration deferred, matches
saisei's own R0 posture) · **Tier**: B · **Status**: 🟡 R0 (seed — 10
jurisdictions) · **ADR**: 2607062200 · **depends**: 2607061800 (saisei — the
pattern this actor generalizes) · 2606112301 + 2606112400 (tate — the
30-jurisdiction referral precedent this actor's worklist overlaps) ·
2607022300 (unified actor deploy — motivates self-containment)

## What this is

The honest, lawful fulfillment of a gap gftdcojp's ADR-0016 named in
2026-04-14 and never built: `judge` (200K judges), `bengoshi` (2.5M lawyers),
`adr` (1M ADR cases/yr), `legal-aid` (10M legal-aid cases/yr) were all planned
as bulk-data actors and none were ever implemented. meibo does **not**
attempt that scale — none of the underlying institutions publish a
bulk-exportable dataset, several restrict scraping in their ToS, and
individual professional records (bar numbers, disciplinary history) carry
their own data-protection weight to republish without the institution's own
participation. Instead: a **verified LINK registry** to the official search
tool each institution already runs — the same pattern already proven inside
saisei (`:proc/official-forms-url`, `legal_directory`), generalized into its
own actor and grown to 10 jurisdictions (`data/legal-directory.edn`, 23
entries).

**Second registry, added 2026-07-28** (`data/verification-window.edn`, 8
entries, Japan): the same verified-link pattern applied to a different
question — not "where do I look up a professional" but "someone claims to be
X; where does X itself publish its window, so I can hang up and call THAT."
Motivated by the 2026 corporate police-impersonation fraud wave (警察庁 2025:
27,832 recognized cases / ¥142.31B, of which ニセ警察詐欺 11,014 / ¥100.5B).
The system-dynamics reason it is a *lookup* and not a *classifier* is in G11
below.

## Hard gates (constitutional — read before any change)

- **G1 institution-level only.** No individual professional records — bar
  number, disciplinary history, docket, personal contact info. Only the
  institution's own official search-tool URL. A schema field for an
  individual's name/bar-number would violate this (tests enforce their
  absence).
- **G2 non-adjudicating.** meibo never says "this lawyer is good" — it points
  at the authoritative place to verify licensing/standing yourself.
- **G11 no-inbound-attestation** (2026-07-28, `verification-window` registry).
  meibo has **no API that takes an inbound number, caller-id, display name, or
  a callback number the caller supplied.** Caller-id is spoofable, so an
  "is this caller genuine?" endpoint fails in both directions: a match would
  attest to a spoofed number, and a non-match would be read as proof of fraud.
  `lookup` therefore returns the constant verdict `:call-the-published-window`
  for every input, including uncovered claims and uncovered jurisdictions, and
  its guidance says explicitly that an uncovered claim is *not* evidence the
  caller is genuine. Tests enforce this as an absence: no schema key may match
  `caller|inbound|allowlist|whitelist|known-good|trusted-number`, and the
  verdict must be identical across covered/uncovered/nonsense inputs.

  *Why this is the load-bearing design decision*: in the はてな 2026-04-20
  incident the bank's own screening fired **18.5% of the way into the transfer
  window** and delivered nothing, because its output terminated at the person
  under attack, who could lift the hold. Improving a judgement does not help
  when the judgement itself is what is under attack. This registry works by
  **replacing the judgement with a lookup**, and adding an attestation endpoint
  would put a judgement right back in.
- **G10 jurisdiction/provenance honesty.** Every `:dir/url` was verified live
  (WebSearch/WebFetch) before being recorded — never guessed or recalled from
  memory. `coverage_report.cljc` names the ~183 uncovered jurisdictions as an
  explicit worklist (entries drop off automatically as they're covered),
  never silently claims coverage it doesn't have.

## Non-goals

N1 does not ingest/cache/republish individual professional records · N2 does
not rank or recommend a specific professional/firm · N3 does not become a
code-level `require` dependency of saisei/tate/toritsugi (see "Actor
independence" below).

## Actor independence (why this stays self-contained)

Every Tier-B actor here is meant to be split into its own standalone GitHub
repo by `actor:publish` (ADR-2607022300). That's why meibo carries its own
`methods/edn.cljc` copy rather than requiring a shared library namespace —
had saisei's `filing_plan.cljc` instead done
`(require '[meibo.methods.directory ...])`, splitting saisei into
`com-etzhayyim-saisei` would silently break the moment meibo's files aren't
present in that new repo. Consumers (saisei's own 4-jurisdiction
`legal_directory` stays as its own self-contained copy; a future tate wave)
are expected to consume meibo via its own public API surface (once deployed)
or a synced data snapshot — never a source-level dependency.

## Layout

```
20-actors/meibo/
├── CLAUDE.md                # this file
├── README.md
├── manifest.edn              # actor manifest (0 cells — link-registry only, 3 gates, 3 non-goals)
├── data/
│   ├── legal-directory.edn   # 23 entries × 10 jurisdictions, each :dir/url verified live
│   └── verification-window.edn # 8 entries (jp), impersonation-claim → published window (G11)
├── methods/                  # clj/bb (.cljc) — kotoba-native, self-contained
│   ├── edn.cljc              # minimal EDN reader (own copy — see Actor independence)
│   ├── directory.cljc        # by-jurisdiction / jurisdictions-covered
│   ├── coverage_report.cljc  # honest jurisdiction coverage + named gaps (G10)
│   └── verification_window.cljc # claim → published window; outbound-only (G11)
├── tests/                    # clj/bb (.cljc) — bb run_tests.cljk (26 tests / 428 assertions)
│   ├── test_directory.cljc
│   ├── test_coverage.cljc
│   └── test_verification_window.cljc
└── run_tests.sh
```

## Run

```bash
bb run_tests.cljk   # full suite: 26 tests / 428 assertions green

bb --classpath 20-actors -e '(require (quote [meibo.methods.coverage-report :as c])) (print (c/report (c/coverage)))'
```

## Do not

- Do not add a per-individual field (attorney name, bar number, disciplinary
  record) to `data/legal-directory.edn` — G1 (tests enforce their absence).
- Do not record a `:dir/url` you have not verified live — G10. Adding a
  jurisdiction = verify the institution's real URL (WebSearch/WebFetch), then
  one EDN entry + a test; no code change.
- Do not `require` this namespace from another actor's `.cljc` — see Actor
  independence above. Consume via meibo's own API surface or a data snapshot.
