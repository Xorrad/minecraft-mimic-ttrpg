import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;

/**
 * Cuts whole Minecraft skins into two skin parts for the plugin's skin composer (see src/skins/README.txt):
 *
 *   body     the person: their face, hair and beard, and a complete body in their skin colour.
 *            Where they wore clothes or headwear, the body is filled in: skin on the body and face,
 *            their hair colour on the rest of the head.
 *   clothes  the outfit, as a single part: helmets, hats, hoods and keffiyehs on the head, and the body,
 *            arms and legs, both layers, minus the bare skin, left transparent so the body shows through.
 *
 * An NPC then gets the body of one skin and the clothes of another: the skin showing between its clothes
 * always matches its face, and its helmet always matches its armour.
 * Bodies are shared by every culture; only clothes belong to a theme (culture).
 *
 * How parts are told apart:
 *   - Skin: the skin tone is read from the face (the most common shades of the middle of the face).
 *     Skins whose face isn't skin-coloured (full helmets, masks, veils) only give clothes, head included.
 *   - Head: colours of the head that are also in the outfit (a helmet's metal, a keffiyeh's white) are
 *     headwear, and so are patches surrounded by headwear (a keffiyeh's checks and cord). Colours that
 *     could be hair (dark, brown, blond, ginger) stay with the person unless they're metal, and so does
 *     the middle of the face (eyes, mouth, beard). When it guesses wrong for a skin, set its head to
 *     "body" or "clothes" in parts_mapping.tsv.
 * Arms are converted to the arm model of the gender (classic for male, slim for female, as in layers.yml),
 * old 64x32 skins are converted to 64x64, and identical parts are only kept once.
 *
 * Which theme and gender each skin belongs to is read from parts_mapping.tsv in the skins folder.
 *
 * Usage (from the project folder):
 *   java tools/SkinPartsGenerator.java minecraft_skins
 * Output, replaced on each run:
 *   minecraft_skins/generated_parts/parts/body/<gender>/<skin>.png              (bodies, for every culture)
 *   minecraft_skins/generated_parts/cultures/<theme>/clothes/<gender>/<skin>.png
 *   minecraft_skins/generated_parts/parts/clothes/<gender>/<skin>.png           (clothes of theme "shared")
 *   minecraft_skins/generated_parts/previews/<theme>_<gender>.png  (the theme's clothes on random bodies)
 *   minecraft_skins/generated_parts/previews/split_<theme>_<gender>.png      (how each skin was split)
 * Copy cultures/ and parts/ into plugins/MimicTTRPG/skins/ on the server, and point each culture to its
 * theme's clothes with "skin: <theme>" in cultures.yml (several cultures can share a theme).
 */
public class SkinPartsGenerator {

    static final int SIZE = 64;
    static final String MAPPING = "parts_mapping.tsv", OUTPUT = "generated_parts";
    static final String BODY = "body", CLOTHES = "clothes";
    // Must match "slim" in the plugin's skins/layers.yml.
    static final Map<String, Boolean> SLIM = Map.of("male", false, "female", true);

    // How far (RGB distance) a shade can be from the face's main skin colour to count as skin.
    static final double SHADE_DISTANCE = 40;
    // How far (degrees of hue) a shade can be from the main skin colour: shades of skin share a hue,
    // while e.g. the pinkish white of a keffiyeh next to a tanned face doesn't.
    static final double SHADE_HUE = 12;
    // How close (RGB distance) a pixel must be to one of the face's skin shades to count as bare skin.
    static final double SKIN_DISTANCE = 14;
    // The smallest share of the body (base layer) clothes must cover to be kept.
    static final double MIN_CLOTHES_COVERAGE = 0.33;

    // A rectangle of the skin: x, y, width, height.
    record Area(int x, int y, int w, int h) {}

    // The body, arms and legs on the base layer, and on the outer layer.
    static final List<Area> LIMBS_BASE = List.of(
            new Area(16, 16, 24, 16), new Area(40, 16, 16, 16), new Area(32, 48, 16, 16),
            new Area(0, 16, 16, 16), new Area(16, 48, 16, 16));
    static final List<Area> LIMBS_OUTER = List.of(
            new Area(16, 32, 24, 16), new Area(40, 32, 16, 16), new Area(48, 48, 16, 16),
            new Area(0, 32, 16, 16), new Area(0, 48, 16, 16));
    // The face, on the front of the head's base layer.
    static final Area FACE = new Area(8, 8, 8, 8);

    // The top-left corners of the four arm areas (right/left arm, base/outer layer).
    static final int[][] ARMS = { {40, 16}, {40, 32}, {32, 48}, {48, 48} };

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java tools/SkinPartsGenerator.java <skins folder>");
            System.exit(1);
        }
        File root = new File(args[0]);
        File out = new File(root, OUTPUT);
        deleteRecursively(out);

        Map<String, String[]> mapping = readMapping(new File(root, MAPPING));
        List<File> skins = new ArrayList<>();
        collect(root, skins, out);

        // theme_gender -> layer -> parts (for previews)
        Map<String, Map<String, List<BufferedImage>>> pools = new TreeMap<>();
        // theme_gender -> (source skin, body part or null, clothes part or null), to show each split
        Map<String, List<BufferedImage[]>> splits = new TreeMap<>();
        Set<String> seen = new HashSet<>();
        int written = 0, duplicates = 0, skipped = 0, noFace = 0, noClothes = 0;

        for (File file : skins) {
            String rel = relative(root, file);
            String[] entry = mapping.get(rel);
            if (entry == null) {
                System.out.println("Not in " + MAPPING + ", skipped: " + rel);
                skipped++;
                continue;
            }
            String theme = entry[0], gender = entry[1];
            if (theme.equals("skip")) { skipped++; continue; }
            if (!SLIM.containsKey(gender)) {
                System.out.println("Unknown gender \"" + gender + "\", skipped: " + rel);
                skipped++;
                continue;
            }

            BufferedImage skin = toArgb(ImageIO.read(file));
            if (skin == null || skin.getWidth() != SIZE || (skin.getHeight() != SIZE && skin.getHeight() != 32)) {
                System.out.println("Not a 64x64 or 64x32 skin, skipped: " + rel);
                skipped++;
                continue;
            }
            if (skin.getHeight() == 32)
                skin = fromLegacy(skin);

            boolean slim = isSlim(skin), wantSlim = SLIM.get(gender);
            if (slim != wantSlim)
                skin = convertArms(skin, slim ? 3 : 4, wantSlim ? 3 : 4);

            List<Color> palette = skinPalette(skin);
            if (palette.isEmpty())
                noFace++;
            int[][] head = classifyHead(skin, palette, entry[2]);
            BufferedImage body = palette.isEmpty() ? null : bodyPart(skin, palette, head);
            BufferedImage clothes = clothesPart(skin, palette, head);
            // Clothes so close to the skin colour that most of them were taken for skin would leave NPCs
            // almost naked: leave them out.
            double coverage = coverage(clothes);
            if (coverage < MIN_CLOTHES_COVERAGE) {
                if (coverage > 0)
                    System.out.printf("Clothes cover only %d%% of the body (too close to the skin colour?), left out: %s%n",
                            Math.round(coverage * 100), rel);
                clothes = null;
                noClothes++;
            }

            String group = theme + "_" + gender;
            splits.computeIfAbsent(group, k -> new ArrayList<>()).add(new BufferedImage[] { skin, body, clothes });

            String name = rel.replace('/', '_').replace(' ', '_').replaceAll("(?i)\\.png$", "");
            Map<String, BufferedImage> parts = new LinkedHashMap<>();
            if (body != null) parts.put(BODY, body);
            if (clothes != null) parts.put(CLOTHES, clothes);

            for (Map.Entry<String, BufferedImage> part : parts.entrySet()) {
                String layer = part.getKey();
                // Bodies are shared by every culture: only clothes belong to a theme (culture).
                boolean shared = layer.equals(BODY) || theme.equals("shared");
                String owner = shared ? "shared" : theme;
                if (!seen.add(owner + "/" + gender + "/" + layer + "/" + hash(part.getValue()))) { duplicates++; continue; }

                File folder = shared
                        ? new File(out, "parts/" + layer + "/" + gender)
                        : new File(out, "cultures/" + theme + "/" + layer + "/" + gender);
                folder.mkdirs();
                ImageIO.write(part.getValue(), "png", new File(folder, name + ".png"));
                written++;
                pools.computeIfAbsent(owner + "_" + gender, k -> new LinkedHashMap<>())
                        .computeIfAbsent(layer, k -> new ArrayList<>()).add(part.getValue());
            }
        }

        // Each theme's clothes on the shared bodies of the same gender, as the plugin will put them together.
        File previews = new File(out, "previews");
        previews.mkdirs();
        for (Map.Entry<String, Map<String, List<BufferedImage>>> pool : pools.entrySet()) {
            String gender = pool.getKey().replaceAll(".*_", "");
            List<BufferedImage> clothes = pool.getValue().getOrDefault(CLOTHES, List.of());
            List<BufferedImage> bodies = pools.getOrDefault("shared_" + gender, Map.of()).getOrDefault(BODY, List.of());
            if (!clothes.isEmpty())
                writePreview(Map.of(BODY, bodies, CLOTHES, clothes), new File(previews, pool.getKey() + ".png"), SLIM.get(gender));
            System.out.printf("%-18s body=%-3d clothes=%-3d%n", pool.getKey(),
                    pool.getValue().getOrDefault(BODY, List.of()).size(), clothes.size());
        }
        for (Map.Entry<String, List<BufferedImage[]>> split : splits.entrySet())
            writeSplits(split.getValue(), new File(previews, "split_" + split.getKey() + ".png"), SLIM.get(split.getKey().replaceAll(".*_", "")));

        System.out.println("Wrote " + written + " parts (" + duplicates + " duplicates left out, " + skipped + " skins skipped, "
                + noFace + " skins without a skin-coloured face gave only clothes, "
                + noClothes + " skins gave no clothes) to " + out);
    }

    // ---- Skin colour ----

    // The shades of skin of the face, or an empty list if the face isn't skin-coloured (helmet, mask...).
    // The main colour is the most common skin-like colour of the middle of the face: from the forehead to
    // the mouth, without the outer columns, where hair often hangs down, nor the chin, often a beard.
    static List<Color> skinPalette(BufferedImage skin) {
        // Skin-like colours of the middle of the face, the cheeks, nose and mouth counting more.
        Map<Integer, Integer> counts = new HashMap<>();
        int total = 0;
        for (int y = FACE.y() + 2; y < FACE.y() + 7; y++)
            for (int x = FACE.x() + 1; x < FACE.x() + FACE.w() - 1; x++) {
                int p = skin.getRGB(x, y);
                if ((p >>> 24) == 0 || !looksLikeSkin(new Color(p & 0xFFFFFF))) continue;
                boolean cheeks = y >= FACE.y() + 4 && x >= FACE.x() + 2 && x < FACE.x() + 6;
                counts.merge(p & 0xFFFFFF, cheeks ? 3 : 1, Integer::sum);
                total++;
            }
        // A few skin-like pixels (a nose under a helmet, a blush on a mask...) aren't a face.
        if (total < 8) return List.of();

        // Skin is usually drawn with several close shades, and hair with one flat colour, so each colour
        // scores with all the colours close to it. The best one is the main skin colour.
        Color main = null;
        int best = -1;
        for (int rgb : counts.keySet()) {
            Color c = new Color(rgb);
            int score = 0;
            for (Map.Entry<Integer, Integer> other : counts.entrySet())
                if (distance(c, new Color(other.getKey())) <= 35) score += other.getValue();
            if (score > best || (score == best && counts.get(rgb) > counts.get(main.getRGB() & 0xFFFFFF))) {
                best = score;
                main = c;
            }
        }

        // Every skin-like colour of the whole face close enough to the main colour is a shade of skin.
        Set<Integer> shades = new LinkedHashSet<>();
        shades.add(main.getRGB() & 0xFFFFFF);
        for (int y = FACE.y(); y < FACE.y() + FACE.h(); y++)
            for (int x = FACE.x(); x < FACE.x() + FACE.w(); x++) {
                int p = skin.getRGB(x, y);
                if ((p >>> 24) == 0) continue;
                Color c = new Color(p & 0xFFFFFF);
                if (distance(c, main) <= SHADE_DISTANCE && hueDifference(c, main) <= SHADE_HUE && looksLikeSkin(c))
                    shades.add(p & 0xFFFFFF);
            }
        List<Color> palette = new ArrayList<>();
        palette.add(main);
        for (int rgb : shades)
            if (rgb != (main.getRGB() & 0xFFFFFF)) palette.add(new Color(rgb));
        return palette;
    }

    // Skin colours, from pale to dark, are warm: clearly more red than green, and green at least as much
    // as blue (not purple). They are neither grey, nor as vivid as orange or red hair, nor as yellow as
    // blond hair or khaki (blue too low next to green).
    static boolean looksLikeSkin(Color c) {
        int r = c.getRed(), g = c.getGreen(), b = c.getBlue();
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double saturation = (max == 0) ? 0 : (max - min) / (double) max;
        return r >= g + 8 && g >= b - 5 && b >= g * 0.5 && max - min >= 15 && max >= 45 && saturation <= 0.62;
    }

    static boolean isSkin(int argb, List<Color> palette) {
        if ((argb >>> 24) == 0 || palette.isEmpty()) return false;
        Color c = new Color(argb & 0xFFFFFF);
        for (Color shade : palette)
            if (distance(c, shade) <= SKIN_DISTANCE) return true;
        return false;
    }

    static double hueDifference(Color a, Color b) {
        float ha = Color.RGBtoHSB(a.getRed(), a.getGreen(), a.getBlue(), null)[0] * 360;
        float hb = Color.RGBtoHSB(b.getRed(), b.getGreen(), b.getBlue(), null)[0] * 360;
        double d = Math.abs(ha - hb);
        return Math.min(d, 360 - d);
    }

    static double distance(Color a, Color b) {
        int dr = a.getRed() - b.getRed(), dg = a.getGreen() - b.getGreen(), db = a.getBlue() - b.getBlue();
        return Math.sqrt(dr * dr + dg * dg + db * db);
    }

    // ---- Head: body (face, hair, beard) or headwear (helmets, hats, keffiyehs...) ----

    static final int NONE = 0, PERSON = 1, HEADWEAR = 2;
    // How a skin's head is split, from the 4th column of parts_mapping.tsv.
    static final String HEAD_AUTO = "auto", HEAD_BODY = "body", HEAD_CLOTHES = "clothes";

    // Whether a pixel of the head's base layer (x < 32) or outer layer (x >= 32) is drawn by Minecraft:
    // its top 8 rows only hold the top and bottom faces.
    static boolean isHeadDrawn(int x, int y) {
        int lx = x % 32;
        return y >= 8 || (lx >= 8 && lx < 24);
    }

    // The middle of the face, from the eyebrows down: eyes, nose, mouth, moustache and beard (both layers).
    // The forehead above isn't part of it: headbands and the front of hoods and keffiyehs are there.
    static boolean isInnerFace(int x, int y) {
        int lx = x % 32;
        return y >= FACE.y() + 3 && y < FACE.y() + FACE.h() && lx >= FACE.x() + 1 && lx < FACE.x() + FACE.w() - 1;
    }

    // The bottom of the head (under the chin), which is the neck: skin when uncovered.
    static boolean isUnderHead(int x, int y) {
        int lx = x % 32;
        return y < 8 && lx >= 16 && lx < 24;
    }

    // Labels each pixel of the head (both layers) as part of the person or of their headwear.
    //   - Skin is the person.
    //   - A group of similar colours that also appears in the outfit (a helmet's metal on the armour,
    //     a keffiyeh's white on the thobe) is headwear. Dark groups (black hair with a black suit) aren't,
    //     unless they turn out to be surrounded by headwear (the black band of a keffiyeh).
    //   - A group mostly surrounded by headwear is headwear too (the red checks of a keffiyeh).
    //   - The middle of the face stays the person (eyes, mouth, beard), unless it's grey metal of the
    //     outfit (the cheek guards of a helmet).
    //   - Everything else (hair, beard, eyebrows) is the person.
    // A skin without a skin-coloured face (full helmet, mask) is all headwear.
    static int[][] classifyHead(BufferedImage skin, List<Color> palette, String mode) {
        int[][] labels = new int[SIZE][16];
        List<int[]> others = new ArrayList<>();
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < SIZE; x++) {
                int p = skin.getRGB(x, y);
                if ((p >>> 24) == 0 || !isHeadDrawn(x, y)) continue;
                if (isSkin(p, palette)) labels[x][y] = PERSON;
                else others.add(new int[] { x, y });
            }

        boolean allHeadwear = palette.isEmpty() || mode.equals(HEAD_CLOTHES);
        if (allHeadwear || mode.equals(HEAD_BODY)) {
            for (int[] o : others) labels[o[0]][o[1]] = allHeadwear ? HEADWEAR : PERSON;
            return labels;
        }

        // Groups of similar colours, the most common colours first.
        Map<Integer, Integer> counts = new HashMap<>();
        for (int[] o : others) counts.merge(skin.getRGB(o[0], o[1]) & 0xFFFFFF, 1, Integer::sum);
        List<Integer> colors = new ArrayList<>(counts.keySet());
        colors.sort((a, b) -> counts.get(b) - counts.get(a));
        Map<Integer, Integer> groupOf = new HashMap<>();
        List<Color> leaders = new ArrayList<>();
        for (int rgb : colors) {
            Color c = new Color(rgb);
            int group = -1;
            for (int g = 0; g < leaders.size() && group < 0; g++)
                if (distance(c, leaders.get(g)) <= 30) group = g;
            if (group < 0) { group = leaders.size(); leaders.add(c); }
            groupOf.put(rgb, group);
        }
        int groups = leaders.size();

        // What each group looks like on the head, and whether its colours are in the outfit.
        List<Color> outfit = outfitColors(skin, palette);
        int[] size = new int[groups], inner = new int[groups], inOutfit = new int[groups];
        double[] brightness = new double[groups], saturation = new double[groups];
        for (int[] o : others) {
            int rgb = skin.getRGB(o[0], o[1]) & 0xFFFFFF, g = groupOf.get(rgb);
            Color c = new Color(rgb);
            size[g]++;
            if (isInnerFace(o[0], o[1])) inner[g]++;
            int max = Math.max(c.getRed(), Math.max(c.getGreen(), c.getBlue())), min = Math.min(c.getRed(), Math.min(c.getGreen(), c.getBlue()));
            brightness[g] += max;
            saturation[g] += (max == 0) ? 0 : (max - min) / (double) max;
        }
        for (int rgb : counts.keySet()) {
            Color c = new Color(rgb);
            for (Color o : outfit)
                if (distance(c, o) <= 25) { inOutfit[groupOf.get(rgb)]++; break; }
        }

        boolean[] headwear = new boolean[groups], metal = new boolean[groups], vivid = new boolean[groups];
        for (int g = 0; g < groups; g++) {
            brightness[g] /= size[g];
            saturation[g] /= size[g];
            Color leader = leaders.get(g);
            boolean outfitColour = inOutfit[g] > 0 && matchingOutfitPixels(skin, palette, leader) >= 6;
            metal[g] = outfitColour && saturation[g] < 0.15 && brightness[g] >= 50 && brightness[g] <= 220;
            // Colours that could be hair (dark, brown, blond, ginger) only count as headwear when they're metal:
            // the same brown on the head and on a leather jerkin is more often hair than a hood.
            boolean couldBeHair = brightness[g] < 60 || isHairHue(leader);
            headwear[g] = outfitColour && (metal[g] || !couldBeHair) && inner[g] < 0.3 * size[g];
            // Vivid colours that can't be hair (red, green, blue...) are headwear as soon as they touch it.
            vivid[g] = saturation[g] > 0.5 && !isHairHue(leader);
        }

        label(labels, others, skin, groupOf, headwear, metal);

        // Patches of the person's colours (outside the middle of the face) that are mostly surrounded by
        // headwear join it, a few times over: the checks or the cord of a keffiyeh, the plume of a helmet.
        // This goes patch by patch, so the black cord around a head joins the keffiyeh while the black
        // beard of the same colour, on the face, stays.
        int[][] directions = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
        for (int round = 0; round < 3; round++) {
            boolean changed = false;
            boolean[][] visited = new boolean[SIZE][16];
            for (int[] start : others) {
                int sx = start[0], sy = start[1];
                if (visited[sx][sy] || labels[sx][sy] != PERSON || isInnerFace(sx, sy)) continue;
                int g = groupOf.get(skin.getRGB(sx, sy) & 0xFFFFFF);

                // The patch: connected pixels of the same group and layer, labelled as the person.
                List<int[]> patch = new ArrayList<>();
                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(start);
                visited[sx][sy] = true;
                int byHeadwear = 0, byPerson = 0;
                while (!queue.isEmpty()) {
                    int[] o = queue.poll();
                    patch.add(o);
                    for (int[] d : directions) {
                        int nx = o[0] + d[0], ny = o[1] + d[1];
                        if (nx < 0 || ny < 0 || nx >= SIZE || ny >= 16 || (nx < 32) != (o[0] < 32)) continue;
                        if (labels[nx][ny] == NONE) continue;
                        boolean sameGroup = groupOf.getOrDefault(skin.getRGB(nx, ny) & 0xFFFFFF, -1) == g;
                        if (sameGroup && labels[nx][ny] == PERSON && !isInnerFace(nx, ny)) {
                            if (!visited[nx][ny]) { visited[nx][ny] = true; queue.add(new int[] { nx, ny }); }
                        }
                        else if (labels[nx][ny] == HEADWEAR) byHeadwear++;
                        else if (!sameGroup) byPerson++;
                    }
                }
                if (byHeadwear > 0 && (byHeadwear > 1.5 * byPerson || vivid[g])) {
                    for (int[] o : patch) labels[o[0]][o[1]] = HEADWEAR;
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return labels;
    }

    // The warm hues of brown, blond and ginger hair (between red-orange and yellow), not grey.
    static boolean isHairHue(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        float hue = hsb[0] * 360;
        return hsb[1] >= 0.18 && hue >= 8 && hue <= 55;
    }

    static void label(int[][] labels, List<int[]> others, BufferedImage skin, Map<Integer, Integer> groupOf, boolean[] headwear, boolean[] metal) {
        for (int[] o : others) {
            int g = groupOf.get(skin.getRGB(o[0], o[1]) & 0xFFFFFF);
            boolean isHeadwear = headwear[g] && (!isInnerFace(o[0], o[1]) || metal[g]);
            labels[o[0]][o[1]] = isHeadwear ? HEADWEAR : PERSON;
        }
    }

    // The distinct colours of the outfit: the body, arms and legs, both layers, without bare skin.
    static List<Color> outfitColors(BufferedImage skin, List<Color> palette) {
        Set<Integer> colors = new LinkedHashSet<>();
        for (List<Area> areas : List.of(LIMBS_BASE, LIMBS_OUTER))
            for (Area a : areas)
                for (int y = a.y(); y < a.y() + a.h(); y++)
                    for (int x = a.x(); x < a.x() + a.w(); x++) {
                        int p = skin.getRGB(x, y);
                        if ((p >>> 24) != 0 && !isSkin(p, palette)) colors.add(p & 0xFFFFFF);
                    }
        List<Color> list = new ArrayList<>();
        for (int rgb : colors) list.add(new Color(rgb));
        return list;
    }

    // How many outfit pixels are close to this colour (a colour on just a button or two doesn't count).
    static int matchingOutfitPixels(BufferedImage skin, List<Color> palette, Color color) {
        int count = 0;
        for (List<Area> areas : List.of(LIMBS_BASE, LIMBS_OUTER))
            for (Area a : areas)
                for (int y = a.y(); y < a.y() + a.h(); y++)
                    for (int x = a.x(); x < a.x() + a.w(); x++) {
                        int p = skin.getRGB(x, y);
                        if ((p >>> 24) != 0 && !isSkin(p, palette) && distance(new Color(p & 0xFFFFFF), color) <= 30) count++;
                    }
        return count;
    }

    // The person's hair colour: the most common non-skin colour of their head outside the middle of the face
    // (their hair), else of the middle of the face (beard, eyebrows), else a dark brown.
    static int hairColor(BufferedImage skin, int[][] head, List<Color> palette) {
        Map<Integer, Integer> hair = new HashMap<>(), face = new HashMap<>();
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < SIZE; x++) {
                int p = skin.getRGB(x, y);
                if (head[x][y] != PERSON || isSkin(p, palette)) continue;
                (isInnerFace(x, y) ? face : hair).merge(p & 0xFFFFFF, 1, Integer::sum);
            }
        Map<Integer, Integer> source = !hair.isEmpty() ? hair : face;
        if (source.isEmpty()) return 0xFF3A2A1E;
        return 0xFF000000 | Collections.max(source.entrySet(), Map.Entry.comparingByValue()).getKey();
    }

    // ---- Parts ----

    // The person: their head without headwear, and their body on the base layer.
    //   - Head: the face, hair and beard as they are. Where headwear was, the base layer is filled:
    //     the face and neck in the skin colour, the rest in the hair colour. The outer layer keeps only
    //     the person (hair, beard).
    //   - Body, arms and legs: bare skin as it is, everything else (clothes) in the main skin colour.
    //     The outer layer of the body is left out: it's clothes.
    static BufferedImage bodyPart(BufferedImage skin, List<Color> palette, int[][] head) {
        BufferedImage part = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int skinFill = 0xFF000000 | (palette.get(0).getRGB() & 0xFFFFFF);
        int hairFill = hairColor(skin, head, palette);

        for (int y = 0; y < 16; y++)
            for (int x = 0; x < SIZE; x++) {
                if (!isHeadDrawn(x, y)) continue;
                int p = skin.getRGB(x, y);
                if (head[x][y] == PERSON)
                    part.setRGB(x, y, p);
                else if (x < 32)
                    part.setRGB(x, y, (isInnerFace(x, y) || isUnderHead(x, y)) ? skinFill : hairFill);
            }

        for (Area a : LIMBS_BASE)
            for (int y = a.y(); y < a.y() + a.h(); y++)
                for (int x = a.x(); x < a.x() + a.w(); x++) {
                    int p = skin.getRGB(x, y);
                    // Only the pixels Minecraft draws (faces of the boxes) are filled, not the unused corners.
                    if ((p >>> 24) == 0 && !isDrawn(x, y)) continue;
                    part.setRGB(x, y, isSkin(p, palette) ? p : skinFill);
                }
        return part;
    }

    // The outfit: the headwear on the head, and the body, arms and legs on both layers without the bare skin.
    static BufferedImage clothesPart(BufferedImage skin, List<Color> palette, int[][] head) {
        BufferedImage part = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < SIZE; x++)
                if (head[x][y] == HEADWEAR) part.setRGB(x, y, skin.getRGB(x, y));

        for (List<Area> areas : List.of(LIMBS_BASE, LIMBS_OUTER))
            for (Area a : areas)
                for (int y = a.y(); y < a.y() + a.h(); y++)
                    for (int x = a.x(); x < a.x() + a.w(); x++) {
                        int p = skin.getRGB(x, y);
                        if (!isSkin(p, palette)) part.setRGB(x, y, p);
                    }
        return part;
    }

    // The share of the body, arms and legs (base layer pixels Minecraft draws) that the clothes cover.
    static double coverage(BufferedImage clothes) {
        int drawn = 0, covered = 0;
        for (Area a : LIMBS_BASE)
            for (int y = a.y(); y < a.y() + a.h(); y++)
                for (int x = a.x(); x < a.x() + a.w(); x++) {
                    if (!isDrawn(x, y)) continue;
                    drawn++;
                    if ((clothes.getRGB(x, y) >>> 24) != 0) covered++;
                }
        return (drawn == 0) ? 0 : covered / (double) drawn;
    }

    // Whether a base-layer pixel of the body, arms or legs is part of a face of their boxes.
    // Each box area is 16 (or 24) wide and 16 high; its top 4 rows only use the top and bottom faces.
    static boolean isDrawn(int x, int y) {
        int[][] boxes = { {16, 16, 8, 4, 12}, {0, 16, 4, 4, 12}, {16, 48, 4, 4, 12}, {40, 16, 4, 4, 12}, {32, 48, 4, 4, 12} };
        for (int[] b : boxes) {
            int ox = b[0], oy = b[1], w = b[2], d = b[3], h = b[4];
            int areaW = 2 * (w + d);
            if (x < ox || x >= ox + areaW || y < oy || y >= oy + d + h) continue;
            int lx = x - ox, ly = y - oy;
            if (ly < d) return lx >= d && lx < d + 2 * w;
            return true;
        }
        return false;
    }

    // ---- Formats ----

    static Map<String, String[]> readMapping(File file) throws IOException {
        Map<String, String[]> mapping = new HashMap<>();
        if (!file.exists()) {
            System.out.println("No " + MAPPING + " found, every skin will be skipped.");
            return mapping;
        }
        for (String line : Files.readAllLines(file.toPath())) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] cols = line.split("\t");
            if (cols.length < 3) { System.out.println("Ignored line in " + MAPPING + ": " + line); continue; }
            String head = (cols.length >= 4 && !cols[3].isBlank()) ? cols[3].trim().toLowerCase() : HEAD_AUTO;
            if (!List.of(HEAD_AUTO, HEAD_BODY, HEAD_CLOTHES).contains(head)) {
                System.out.println("Unknown head \"" + head + "\" in " + MAPPING + ", using auto: " + line);
                head = HEAD_AUTO;
            }
            mapping.put(cols[0].trim(), new String[] { cols[1].trim().toLowerCase(), cols[2].trim().toLowerCase(), head });
        }
        return mapping;
    }

    static void collect(File dir, List<File> out, File exclude) {
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            if (f.equals(exclude)) continue;
            if (f.isDirectory()) collect(f, out, exclude);
            else if (f.getName().toLowerCase().endsWith(".png")) out.add(f);
        }
    }

    static String relative(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    static BufferedImage toArgb(BufferedImage image) {
        if (image == null) return null;
        BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return argb;
    }

    // Slim skins leave the 4th column of the right arm's front and back faces transparent.
    static boolean isSlim(BufferedImage skin) {
        for (int y = 20; y < 32; y++)
            for (int x = 54; x < 56; x++)
                if ((skin.getRGB(x, y) >>> 24) != 0) return false;
        return true;
    }

    // The faces of an arm or leg box of the given width (depth 4, height 12), relative to its area's corner:
    // top, bottom, right side, front, left side, back.
    static Area[] faces(int w) {
        int d = 4, h = 12;
        return new Area[] {
                new Area(d, 0, w, d), new Area(d + w, 0, w, d),
                new Area(0, d, d, h), new Area(d, d, w, h), new Area(d + w, d, d, h), new Area(d + w + d, d, w, h) };
    }

    // Converts an old 64x32 skin: the left arm and leg are the right ones mirrored, as Minecraft does.
    static BufferedImage fromLegacy(BufferedImage legacy) {
        BufferedImage skin = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < SIZE; x++)
                skin.setRGB(x, y, legacy.getRGB(x, y));
        mirrorLimb(skin, 0, 16, 16, 48);   // right leg -> left leg
        mirrorLimb(skin, 40, 16, 32, 48);  // right arm -> left arm
        return skin;
    }

    static void mirrorLimb(BufferedImage skin, int sx, int sy, int dx, int dy) {
        Area[] f = faces(4);
        // Each face is flipped; the outer and inner sides swap places.
        int[] target = { 0, 1, 4, 3, 2, 5 };
        for (int i = 0; i < 6; i++) {
            Area from = f[i], to = f[target[i]];
            for (int y = 0; y < from.h(); y++)
                for (int x = 0; x < from.w(); x++)
                    skin.setRGB(dx + to.x() + (from.w() - 1 - x), dy + to.y() + y, skin.getRGB(sx + from.x() + x, sy + from.y() + y));
        }
    }

    // Widens (3 -> 4) or narrows (4 -> 3) the arms: front, back, top and bottom faces are resampled,
    // keeping the edge columns and repeating or dropping a middle one.
    static BufferedImage convertArms(BufferedImage skin, int fromW, int toW) {
        BufferedImage converted = copy(skin);
        Area[] from = faces(fromW), to = faces(toW);
        for (int[] arm : ARMS) {
            int ox = arm[0], oy = arm[1];
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++)
                    converted.setRGB(ox + x, oy + y, 0);
            for (int i = 0; i < 6; i++) {
                Area a = from[i], b = to[i];
                for (int y = 0; y < b.h(); y++)
                    for (int x = 0; x < b.w(); x++) {
                        int srcX = (a.w() == b.w()) ? x : (int) Math.round((x + 0.5) * a.w() / b.w() - 0.5);
                        converted.setRGB(ox + b.x() + x, oy + b.y() + y, skin.getRGB(ox + a.x() + srcX, oy + a.y() + y));
                    }
            }
        }
        return converted;
    }

    static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                copy.setRGB(x, y, image.getRGB(x, y));
        return copy;
    }

    static String hash(BufferedImage image) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        for (int y = 0; y < SIZE; y++)
            for (int x = 0; x < SIZE; x++) {
                int p = image.getRGB(x, y);
                digest.update(new byte[] { (byte) (p >>> 24), (byte) (p >>> 16), (byte) (p >>> 8), (byte) p });
            }
        return HexFormat.of().formatHex(digest.digest());
    }

    // ---- Previews ----

    static final int SCALE = 4, CELL_W = 16 * SCALE + 12, CELL_H = 32 * SCALE + 12;

    // 24 random combinations of a body and clothes, drawn from the front.
    static void writePreview(Map<String, List<BufferedImage>> layers, File file, boolean slim) throws IOException {
        List<BufferedImage> bodies = layers.getOrDefault(BODY, List.of()), clothes = layers.getOrDefault(CLOTHES, List.of());
        if (bodies.isEmpty() || clothes.isEmpty()) return;
        int count = 24, cols = 12;
        BufferedImage sheet = sheet(cols, (count + cols - 1) / cols);
        Graphics2D g = sheet.createGraphics();
        Random random = new Random(file.getName().hashCode());
        for (int i = 0; i < count; i++) {
            BufferedImage skin = stack(bodies.get(random.nextInt(bodies.size())), clothes.get(random.nextInt(clothes.size())));
            draw(g, front(skin, slim), i % cols, i / cols);
        }
        g.dispose();
        ImageIO.write(sheet, "png", file);
    }

    // For each skin: the original, its body part and its clothes part, side by side.
    static void writeSplits(List<BufferedImage[]> splits, File file, boolean slim) throws IOException {
        int cols = 12, triples = cols / 3;
        BufferedImage sheet = sheet(cols, (splits.size() + triples - 1) / triples);
        Graphics2D g = sheet.createGraphics();
        for (int i = 0; i < splits.size(); i++) {
            BufferedImage[] s = splits.get(i);
            for (int j = 0; j < 3; j++)
                if (s[j] != null)
                    draw(g, front(s[j], slim), (i % triples) * 3 + j, i / triples);
        }
        g.dispose();
        ImageIO.write(sheet, "png", file);
    }

    static BufferedImage sheet(int cols, int rows) {
        BufferedImage sheet = new BufferedImage(cols * CELL_W, rows * CELL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(90, 110, 130));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.dispose();
        return sheet;
    }

    static void draw(Graphics2D g, BufferedImage view, int col, int row) {
        g.drawImage(view, col * CELL_W + 6, row * CELL_H + 6, 16 * SCALE, 32 * SCALE, null);
    }

    static BufferedImage stack(BufferedImage... layers) {
        BufferedImage skin = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = skin.createGraphics();
        for (BufferedImage layer : layers) g.drawImage(layer, 0, 0, null);
        g.dispose();
        return skin;
    }

    // A 16x32 front view of a skin, outer layer over the base layer.
    static BufferedImage front(BufferedImage s, boolean slim) {
        BufferedImage v = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = v.createGraphics();
        int aw = slim ? 3 : 4;
        int[][] draws = {
                {8, 8, 8, 8, 4, 0}, {40, 8, 8, 8, 4, 0},              // head
                {20, 20, 8, 12, 4, 8}, {20, 36, 8, 12, 4, 8},         // body
                {44, 20, aw, 12, 4 - aw, 8}, {44, 36, aw, 12, 4 - aw, 8}, // right arm
                {36, 52, aw, 12, 12, 8}, {52, 52, aw, 12, 12, 8},     // left arm
                {4, 20, 4, 12, 4, 20}, {4, 36, 4, 12, 4, 20},         // right leg
                {20, 52, 4, 12, 8, 20}, {4, 52, 4, 12, 8, 20} };      // left leg
        for (int[] d : draws)
            g.drawImage(s.getSubimage(d[0], d[1], d[2], d[3]), d[4], d[5], null);
        g.dispose();
        return v;
    }

    static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null)
            for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
