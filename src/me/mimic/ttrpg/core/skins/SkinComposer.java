package me.mimic.ttrpg.core.skins;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

// Builds a skin by stacking skin parts: one random part per layer, drawn from the first layer to the last.
//
// Every part is a whole 64x64 skin image, transparent except where the part is (a face, hair, a tunic,
// boots...), so a part can use any area of the skin, including the outer layer (hats, jackets...).
//
// The parts of a layer are looked up in the culture's skin folders first, then in the shared parts folder:
//   cultures/<folder>/<layer>/ (for each of the culture's folders, together)   then   parts/<layer>/
// A culture's skin folders are its own (cultures/<culture id>/) unless the "skin" property of cultures.yml
// lists others, so several cultures can share clothes.
// In each of these, the PNGs in the gender's subfolder (male/ or female/) and directly in the layer folder
// (for both genders) are candidates. A culture that has its own parts for a layer only uses those.
public class SkinComposer {

    public static final int SIZE = 64;

    // A layer of the skin. A required layer must have a part, or no skin is made;
    // an optional one gets a part only with the given chance, and only if it has parts.
    public record Layer(String name, boolean required, double chance) {}

    // A composed skin, as PNG bytes, and the parts it was made from (e.g. "parts/body/male/tan.png").
    public record Composed(byte[] png, List<String> parts) {}

    private final File partsFolder;
    private final File culturesFolder;
    private final List<Layer> layers;
    private final Random random;
    private final Consumer<String> warn;

    public SkinComposer(File partsFolder, File culturesFolder, List<Layer> layers, Random random, Consumer<String> warn) {
        this.partsFolder = partsFolder;
        this.culturesFolder = culturesFolder;
        this.layers = layers;
        this.random = random;
        this.warn = warn;
    }

    // Composes a skin from these folders of cultures/ (none: only shared parts) and gender folder
    // ("male" or "female"). Returns null when a required layer has no usable part.
    public Composed compose(List<String> folders, String gender) throws IOException {
        BufferedImage canvas = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        graphics.setComposite(AlphaComposite.SrcOver);
        List<String> used = new ArrayList<>();

        try {
            for(Layer layer : this.layers) {
                if(!layer.required() && this.random.nextDouble() >= layer.chance())
                    continue;

                Part part = this.pickPart(layer.name(), folders, gender);
                if(part == null) {
                    if(layer.required())
                        return null;
                    continue;
                }
                graphics.drawImage(part.image(), 0, 0, null);
                used.add(part.label());
            }
        }
        finally {
            graphics.dispose();
        }

        if(used.isEmpty())
            return null;

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", png);
        return new Composed(png.toByteArray(), used);
    }

    private record Part(BufferedImage image, String label) {}

    // A random valid part of the layer. Invalid images are reported and skipped.
    private Part pickPart(String layer, List<String> folders, String gender) {
        List<File> candidates = new ArrayList<>(this.candidates(layer, folders, gender));
        Collections.shuffle(candidates, this.random);

        for(File file : candidates) {
            try {
                BufferedImage image = ImageIO.read(file);
                if(image == null) {
                    this.warn.accept("Skipped skin part " + this.label(file) + ": not a readable image.");
                    continue;
                }
                if(image.getWidth() != SIZE || image.getHeight() != SIZE) {
                    this.warn.accept("Skipped skin part " + this.label(file) + ": it is " + image.getWidth() + "x"
                            + image.getHeight() + ", but parts must be " + SIZE + "x" + SIZE + ".");
                    continue;
                }
                return new Part(image, this.label(file));
            }
            catch(IOException e) {
                this.warn.accept("Skipped skin part " + this.label(file) + ": " + e.getMessage());
            }
        }
        return null;
    }

    // The parts of this layer in all the culture's folders if there are any, else the shared ones.
    private List<File> candidates(String layer, List<String> folders, String gender) {
        List<File> cultural = new ArrayList<>();
        for(String folder : folders)
            cultural.addAll(pngsIn(new File(new File(this.culturesFolder, folder), layer), gender));
        if(!cultural.isEmpty())
            return cultural;
        return pngsIn(new File(this.partsFolder, layer), gender);
    }

    // The PNGs in the gender's subfolder and directly in the folder.
    private static List<File> pngsIn(File layerFolder, String gender) {
        List<File> files = new ArrayList<>();
        files.addAll(pngs(new File(layerFolder, gender)));
        files.addAll(pngs(layerFolder));
        return files;
    }

    private static List<File> pngs(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if(files == null)
            return List.of();
        Arrays.sort(files);
        return Arrays.asList(files);
    }

    // The part's path from the skins folder, e.g. "cultures/roman/torso/male/tunic.png".
    private String label(File file) {
        File skins = this.partsFolder.getParentFile();
        String path = skins.toPath().relativize(file.toPath()).toString();
        return path.replace(File.separatorChar, '/');
    }
}
