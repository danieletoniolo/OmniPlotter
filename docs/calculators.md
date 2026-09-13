# Supported calculators

Every model OmniPlotter writes files for, and the formats each one takes.
[← Back to the README](../README.md)

| Brand | Models | Picture files | Python scripts |
|---|---|---|---|
| Casio | fx-CG10/20/50 · Graph 90+E | `cp.g3p` `cp01.g3p` `cp_i.g3p` `cp01_i.g3p` | `graphic_cg.py` `gint_cg.py` `kandinsky_cg.py` |
| Casio | fx-CG50/100 · Graph 90+E/Math+ | | `casioplot_cg.py` `graphic_cg.py` `gint_cg.py` `kandinsky_cg.py` |
| Casio | fx-CG100 · Graph Math+ | `cp01.g4p` `cp01_i.g4p` `cp.g3p` `cp01.g3p` `cp_i.g3p` `cp01_i.g3p` | |
| Casio | fx-CP400 · CG500 | `c2p` `i.c2p` | |
| Casio | fx-9750/9860GIII · Graph 35+E II | | `casioplot_g3.py` `graphic_g3.py` `gint_g3.py` |
| Casio | fx-9860G/GII · fx-9750GII · Graph 35+E/75/85/95 | | `graphic_g3.py` `gint_g3.py` |
| TI | 83 Premium CE · 84 Plus CE Python | `im8c.8xv` `8ca` `8ci` | `ti_graphics.py` `ti_draw_ce.py` `microbit.py` `ti_hub_mb.py` `ti_hub_rgbarr.py` |
| TI | 82 Advanced Python · 83 Premium CE · 84 Plus CE/CSE | `8ca` `8ci` | |
| TI | 82+ · 82 Advanced · 83+ · 84+ | `8xi` | |
| TI | 73 · 76 · 82 · 82 Stats · 83 · 85 · 86 | `8xi` `73i` `82i` `83i` `85i` `86i` | |
| TI | Nspire · CM · CX · CX II | | `nsp_ns.py` `nsp_cx.py` `graphic.py` `kandinsky.py` `ti_draw_cx.py` `microbit.py` `ti_hub_mb.py` `ti_hub_rgbarr.py` |
| NumWorks | N0100 · N0110 · N0120 | | `kandinsky.py` `graphic.py` |
| HP | Prime | | `hpprime.py` |
| Zero | ZGC1–ZGC4 | `zpic` | |

`omniplotter targets` lists every model with its identifier, and `omniplotter formats --target <id>`
the formats it takes.

Format and target identifiers are the same strings img2calc uses in its URLs, so a link from the web
tool translates directly into a command here.
