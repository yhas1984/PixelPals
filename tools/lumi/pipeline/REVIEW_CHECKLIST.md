# Lumi V2 Review Checklist

The debug activity now contains a deterministic review panel. It is the gate
before any Lumi V2 art is considered for production.

## Review Controls

1. Open `LumiDebugActivity` with overlay permission enabled.
2. Choose `Direction` to mirror the canonical right-facing clips.
3. Choose `Speed: 0.25x` for pose inspection or `Speed: 1x` for timing.
4. Select one clip at a time.
5. Use `Autonomous` only after the clip review is complete.

The overlay is made non-touchable while reviewing so it cannot cover the
activity controls. `Autonomous` restores normal sprite touch input.

## Acceptance Gates

| Clip | Must pass |
| --- | --- |
| `idle` | Same silhouette, no visible head bob, breathing reads as one loop. |
| `walk` | Eight frames keep the same scale and baseline; paws do not slide; both directions read forward. |
| `turn` | Side -> three-quarter -> front -> opposite side; root stays in place and no frame jumps. |
| `hop_up` | Anticipation, takeoff, airborne and landing read in order; movement advances in the facing direction. |
| `hop_down` | Airborne, contact, settle and recovery read in order; the lower landing is believable. |
| `front_social` | Front-facing identity is stable; blink and paw lift do not change anatomy. |
| `pounce` | Short playful burst; no backward travel, foot sliding or detached orb. |
| `sleep` | Drowsy -> curl transition is calm; the curled loop breathes without changing scale or losing the orb. |
| `magic` | Paw, orb glow and recovery stay attached to the same character; no extra objects or background effects. |

Reject a clip if the problem cannot be fixed by frame ordering, mirroring,
cropping, or pivot normalization. Regenerate only the rejected board and keep
the request at `quality=low` unless a medium-quality retry is justified.
