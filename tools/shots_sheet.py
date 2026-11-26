from PIL import Image, ImageDraw
import glob,os,sys
fs=sorted(glob.glob(sys.argv[1]+'/zv_mod_*.png'))
W,H=640,360
for k in range(0,len(fs),6):
  sheet=Image.new('RGB',(W*3,H*2),'black')
  for i,f in enumerate(fs[k:k+6]):
    im=Image.open(f).convert('RGB').resize((W,H))
    d=ImageDraw.Draw(im); d.rectangle((0,0,300,18),fill='black'); d.text((4,3),os.path.basename(f)[7:-4],fill='yellow')
    sheet.paste(im,((i%3)*W,(i//3)*H))
  sheet.save(sys.argv[2]+'%d.png'%(k//6))
print(len(fs))
