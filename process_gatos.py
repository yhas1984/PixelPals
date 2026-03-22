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
# 376 is the tight sleeping cat
process(D+'media__1774173796376.png', 'app/src/main/res/drawable/gato_0.png')
# 347 is sleeping cat with a bubble
process(D+'media__1774173796347.png', 'app/src/main/res/drawable/gato_1.png')
# 327 is a falling feather
process(D+'media__1774173796327.png', 'app/src/main/res/drawable/gato_2.png')
# 302 is the other falling feather
process(D+'media__1774173796302.png', 'app/src/main/res/drawable/gato_3.png')
# 286 is the cat completely awake!
process(D+'media__1774173796286.png', 'app/src/main/res/drawable/gato_4.png')
