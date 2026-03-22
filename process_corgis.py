from PIL import Image, ImageDraw

def process(input_path, output_path):
    img = Image.open(input_path).convert("RGBA")
    
    bg_mask = Image.new("L", img.size, 255)
    ImageDraw.floodfill(bg_mask, (0, 0), 0, thresh=50) 
    ImageDraw.floodfill(bg_mask, (img.size[0]-1, 0), 0, thresh=50)
    ImageDraw.floodfill(bg_mask, (0, img.size[1]-1), 0, thresh=50)
    ImageDraw.floodfill(bg_mask, (img.size[0]-1, img.size[1]-1), 0, thresh=50)
    
    white_bg = Image.new("RGBA", img.size, (255, 255, 255, 255))
    white_bg.putalpha(bg_mask)
    
    final = Image.alpha_composite(white_bg, img)
    final.save(output_path)
    print("Saved", output_path)

D='/home/yhas/.gemini/antigravity/brain/09256ffe-f192-4fa0-8552-263bd57034b5/'
# Mapping the 6 images to 6 storyperros
process(D+'media__1774174191468.png', 'app/src/main/res/drawable/storyperro_1.png')
process(D+'media__1774174191410.png', 'app/src/main/res/drawable/storyperro_2.png')
process(D+'media__1774174191405.png', 'app/src/main/res/drawable/storyperro_3.png') # walk 2
process(D+'media__1774174191346.png', 'app/src/main/res/drawable/storyperro_4.png')
process(D+'media__1774174191232.png', 'app/src/main/res/drawable/storyperro_5.png') # belly rub
process(D+'media__1774174191468.png', 'app/src/main/res/drawable/storyperro_6.png') # idle (same as storyperro_1)

