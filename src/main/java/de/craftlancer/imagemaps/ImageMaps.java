package de.craftlancer.imagemaps;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.Metrics;

import javax.imageio.ImageIO;

public class ImageMaps extends JavaPlugin implements Listener
{
    public static final int MAP_WIDTH = 128;
    public static final int MAP_HEIGHT = 128;
    
    private Map<String, PlacingCacheEntry> placing = new HashMap<String, PlacingCacheEntry>();
    private Map<Short, ImageMap> maps = new HashMap<Short, ImageMap>();
    private Map<String, BufferedImage> images = new HashMap<String, BufferedImage>();
    private List<Short> sendList = new ArrayList<Short>();
    private FastSendTask sendTask;
    
    @Override
    public void onEnable()
    {
        if (!new File(getDataFolder(), "images").exists())
            new File(getDataFolder(), "images").mkdirs();
        
        int sendPerTicks = getConfig().getInt("sendPerTicks", 20);
        int mapsPerSend = getConfig().getInt("mapsPerSend", 8);
        
        loadMaps();
        getCommand("imagemap").setExecutor(new ImageMapCommand(this));
        getServer().getPluginManager().registerEvents(this, this);
        sendTask = new FastSendTask(this, mapsPerSend);
        getServer().getPluginManager().registerEvents(sendTask, this);
        sendTask.runTaskTimer(this, sendPerTicks, sendPerTicks);
        
        try
        {
            Metrics metrics = new Metrics(this);
            metrics.start();
        }
        catch (IOException e)
        {
            getLogger().severe("Failed to load Metrics!");
        }
    }
    
    @Override
    public void onDisable()
    {
        saveMaps();
        getServer().getScheduler().cancelTasks(this);
    }
    
    public List<Short> getFastSendList()
    {
        return sendList;
    }
    
    public void startPlacing(Player p, String image, boolean fastsend) {
        placing.put(p.getName(), new PlacingCacheEntry(image, fastsend));
    }
    
    public boolean placeImage(Block block, BlockFace face, PlacingCacheEntry cache, BufferedImage image)
    {
        int xMod = 0;
        int zMod = 0;
        
        switch (face)
        {
            case EAST:
                zMod = -1;
                break;
            case WEST:
                zMod = 1;
                break;
            case SOUTH:
                xMod = 1;
                break;
            case NORTH:
                xMod = -1;
                break;
            default:
                getLogger().severe("Someone tried to create an image with an invalid block facing");
                return false;
        }

        if (image == null)
        {
            getLogger().severe("Someone tried to create an image with an invalid file!");
            return false;
        }

        Block b = block.getRelative(face);

        int width = (int) Math.ceil((double) image.getWidth() / (double) MAP_WIDTH);
        int height = (int) Math.ceil((double) image.getHeight() / (double) MAP_HEIGHT);

        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                if (!block.getRelative(x * xMod, -y, x * zMod).getType().isSolid())
                    return false;

        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                setItemFrame(b.getRelative(x * xMod, -y, x * zMod), image, face, x * MAP_WIDTH, y * MAP_HEIGHT, cache);

        return true;
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e)
    {
        if (!e.hasBlock())
            return;
        
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        
        if (!placing.containsKey(e.getPlayer().getName()))
            return;

        PlacingCacheEntry cache = placing.get(e.getPlayer().getName());

        BufferedImage image = loadImage(cache.getImage()) != null ? loadImage(cache.getImage()) : loadOnlineImage(cache.getImage());
        
        if (!placeImage(e.getClickedBlock(), e.getBlockFace(), cache, image))
            e.getPlayer().sendMessage("Can't place the image here!");
        else
            saveMaps();
        
        placing.remove(e.getPlayer().getName());
        
    }
    
    private void setItemFrame(Block bb, BufferedImage image, BlockFace face, int x, int y, PlacingCacheEntry cache)
    {
        bb.setType(Material.AIR);
        ItemFrame i = bb.getWorld().spawn(bb.getRelative(face.getOppositeFace()).getLocation(), ItemFrame.class);
        i.teleport(bb.getLocation());
        i.setFacingDirection(face, true);
        
        ItemStack item = getMapItem(cache.getImage(), x, y, image);
        i.setItem(item);
        
        short id = item.getDurability();
        
        if (cache.isFastSend() && !sendList.contains(id))
        {
            sendList.add(id);
            sendTask.addToQueue(id);
        }
        
        maps.put(id, new ImageMap(cache.getImage(), x, y, sendList.contains(id)));
    }
    
    @SuppressWarnings("deprecation")
    private ItemStack getMapItem(String file, int x, int y, BufferedImage image)
    {
        ItemStack item = new ItemStack(Material.MAP);
        
        for (Entry<Short, ImageMap> entry : maps.entrySet())
            if (entry.getValue().isSimilar(file, x, y))
            {
                item.setDurability(entry.getKey());
                return item;
            }
        
        MapView map = getServer().createMap(getServer().getWorlds().get(0));
        for (MapRenderer r : map.getRenderers())
            map.removeRenderer(r);
        
        map.addRenderer(new ImageMapRenderer(image, x, y));
        
        item.setDurability(map.getId());
        
        return item;
    }

    private BufferedImage loadOnlineImage(String url) {

        if (images.containsKey(url))
            return images.get(url);

        BufferedImage image = null;
        try {
            image = ImageIO.read(new URL(url));
            images.put(url, image);
        } catch (Exception e) {

        }
        return image;
    }
    
    private BufferedImage loadImage(String file) {
        if (images.containsKey(file))
            return images.get(file);
        
        File f = new File(getDataFolder(), "images" + File.separatorChar + file);
        BufferedImage image = null;
        
        if (!f.exists())
            return null;
        
        try
        {
            image = ImageIO.read(f);
            images.put(file, image);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        
        return image;
    }
    
    @SuppressWarnings("deprecation")
    private void loadMaps()
    {
        File file = new File(getDataFolder(), "maps.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        for (String key : config.getKeys(false))
        {
            short id = Short.parseShort(key);
            
            MapView map = getServer().getMap(id);
            
            for (MapRenderer r : map.getRenderers())
                map.removeRenderer(r);
            
            String image = config.getString(key + ".image");
            int x = config.getInt(key + ".x");
            int y = config.getInt(key + ".y");
            boolean fastsend = config.getBoolean(key + ".fastsend", false);
            
            BufferedImage bimage = loadImage(image);
            
            if (bimage == null)
            {
                getLogger().warning("Image file " + image + " not found, removing this map!");
                continue;
            }
            
            if (fastsend)
                sendList.add(id);
            
            map.addRenderer(new ImageMapRenderer(loadImage(image), x, y));
            maps.put(id, new ImageMap(image, x, y, fastsend));
        }
    }
    
    private void saveMaps()
    {
        File file = new File(getDataFolder(), "maps.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        for (String key : config.getKeys(false))
            config.set(key, null);
        
        for (Entry<Short, ImageMap> e : maps.entrySet())
        {
            config.set(e.getKey() + ".image", e.getValue().getImage());
            config.set(e.getKey() + ".x", e.getValue().getX());
            config.set(e.getKey() + ".y", e.getValue().getY());
            config.set(e.getKey() + ".fastsend", e.getValue().isFastSend());
        }
        
        try
        {
            config.save(file);
        }
        catch (IOException e1)
        {
            getLogger().severe("Failed to save maps.yml!");
            e1.printStackTrace();
        }
    }
    
    @SuppressWarnings("deprecation")
    public void reloadImage(String file)
    {
        images.remove(file);
        BufferedImage image = loadImage(file);
        
        int width = (int) Math.ceil((double) image.getWidth() / (double) MAP_WIDTH);
        int height = (int) Math.ceil((double) image.getHeight() / (double) MAP_HEIGHT);
        
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
            {
                short id = getMapItem(file, x * MAP_WIDTH, y * MAP_HEIGHT, image).getDurability();
                MapView map = getServer().getMap(id);
                
                for (MapRenderer renderer : map.getRenderers())
                    if (renderer instanceof ImageMapRenderer)
                        ((ImageMapRenderer) renderer).recalculateInput(image, x * MAP_WIDTH, y * MAP_HEIGHT);
                
                sendTask.addToQueue(id);
            }
        
    }
}
