from pathlib import Path
import json,hashlib
import numpy as np
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'tools/bloop/review';OUT.mkdir(exist_ok=True)
SHADOW={'fantasma_1':590,'fantasma_2':582,'fantasma_5':590,'fantasma_7':590,'fantasma_8':555,'pet_bloop':416}
TAIL={'fantasma_1':[(285,282),(292,280),(316,278),(350,273),(380,265),(415,251),(424,245)],
 'fantasma_5':[(285,282),(292,280),(316,278),(350,273),(380,265),(415,251),(424,245)],
 'fantasma_7':[(285,282),(292,280),(316,278),(350,273),(380,265),(415,251),(424,245)],
 'pet_bloop':[(180,176),(182,175),(197,172),(225,170),(259,162),(280,152),(284,149)]}
report={}
paths=sorted((ROOT/'tools/bloop/source').glob('fantasma_*.png'))+[ROOT/'tools/bloop/source/pet_bloop.png']
sheet=Image.new('RGB',(1200,640),(239,234,221));d=ImageDraw.Draw(sheet)
for i,p in enumerate(paths):
 im=Image.open(p).convert('RGBA');original=np.array(im);arr=original.copy();key=p.stem
 if key in SHADOW:arr[SHADOW[key]:,:,3]=0
 if key in TAIL:
  pts=np.array(TAIL[key]);
  for y in range(int(pts[0,0]),int(pts[-1,0])+1):
   edge=float(np.interp(y,pts[:,0],pts[:,1])); x=int(edge)
   arr[y,:x,3]=0
   arr[y,x,3]=int(arr[y,x,3]*(1-(edge-x)))
 if key=='fantasma_4':
  mask=Image.new('L',im.size,255);md=ImageDraw.Draw(mask)
  md.rectangle((0,609,767,767),fill=0)
  # Preserve the tapering spectral wisp while erasing the flat portal underneath.
  md.polygon([(368,609),(429,609),(411,614),(401,618),(400,622),(409,629),(428,633),(438,637),(445,645),(440,654),(435,657),(432,650),(422,645),(399,642),(383,636),(375,627),(369,618)],fill=255)
  arr[:,:,3]=np.minimum(arr[:,:,3],np.array(mask))
 cleaned=Image.fromarray(arr);cleaned.save(OUT/p.name)
 changed=np.any(arr!=original,axis=2)
 report[p.name]={'sourceSha256':hashlib.sha256(p.read_bytes()).hexdigest(),'candidateSha256':hashlib.sha256((OUT/p.name).read_bytes()).hexdigest(),'changedPixels':int(changed.sum()),'rgbUnchanged':bool(np.array_equal(arr[:,:,:3],original[:,:,:3])),'size':im.size}
 thumb=cleaned.copy();thumb.thumbnail((290,290));sheet.paste(thumb,((i%4)*300,(i//4)*320),thumb);d.text(((i%4)*300+5,(i//4)*320+300),p.name,fill=(20,20,20))
sheet.save(OUT/'review.png');(OUT/'report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
