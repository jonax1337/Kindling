"""Regenerate the authored pixel-leaf SVG/PNG brand kit.
Run: python docs/branding/generate_pixel_brand.py
Dependencies: Pillow, fonttools. No network or model calls.
"""
from io import BytesIO
import json
from pathlib import Path
from xml.sax.saxutils import escape
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "pixel-leaf"
COLORS = {"forest":"#125231", "leaf":"#6BCB35", "ember":"#FF681E", "gold":"#FFC52B",
          "paper":"#FAF7EF", "ink":"#102D20", "night":"#0B1710", "surface":"#11251A", "muted":"#ADBEAC"}
# D shaded leaf, L leaf, O flame, Y ember; V and dots are transparent.
MARK = [
    "..........................YY....",
    "..........................YY....",
    "................................",
    "........................OO......",
    ".......................OOO......",
    ".......................OOO......",
    "....................OOOOOO......",
    "....................OOOOYOO.....",
    "...................OOOOYYOO.....",
    "................OOOOOOYYYOO.....",
    "................OOOOOYYYYOO.....",
    ".............OOOOOOOYYYYOOO.....",
    "...........LOOOOOOOYYYYYOOO.....",
    "..........LLOOOLOOYYYYYOOOO.....",
    ".........LLLOOLLOYYYYOOOOOO.....",
    ".........LLOOLLLLOOOOYYOOO......",
    "........LLLLLLLLLOOOOYYOOO......",
    ".......LLLLLLLLLOOOVVYOOOO......",
    ".......LLLLLLLLOOVVVOOOOO.......",
    "......LLLLLLLLLLVVVLOOOOO.......",
    ".....LLDLLLLLLVVVLLLOOOO........",
    ".....LDDLLLLVVVLLLLLOOLL........",
    "....DDDDLLVVVLLLLLLLLLL.........",
    "....DDDDLVVLLDLLLLLLLL..........",
    "....DDDDVVDDDDLLLLLLLL..........",
    "....DDDVVDDDDLLLLLLLL...........",
    "....DDVVDDDDDDDLLLLL............",
    "....DDVDDDDDDDDDLLL.............",
    "....DDDDDDDDDDDLLL..............",
    "...DDDDDDDDDLLL.................",
    "..DDDD..........................",
    "..DDD...........................",
]
# Original bold pixel lettering; no font dependency for the wordmark.
GLYPHS = {
    "K":["11000111","11001110","11011100","11111000","11110000","11111000","11011100","11001110","11000111","00000000","00000000"],
    "i":["11","11","00","11","11","11","11","11","11","00","00"],
    "n":["0000000","0000000","0000000","1111110","1111111","1100011","1100011","1100011","1100011","0000000","0000000"],
    "d":["0000011","0000011","0000011","0111111","1111111","1100011","1100011","1111111","0111111","0000000","0000000"],
    "l":["11","11","11","11","11","11","11","11","11","00","00"],
    "g":["0000000","0000000","0000000","0111111","1111111","1100011","1111111","0111111","0000011","1111111","1111110"],
}
WORD_WIDTH = sum(len(GLYPHS[c][0]) for c in "Kindling") + 7
PALETTE = {
    "mark":COLORS,
    "light":{"bg":COLORS["paper"], "fg":COLORS["ink"], "ember":"#C5410B", "muted":"#516553", "line":"#D6DDCF"},
    "dark":{"bg":COLORS["night"], "surface":COLORS["surface"], "fg":COLORS["paper"],
            "ember":"#FF8B4C", "leaf":"#86DC57", "muted":COLORS["muted"], "line":"#2C4433"},
}
FONT = instantiateVariableFont(TTFont(ROOT / "fonts" / "Manrope.ttf"), {"wght":650}, inplace=True)
FONT_BUFFER = BytesIO()
FONT.save(FONT_BUFFER)
FONT_BYTES = FONT_BUFFER.getvalue()
GLYPH_SET = FONT.getGlyphSet()
CMAP = FONT.getBestCmap()
UPM = FONT["head"].unitsPerEm

class Canvas:
    """The same authored geometry produces vector and raster exports."""
    def __init__(self, width, height, background=None):
        self.width, self.height = width, height
        self.image = Image.new("RGBA", (width,height))
        self.draw = ImageDraw.Draw(self.image)
        self.svg = []
        if background:
            self.rect(0,0,width,height,background)

    def rect(self,x,y,width,height,color):
        self.svg.append(f'<rect x="{x:g}" y="{y:g}" width="{width:g}" height="{height:g}" fill="{color}" shape-rendering="crispEdges"/>')
        self.draw.rectangle((round(x),round(y),round(x+width)-1,round(y+height)-1), fill=color)

    def text(self,text,x,baseline,size,color):
        scale, cursor = size/UPM, x
        for character in text:
            glyph_name = CMAP[ord(character)]
            pen = SVGPathPen(GLYPH_SET)
            GLYPH_SET[glyph_name].draw(pen)
            self.svg.append(f'<path d="{pen.getCommands()}" transform="translate({cursor:g} {baseline:g}) scale({scale:g} {-scale:g})" fill="{color}"/>')
            cursor += FONT["hmtx"][glyph_name][0]*scale
        font = ImageFont.truetype(BytesIO(FONT_BYTES),size)
        self.draw.text((x,baseline),text,font=font,fill=color,anchor="ls")

    def grid(self,rows,x,y,unit,palette):
        for row_index,row in enumerate(rows):
            start = 0
            while start < len(row):
                cell, end = row[start], start+1
                while end < len(row) and row[end] == cell:
                    end += 1
                if color := palette.get(cell):
                    self.rect(x+start*unit,y+row_index*unit,(end-start)*unit,unit,color)
                start = end

    def mark(self,x,y,unit,mono=None,dark=False):
        palette = dict(zip("DLOY",[COLORS["forest"],COLORS["leaf"],COLORS["ember"],COLORS["gold"]]))
        if dark:
            palette["D"] = "#337B43"
        if mono:
            # Keep the vein open in monochrome printing.
            palette = {key:mono for key in "DLOY"}
        self.grid(MARK,x,y,unit,palette)

    def word(self,x,y,unit,color):
        for character in "Kindling":
            glyph = GLYPHS[character]
            self.grid(glyph,x,y,unit,{"1":color})
            x += (len(glyph[0])+1)*unit

    def save(self,name,folder=OUT,transparent=True,vector=True):
        folder.mkdir(parents=True,exist_ok=True)
        path = folder / name
        self.image.save(path.with_suffix(".png"),optimize=True)
        if vector:
            svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" viewBox="0 0 {self.width} {self.height}"><title>{escape("Kindling — "+name)}</title>'+"".join(self.svg)+"</svg>\n"
            path.with_suffix(".svg").write_text(svg,encoding="utf-8")
        return {"file":str(path.with_suffix(".svg" if vector else ".png").relative_to(ROOT)).replace("\\","/"),
                "width":self.width,"height":self.height,"transparent":transparent}

def build():
    assert len(MARK) == 32 and all(len(row) <= 32 for row in MARK)
    exports = []
    for name,mono,dark in [("icon-color",None,False),("icon-inverse",None,True),
                           ("icon-mono-black","#000000",False),("icon-mono-white","#FFFFFF",True)]:
        c = Canvas(1024,1024)
        c.mark(64,64,28,mono,dark)
        exports.append(c.save(name))
    for theme in ("light","dark"):
        bg,fg = PALETTE[theme]["bg"],PALETTE[theme]["fg"]
        dark = theme == "dark"
        c = Canvas(1600,480)
        c.word((1600-WORD_WIDTH*30)//2,95,30,fg)
        exports.append(c.save("wordmark-"+theme))
        c = Canvas(1600,420)
        c.mark(24,34,11,dark=dark)
        c.word(478,111,22,fg)
        exports.append(c.save("logo-horizontal-"+theme))
        c = Canvas(1024,1024)
        c.mark(192,40,20,dark=dark)
        c.word((1024-WORD_WIDTH*18)//2,748,18,fg)
        exports.append(c.save("logo-stacked-"+theme))
        c = Canvas(1024,1024,bg)
        c.mark(192,192,20,dark=dark)
        exports.append(c.save("avatar-"+theme,transparent=False))
        c = Canvas(1600,500,bg)
        c.mark(60,58,12,dark=dark)
        c.word(524,116,21,fg)
        c.text("Vanilla behaviour. Every change measured.",524,392,29,PALETTE[theme]["muted"])
        exports.append(c.save("banner-"+theme,transparent=False))
        c = Canvas(1280,640,bg)
        c.mark(64,80,15,dark=dark)
        c.word(580,208,13,fg)
        c.text("Built on Leaf.",580,410,29,PALETTE[theme]["muted"])
        c.text("Made for your world.",580,453,29,PALETTE[theme]["muted"])
        exports.append(c.save("social-preview-"+theme,transparent=False))
    c = Canvas(4096,4096)
    c.mark(256,256,112)
    exports.append(c.save("icon-4k"))
    c = Canvas(64,64)
    c.mark(0,0,2,dark=True)
    exports.append(c.save("server-icon",vector=False))
    for theme in ("light","dark"):
        c = Canvas(32,32)
        c.mark(0,0,1,dark=theme == "dark")
        exports.append(c.save("icon-32-"+theme,vector=False))
    c = Canvas(192,192,COLORS["night"])
    c.mark(16,16,5,dark=True)
    exports.append(c.save("app-icon",transparent=False))
    c.image.resize((180,180),Image.Resampling.NEAREST).save(OUT / "apple-touch-icon.png")
    exports.append({"file":"pixel-leaf/apple-touch-icon.png","width":180,"height":180,"transparent":False})
    c = Canvas(1800,1850,COLORS["night"])
    c.text("Kindling / Pixel Leaf",64,105,46,COLORS["paper"])
    c.text("A Leaf fork. A spark of its own.",64,155,25,COLORS["muted"])
    c.rect(64,210,1672,510,COLORS["paper"])
    c.mark(135,260,13)
    c.word(690,325,20,COLORS["ink"])
    c.text("Vanilla behaviour. Every change measured.",690,590,25,"#516553")
    c.text("THE MARK",64,793,20,COLORS["muted"])
    c.rect(64,830,810,500,COLORS["surface"])
    c.mark(249,875,12,dark=True)
    c.rect(910,830,826,500,COLORS["paper"])
    c.mark(1131,875,12)
    c.text("COLOUR",64,1410,20,COLORS["muted"])
    for index,(label,key) in enumerate([("Forest","forest"),("Leaf","leaf"),("Ember","ember"),("Gold","gold"),("Paper","paper")]):
        x = 64+index*338
        c.rect(x,1445,308,96,COLORS[key])
        c.text(label,x,1580,22,COLORS["paper"])
        c.text(COLORS[key],x,1612,18,COLORS["muted"])
    c.text("Custom pixel wordmark / Manrope for everything you read.",64,1710,25,COLORS["paper"])
    c.text("Outlined SVG masters · Transparent PNGs · 32 px to 4K",64,1755,20,COLORS["muted"])
    exports.append(c.save("brand-overview",ROOT,transparent=False))
    (ROOT / "exports.json").write_text(json.dumps(exports,indent=2)+"\n",encoding="utf-8")
    (ROOT / "palette.json").write_text(json.dumps(PALETTE,indent=2)+"\n",encoding="utf-8")
    print(f"Generated {len(exports)} brand assets in {OUT}")

if __name__ == "__main__":
    build()
