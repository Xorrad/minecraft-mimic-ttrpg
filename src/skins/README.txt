SKINS OF NPCs
=============

When an NPC is created with /char new, or when the Skin button of the NPC menu is clicked,
the NPC gets a skin in one of these two ways:

1. PUT TOGETHER FROM PARTS (if there are parts for every required layer, see layers.yml)
   One random part is picked for each layer of layers.yml and they are stacked, first layer at the bottom.

   A part is a whole 64x64 Minecraft skin image (.png), transparent everywhere except where the part is.
   With the default layers there are two parts:
     body     the person: face, hair and beard, and a whole body in their skin colour
     clothes  the whole outfit, helmets, hats and hoods included, with the bare skin left transparent
              so the body shows through
   So an NPC gets the face of one skin and the clothes of another: the skin showing between its
   clothes always matches its face, and its helmet always matches its armour.

   Parts shared by every culture:     parts/<layer>/
   Parts of a culture:                cultures/<culture id in lowercase>/<layer>/
   For a layer, a culture that has its own parts only uses those; otherwise it uses the shared ones.

   SHARING CLOTHES BETWEEN CULTURES
   The folders of cultures/ don't have to be named after a culture: a culture can take its parts from
   other folders with the "skin" property of cultures.yml, one folder or a list. For example:
       FRENCH:
         name: French
         skin: medieval              # uses cultures/medieval/
       ENGLISH:
         name: English
         skin: [medieval, peasant]   # uses cultures/medieval/ and cultures/peasant/ together
   French and English NPCs then share the clothes of cultures/medieval/. Without "skin", a culture
   uses its own folder, cultures/<culture id in lowercase>/. /culture info shows a culture's folders.

   In each layer folder, put parts for one gender in male/ or female/, and parts for both directly
   in the layer folder.

   Bodies usually go in parts/, so every culture shares the same faces, and clothes in cultures/.
   Examples:
     parts/body/male/farmer.png                  a man's face and body, for every culture
     cultures/roman/clothes/male/legionary.png   the armour of a Roman soldier
     cultures/arab/clothes/female/abaya.png      an Arab woman's clothes

   Female skins use the slim arm model and male skins the classic one, unless changed in layers.yml,
   so draw each gender's parts for its model.
   Each put-together skin is saved in generated/, so you can check how it looks.

   MAKING PARTS FROM WHOLE SKINS
   The plugin's source has a tool that cuts whole skins into body and clothes parts:
   tools/SkinPartsGenerator.java. It finds the bare skin by the colour of each skin's face, and tells
   headwear from hair by the colours the head shares with the outfit.
   List each skin's theme (culture) and gender in parts_mapping.tsv next to the skins, run
       java tools/SkinPartsGenerator.java <folder of skins>
   and copy the cultures/ and parts/ folders it makes into this folder. It puts every body in parts/
   (shared) and the clothes in cultures/<theme>/. It also converts the arms
   to each gender's model and old 64x32 skins to 64x64. In previews/, it shows sample combinations
   and how each skin was split.

2. A WHOLE SKIN (if no skin could be put together)
   A random image from male/ or female/, depending on the NPC's gender.
   A file with "slim" in its name (e.g. anna_slim.png) uses the slim arm model.

Skins are uploaded to MineSkin (through Citizens) to be signed, which can take a few seconds.
Each image is only uploaded once: the result is kept in cache.yml, which is safe to delete.
