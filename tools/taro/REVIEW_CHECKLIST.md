# Taro V2 Review Checklist

## Technical

- [ ] Atlas is `3072x1920` RGBA with 40 contiguous frames.
- [ ] Every frame has at least 16 px transparent padding.
- [ ] Pivot is `{ "x": 192, "y": 368 }`.
- [ ] All required clips exist and cover every frame.
- [ ] Debug asset and JSON load without fallback frames.

## Visual

- [ ] Taro stays on model in all frames.
- [ ] Shell, orange rim, plastron, eyes, cheeks, flippers, and claws remain stable.
- [ ] Walk has a readable contact line and no foot sliding.
- [ ] Hide and peek read as one continuous action.
- [ ] Sleep loop is calm and grounded.
- [ ] Front social and touch recovery preserve the friendly identity.

## Device

- [ ] Review every clip at normal speed and 0.25x.
- [ ] Review both facing directions.
- [ ] Test tap, drag, release, and autonomous movement.
- [ ] Run for 30 minutes without crashes or runaway layout updates.
