#!/usr/bin/env python3
"""Report the actual home clip routing; frame counts are not artistic acceptance."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
PETS = 'corgi taro bloop nube_michi jelly ginger angel patito diablillo moki yuki piru menta tela lumi'.split()
LEGACY = {'corgi': [1, 4, 1, 2, 2], 'bloop': [1, 2, 1, 2, 1], 'nube_michi': [1, 2, 1, 2, 1],
          'jelly': [1, 4, 1, 2, 1], 'patito': [1, 2, 1, 2, 1], 'diablillo': [2, 2, 2, 2, 1]}


def check_candidate(path):
    spec = json.loads(path.read_text())
    image = Image.open(path.with_suffix('.png')).convert('RGBA')
    assert image.size == (spec['columns'] * spec['frameWidth'], spec['rows'] * spec['frameHeight'])
    for i in range(spec['frameCount']):
        x, y = i % spec['columns'] * spec['frameWidth'], i // spec['columns'] * spec['frameHeight']
        frame = image.crop((x, y, x + spec['frameWidth'], y + spec['frameHeight']))
        bounds = frame.getchannel('A').getbbox()
        assert bounds and min(bounds[0], bounds[1]) > 0, (path, i, bounds)
        assert bounds[2] < frame.width and abs(bounds[3] - spec['pivot']['y']) <= 3, (path, i, bounds)
    assert [f['index'] for f in spec['frames']] == list(range(spec['frameCount']))
    for clip in spec['clips']:
        assert clip['frames'] and all(0 <= i < spec['frameCount'] for i in clip['frames'])
    return spec


def main():
    lines = ['# Home asset audit — 2026-09-08', '',
             'Counts are distinct frame indices in the routed clip, not quality scores. Candidate art is debug-only.', '',
             '| Pet | Source | Idle | Walk | Turn | Play | Sleep | Wake |',
             '|---|---|---:|---:|---:|---:|---:|---|']
    total_cells = 0
    for pet in PETS:
        candidate = ROOT / f'app/src/debug/assets/companion/pets/{pet}.json'
        choices = list((ROOT / f'app/src/main/assets/pets/{pet}').glob('*.json'))
        choices = sorted((p for p in choices if not p.name.startswith('care')), key=lambda p: ('motion_v2' in p.name, p.name), reverse=True)
        if candidate.exists():
            spec, source = check_candidate(candidate), 'new candidate'
            total_cells += spec['frameCount']
        elif choices:
            spec, source = json.loads(choices[0].read_text()), choices[0].name
        else:
            counts = LEGACY[pet]
            lines.append(f'| {pet} | legacy drawables | ' + ' | '.join(map(str, counts)) + ' | reversed sleep |')
            continue
        clips = {c['id']: c['frames'] for c in spec['clips']}
        def choose(*names):
            return next((clips[n] for n in names if n in clips), next(iter(clips.values())))
        idle = choose('idle', 'sit', 'perch_loop', 'hover')
        selected = [idle, choose('walk', 'crawl_loop', 'right', 'glide', 'hover'), clips.get('turn', idle),
                    choose('play', 'playful_delight', 'happy', 'front_social', 'groom', 'grace', 'tongue_strike', 'idle'),
                    choose('blink', 'idle') if pet == 'menta' else choose('sleep', 'prayer', 'perch_loop', 'idle')]
        numbers = [str(len(set(c))) for c in selected]
        if 'turn' not in clips: numbers[2] += ' (idle fallback)'
        counts = ' | '.join(numbers)
        wake = str(len(set(clips['wake']))) if 'wake' in clips else 'reversed sleep'
        lines.append(f'| {pet} | {source} | {counts} | {wake} |')
    lines += ['', '## Remaining art work', '',
              '- Corgi and Taro: review candidate-to-walk scale and orientation in motion, refine planted turn drawings, and validate every care contact. New art does not yet replace desktop/care packs.',
              '- Other 13 pets: dedicated wake transitions and review of action-specific clips remain; fallback idle/play clips are not accepted as finished animation.',
              '- All pets: visual review of feed, play, clean, rest and touch, lifecycle/performance and physical acceptance remain separate from these structural checks.',
              '- Do not promote candidate assets to release on the strength of this audit alone.', '']
    destination = ROOT / 'docs/companion/MOTION-ASSET-AUDIT.md'
    destination.write_text('\n'.join(lines))
    print(f'15 pet routes audited; {total_cells} candidate atlas cells validated')


if __name__ == '__main__':
    main()
