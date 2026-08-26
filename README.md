# Cetori Privates — Fabric 26.2

Server-side land-claim mod for Minecraft 26.2.

## Claim blocks

| Block | Area |
|---|---:|
| Iron Block | 10×10 |
| Gold Block | 15×15 |
| Diamond Block | 20×20 |
| Emerald Block | 25×25 |
| Netherite Block | 30×30 |

Place one of the five blocks to create a claim centered on it.

## Commands

- `/rg info` — info about the claim at your position
- `/rg info <id>` — info about a claim
- `/rg addmember <id> <player>` — add a member
- `/rg removemember <id> <player>` — remove a member
- `/rg remove <id>` — remove your claim
- `/rg list` — list your claims
- `/rg trust <id> <player>` — alias for addmember
- `/rg untrust <id> <player>` — alias for removemember
- `/rg admin remove <id>` — operator-only removal

Claims are saved to `cetori_privates.json` in the world root.
