import os
from PIL import Image, ImageDraw

base_img_path = 'app/src/main/res/drawable/pet_corgi.png'
out_dir = 'app/src/main/res/drawable/'

if not os.path.exists(base_img_path):
    print("Base image not found!")
    exit(1)

img = Image.open(base_img_path).convert("RGBA")

# Frame 0: Sentado (Idle)
img.save(os.path.join(out_dir, 'corgi_0.png'))
print("corgi_0.png generated")

# Frames 1, 2, 3: Ciclo de carrera
# Frame 1: Up and rotated right
img1 = img.rotate(-10, expand=False, fillcolor=(0,0,0,0))
img1.save(os.path.join(out_dir, 'corgi_1.png'))
# Frame 2: Squashed horizontal stretch
img2 = img.resize((img.width + 40, img.height - 40)).crop((20, -20, img.width + 20, img.height - 20))
img2.save(os.path.join(out_dir, 'corgi_2.png'))
# Frame 3: Up and rotated left
img3 = img.rotate(10, expand=False, fillcolor=(0,0,0,0))
img3.save(os.path.join(out_dir, 'corgi_3.png'))

# Frames 4, 5: Acariciar
img4 = img.resize((img.width + 20, img.height - 20)).crop((10, -10, img.width + 10, img.height - 10))
img4.save(os.path.join(out_dir, 'corgi_4.png'))

img5 = img.resize((img.width + 60, img.height - 40)).crop((30, -20, img.width + 30, img.height - 20))
img5.save(os.path.join(out_dir, 'corgi_5.png'))

# Frame 6: Panza arriba (giro 180)
img6 = img.rotate(180, expand=False, fillcolor=(0,0,0,0))
img6.save(os.path.join(out_dir, 'corgi_6.png'))
print("All 7 Corgi frames generated.")
