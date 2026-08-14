# Lumi Character Bible v1

## Role

Lumi is PixelPals' flagship baby fox. She is curious, affectionate, magical,
and physically readable at a small floating-overlay size. Her animation should
make her feel like a small living creature, not a menu of disconnected poses.

## Canonical Identity

These features are invariant and must remain recognizable in every approved
frame:

- Golden-orange baby fox body.
- Cream muzzle, chest fur, paws, ear interiors, and tail tip.
- Large cyan eyes with the established painted highlight style.
- Cream forehead tuft and cheek fur silhouette.
- Cyan star marking on the chest.
- Large expressive tail with a cyan glowing orb at its tip.
- Soft painted 2D finish with warm orange highlights and dark warm contours.
- Friendly baby-fox proportions: large ears and eyes, compact body, readable
  paws, and an oversized expressive tail.

## Canonical Views

The production set uses these view rules:

- Default gameplay facing: right-facing side or readable 3/4 side view.
- Front-facing views are reserved for social, surprise, magic, and approval
  poses where the face is the important signal.
- Do not flip a front-facing frame to fake a side-facing walk frame.
- Keep the orb and tail relationship believable when the body turns.

The approved turnaround reference is a design guide, not an animation atlas.
It defines proportions and volumes; it does not authorize arbitrary new views.

## Color And Lighting

- Warm orange/gold body remains the dominant color family.
- Cream areas stay warm and slightly off-white, never flat pure white.
- Cyan is reserved for eyes, star, orb, and intentional magic effects.
- Highlights follow one consistent light direction across a clip.
- Do not introduce neon colors, hard black fills, or a new outline language.
- Magic may brighten cyan locally but must not recolor Lumi's body.

## Shape Priorities

When a frame is small or crowded, preserve features in this order:

1. Overall fox silhouette and ears.
2. Tail volume and orb.
3. Face direction and eye expression.
4. Chest star.
5. Individual paws and small fur strands.

## Personality

Lumi follows these behavioral principles:

- Curious before brave.
- Play in short bursts.
- Return to a comfort spot.
- Seek affection after exertion.
- Sleep in long uninterrupted holds.

Her emotional read should progress through visible intent:

```text
observe -> notice -> investigate -> commit -> recover -> seek comfort
```

She is not frantic by default. Even energetic actions should return to a
settled, affectionate baseline.

## Approved Emotional Vocabulary

| Emotion | Visual read | Use |
|---|---|---|
| Neutral | relaxed eyes, balanced stance | default idle |
| Curious | head tilt, focused eyes, slightly raised ears | investigate |
| Playful | low crouch, forward weight, bright eyes | stalk/pounce |
| Joyful | open smile, lifted paws or small hop | reward/celebrate |
| Magical wonder | orb attention, cyan glow, focused face | magic |
| Surprised | widened eyes, raised posture, open mouth | startle |
| Drowsy | lowered lids, slack posture, yawn | sleep entry |
| Comfort | curled body, soft eyes, tail shelter | sleep/snuggle |

## Pose Invariants

- The chest star remains attached to the chest plane.
- The orb remains attached to the tail tip unless a magic pose explicitly
  shows Lumi holding or casting with it.
- Paws remain anatomically paired; no frame invents extra limbs.
- Ears remain the same relative size and base placement.
- Tail remains large and readable, but its curl may change with emotion.
- The head and eye line must agree with the direction of attention.
- Contact poses must have a believable weight-bearing paw arrangement.

## Master Pose Set

Before a complete atlas, approve these six master poses:

1. `idle_neutral`: standing, relaxed, ground contact.
2. `idle_breath_in`: same silhouette with a small chest lift.
3. `look_profile`: clean side-facing attention pose.
4. `walk_contact_right`: one readable weight-bearing contact pose.
5. `pounce_air`: compact airborne silhouette with clear direction.
6. `sleep_curl`: believable curled comfort pose.

These poses are the reference for later frames. A new pose that cannot be
explained as a controlled change from a master pose is reviewed again before
approval.

## Manual Retouch Checklist

For every AI-generated candidate:

- Correct eye count, pupils, highlights, and gaze direction.
- Correct ear shape, inner fur, and symmetry appropriate to the view.
- Correct paw count and contact placement.
- Rebuild the tail silhouette if it changes unpredictably.
- Reattach the orb and restore its cyan glow.
- Restore the chest star and its scale.
- Match the warm palette and light direction.
- Remove background, checkerboard, matte, text, and stray particles.
- Align the pivot and ground line.
- Compare at both full resolution and the expected phone display size.

## Source References

- Canonical painted reference: `/home/yhas/Pictures/pixelpals_refs/lumi.png`
- Action reference: `/home/yhas/Pictures/pixelpals_refs/lumi_action_sheet.png`
- Expressions reference: `/home/yhas/Pictures/pixelpals_refs/lumi_expressions_sheet.png`
- Turnaround reference: `/home/yhas/Pictures/pixelpals_refs/lumi_turnaround_sheet.png`
- Archived 16-pose source atlas: `tools/lumi/archive/v1/source_atlas/`
- Archived 32-pose action trial: `tools/lumi/archive/v1/source_atlas/action_trial_v1/`

The action trial is not a character bible. It is a useful pose bank with known
art consistency gaps and remains outside production promotion.
