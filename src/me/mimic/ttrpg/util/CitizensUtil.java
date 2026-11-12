package me.mimic.ttrpg.util;

import me.mimic.ttrpg.core.Culture;
import me.mimic.ttrpg.core.Gender;
import me.mimic.ttrpg.core.traits.CultureTrait;
import me.mimic.ttrpg.core.traits.GenderTrait;
import net.citizensnpcs.Citizens;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.AttributeTrait;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.EntityType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;

public class CitizensUtil {
    public static NPC spawnNPC(String name, Location location) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.setProtected(false);
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE, false);
        npc.setAlwaysUseNameHologram(false);
        ((AttributeTrait) npc.getOrAddTrait(AttributeTrait.class)).setAttributeValue(Attribute.SCALE, MathUtil.random(0.9, 1.1));
        npc.spawn(location);
        ((Citizens) CitizensAPI.getPlugin()).storeNPCs();
        return npc;
    }

    // Finds where a character at this location can stand: the feet are moved up out of solid blocks
    // (or down onto the ground when floating) and placed on top of the block below,
    // so slabs and stairs are taken into account.
    public static Location standingLocation(Location location) {
        Block feet = location.getBlock();
        for(int i = 0; i < 3 && !feet.isPassable(); i++)
            feet = feet.getRelative(BlockFace.UP);
        for(int i = 0; i < 2 && feet.isPassable() && feet.getRelative(BlockFace.DOWN).isPassable(); i++)
            feet = feet.getRelative(BlockFace.DOWN);

        Block ground = feet.getRelative(BlockFace.DOWN);
        Location standing = location.clone();
        standing.setY(ground.isPassable() ? feet.getY() : ground.getBoundingBox().getMaxY());
        return standing;
    }

    // Where a character standing at this location sits down: on the step of the stairs or the bottom slab
    // under its feet, or else on top of the ground it stands on.
    public static Location seatLocation(Location feet) {
        Location seat = feet.clone();
        Block block = feet.getWorld().getBlockAt(feet.getBlockX(), (int) Math.ceil(feet.getY()) - 1, feet.getBlockZ());
        BlockData data = block.getBlockData();

        boolean lowerStairs = (data instanceof Stairs stairs) && stairs.getHalf() == Bisected.Half.BOTTOM;
        boolean bottomSlab = (data instanceof Slab slab) && slab.getType() == Slab.Type.BOTTOM;
        if(lowerStairs || bottomSlab)
            seat.setY(block.getY() + 0.5);
        else if(!block.isPassable())
            seat.setY(block.getBoundingBox().getMaxY());
        // Over an empty block (e.g. while falling), sit where the feet are.
        return seat;
    }

    // Whether a character of this size fits at this location: its whole hitbox is clear of blocks and liquids,
    // and something solid is right under it. Checking the hitbox, not just the block at the feet,
    // keeps a character next to a wall from ending up partly inside it.
    public static boolean canStandAt(Location location, double width, double height) {
        double half = width / 2;
        BoundingBox body = new BoundingBox(
                location.getX() - half, location.getY() + 0.001, location.getZ() - half,
                location.getX() + half, location.getY() + height, location.getZ() + half);
        BoundingBox feet = new BoundingBox(
                body.getMinX(), location.getY() - 0.05, body.getMinZ(),
                body.getMaxX(), location.getY(), body.getMaxZ());

        return !touchesBlocks(location.getWorld(), body, true) && touchesBlocks(location.getWorld(), feet, false);
    }

    // Whether the box overlaps the collision shape of any block (or, if asked, any liquid).
    private static boolean touchesBlocks(World world, BoundingBox box, boolean liquidsCount) {
        for(int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for(int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                for(int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if(liquidsCount && block.isLiquid())
                        return true;
                    // Collision shapes are relative to the block's corner.
                    if(block.getCollisionShape().overlaps(box.clone().shift(-x, -y, -z)))
                        return true;
                }
            }
        }
        return false;
    }

    // Whether a character of this size can be sent to the spot from the origin: it fits there,
    // it is within 3 blocks of the origin's height, and the origin can see it (so it isn't sent through a wall).
    public static boolean isReachableSpot(Location origin, Location spot, double width, double height) {
        if(Math.abs(spot.getY() - origin.getY()) > 3 || !canStandAt(spot, width, height))
            return false;

        Location eye = origin.clone().add(0, 1, 0);
        Vector toSpot = spot.clone().add(0, 1, 0).toVector().subtract(eye.toVector());
        return toSpot.lengthSquared() <= 0.01
                || origin.getWorld().rayTraceBlocks(eye, toSpot.clone().normalize(), toSpot.length(), FluidCollisionMode.NEVER, true) == null;
    }

    // A random reachable spot within the radius of the search center, facing the origin and at least
    // a block away from the spots already taken (or closer if nothing else is free). Null if none is found.
    private static Location findSpot(Location searchCenter, Location origin, double radius, Collection<Location> taken, double width, double height) {
        Location fallback = null;

        for(int attempt = 0; attempt < 30; attempt++) {
            double angle = MathUtil.random.nextDouble() * 2 * Math.PI;
            double distance = radius * Math.sqrt(MathUtil.random.nextDouble());
            Location candidate = searchCenter.clone().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Location standing = standingLocation(candidate);
            if(!isReachableSpot(origin, standing, width, height))
                continue;

            faceTowards(standing, origin);
            if(!isTaken(standing, taken))
                return standing;
            if(fallback == null)
                fallback = standing;
        }
        return fallback;
    }

    private static boolean isTaken(Location spot, Collection<Location> taken) {
        return taken.stream().anyMatch(other -> other.getWorld() == spot.getWorld() && other.distanceSquared(spot) < 1.0);
    }

    // A random spot within the radius of the center where a character of this size can stand (see findSpot).
    // Falls back to the center when no such spot is found.
    public static Location scatterLocation(Location center, double radius, Collection<Location> taken, double width, double height) {
        Location spot = findSpot(center, center, radius, taken, width, height);
        return (spot != null) ? spot : center.clone();
    }

    // The spot of a formation (line, circle...): the target itself if a character fits there and the origin can see it,
    // otherwise a free spot close to it, otherwise somewhere around the origin. Always faces the origin.
    public static Location formationLocation(Location target, Location origin, Collection<Location> taken, double width, double height) {
        Location exact = standingLocation(target);
        if(isReachableSpot(origin, exact, width, height) && !isTaken(exact, taken)) {
            faceTowards(exact, origin);
            return exact;
        }
        Location near = findSpot(target, origin, 1.5, taken, width, height);
        return (near != null) ? near : scatterLocation(origin, 3, taken, width, height);
    }

    private static void faceTowards(Location location, Location target) {
        Vector direction = target.toVector().subtract(location.toVector()).setY(0);
        if(direction.lengthSquared() < 0.0001)
            return;
        location.setDirection(direction);
        location.setPitch(0);
    }

    public static NPC spawnNPC(String name, Location location, Culture culture, Gender gender) {
        NPC npc = spawnNPC(name, location);
        npc.getOrAddTrait(CultureTrait.class).setCulture(culture);
        npc.getOrAddTrait(GenderTrait.class).setGender(gender);
        ((Citizens) CitizensAPI.getPlugin()).storeNPCs();
        return npc;
    }
}
