"""Reviewed fixed-camera Patito pack, opt-in alongside the care preview artwork."""
from pathlib import Path
import json,hashlib
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[2]
CELL=256
DEST=ROOT/'app/src/carePreview/assets/pets/patito'
REVIEW=ROOT/'tools/patito/review'

def build():
    atlas=Image.new('RGBA',(CELL*4,CELL*4))
    sources=[]
    for index in range(16):
        path=(ROOT/f'app/src/main/res/drawable-nodpi/patito_{index}.png' if index<10 else REVIEW/f'walk-{index-10}.png')
        im=Image.open(path).convert('RGBA')
        # One calibration for the whole walk board, never per-pose alpha fitting.
        # Eye line (337/768 vs 160/512) and torso/tail match standing pose 8.
        scale=1.0 if index<10 else .8
        size=round(CELL*scale)
        sprite=im.resize((size,size),Image.Resampling.LANCZOS)
        x=round((CELL-size)*.5); y=round((CELL-size)*.92)
        cell=Image.new('RGBA',(CELL,CELL));cell.alpha_composite(sprite,(x,y))
        atlas.paste(cell,(index%4*CELL,index//4*CELL))
        sources.append({'frame':index,'source':str(path.relative_to(ROOT)),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'cellScale':scale,'offset':[x,y]})
    DEST.mkdir(parents=True,exist_ok=True)
    atlas.save(DEST/'patito_motion_v2.png',optimize=True)
    clips={'idle':([8],True,500),'walk':(list(range(10,16)),True,130),'swim':([0,1,2,3],True,250),
           'takeoff':([8,4,9],False,120),'flutter':([4,9],True,120),'land':([6,7,8],False,140),
           'play':([8,8,7,8],True,350),'sleep':([8],False,500),'wake':([8],False,300),'legacy_wing':([5],False,120)}
    spec={'version':2,'petId':'patito','atlasPath':'pets/patito/patito_motion_v2.png','frameWidth':CELL,'frameHeight':CELL,'columns':4,'rows':4,'frameCount':16,
          'pivot':{'x':128,'y':128},'renderHints':{'innerTransparentPaddingPx':4,'recommendedBleedInsetPx':0,'filterBitmap':True,'preserveFrameAnchors':True,'drawScale':1.0},
          'clips':[{'id':k,'frames':v[0],'loop':v[1],'frameDurationMs':v[2]} for k,v in clips.items()],
          'frames':[{'index':i,'name':f'legacy_{i}' if i<10 else f'walk_{i-10}'} for i in range(16)]}
    (DEST/'patito_motion_v2.json').write_text(json.dumps(spec,indent=2)+'\n')
    sheet=Image.new('RGB',atlas.size,'#f5f0e4');sheet.paste(atlas,mask=atlas.getchannel('A'));d=ImageDraw.Draw(sheet)
    for i in range(16):d.text((i%4*CELL+8,i//4*CELL+8),str(i),fill='black')
    sheet.save(REVIEW/'motion-atlas-review.png')
    (REVIEW/'motion-atlas-sources.json').write_text(json.dumps(sources,indent=2)+'\n')

if __name__=='__main__':build()
