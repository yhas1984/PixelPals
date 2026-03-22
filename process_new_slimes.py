import os
import glob
from PIL import Image

def remove_background(input_path, output_path):
    print(f"Processing {input_path} -> {output_path}")
    try:
        img = Image.open(input_path).convert("RGBA")
        datas = img.getdata()
        
        # we will use a flood fill algorithm from the corners to find the background
        # and turn it transparent.
        # But for stability, any pixel that is very close to white, we make it transparent.
        # Let's inspect the corner color.
        bg_col = datas[0]
        
        newData = []
        for item in datas:
            # Check if it's very close to white or pure white
            if item[0] > 240 and item[1] > 240 and item[2] > 240:
                newData.append((255, 255, 255, 0))
            else:
                newData.append(item)
                
        img.putdata(newData)
        # Crop the transparent border if we want? The size should be kept.
        img.save(output_path, "PNG")
        print(f"Success: {output_path}")
    except Exception as e:
        print(f"Error processing {input_path}: {e}")

images_dir = "/home/yhas/.gemini/antigravity/brain/e4eda4d5-89d5-40ed-971e-29bfe95f38f0"
out_dir = "/media/yhas/_dde_data/home/yhas/Programacion/MascotasAndroidAntigravity/app/src/main/res/drawable"

# 1774177515266.png -> Image 1 (Maybe idle 1)
# 1774177515463.png -> Image 2 (idle 0)
# 1774177515639.png -> Image 3 (Squash)
# 1774177515653.png -> Image 4 (Tall)

remove_background(os.path.join(images_dir, "media__1774177515463.png"), os.path.join(out_dir, "slide_0.png"))
remove_background(os.path.join(images_dir, "media__1774177515266.png"), os.path.join(out_dir, "slide_1.png"))
remove_background(os.path.join(images_dir, "media__1774177515639.png"), os.path.join(out_dir, "slide_2.png"))
remove_background(os.path.join(images_dir, "media__1774177515653.png"), os.path.join(out_dir, "slide_3.png"))
