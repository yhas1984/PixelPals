# Home asset audit — 2026-09-08

Counts are distinct frame indices in the routed clip, not quality scores. Candidate art is debug-only.

| Pet | Source | Idle | Walk | Turn | Play | Sleep | Wake |
|---|---|---:|---:|---:|---:|---:|---|
| corgi | new candidate | 4 | 8 | 4 | 4 | 4 | 4 |
| taro | new candidate | 1 | 8 | 4 | 4 | 4 | 4 |
| bloop | legacy drawables | 1 | 2 | 1 | 2 | 1 | reversed sleep |
| nube_michi | legacy drawables | 1 | 2 | 1 | 2 | 1 | reversed sleep |
| jelly | legacy drawables | 1 | 4 | 1 | 2 | 1 | reversed sleep |
| ginger | ginger_sheet_v2.json | 1 | 4 | 1 (idle fallback) | 2 | 1 | 3 |
| angel | angel_sheet_v4.json | 4 | 2 | 4 (idle fallback) | 4 | 2 | reversed sleep |
| patito | legacy drawables | 1 | 2 | 1 | 2 | 1 | reversed sleep |
| diablillo | legacy drawables | 2 | 2 | 2 | 2 | 1 | reversed sleep |
| moki | moki_sheet_v1.json | 4 | 4 | 4 (idle fallback) | 4 | 4 | reversed sleep |
| yuki | yuki_sheet_v1.json | 3 | 2 | 3 (idle fallback) | 4 | 1 | reversed sleep |
| piru | piru_sheet_v1.json | 3 | 2 | 3 (idle fallback) | 4 | 2 | reversed sleep |
| menta | menta_sheet_v1.json | 3 | 4 | 3 (idle fallback) | 2 | 1 | reversed sleep |
| tela | tela_motion_v2.json | 4 | 8 | 4 (idle fallback) | 4 | 4 | reversed sleep |
| lumi | lumi_motion_v2.json | 4 | 8 | 4 | 4 | 4 | reversed sleep |

## Remaining art work

- Corgi and Taro: review candidate-to-walk scale and orientation in motion, refine planted turn drawings, and validate every care contact. New art does not yet replace desktop/care packs.
- Other 13 pets: dedicated wake transitions and review of action-specific clips remain; fallback idle/play clips are not accepted as finished animation.
- All pets: visual review of feed, play, clean, rest and touch, lifecycle/performance and physical acceptance remain separate from these structural checks.
- Do not promote candidate assets to release on the strength of this audit alone.
