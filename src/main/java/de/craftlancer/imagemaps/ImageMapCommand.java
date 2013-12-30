package de.craftlancer.imagemaps;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public class ImageMapCommand implements TabExecutor
{
    ImageMaps plugin;
    
    public ImageMapCommand(ImageMaps plugin)
    {
        this.plugin = plugin;
    }
    
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args)
    {
        switch (args.length)
        {
            case 1:
                return getMatches(args[0], new File(plugin.getDataFolder(), "images").list());
            default:
                return null;
        }
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (!sender.hasPermission("imagemaps.command") || !(sender instanceof Player))
            return false;
        
        if (args.length < 1)
            return false;
        
        plugin.startPlacing((Player) sender, args[0]);
        
        sender.sendMessage("started placing");
        
        return true;
    }
    
    /**
     * Get all values of a String array which start with a given String
     * 
     * @param value
     *            the given String
     * @param list
     *            the array
     * @return a List of all matches
     */
    public static List<String> getMatches(String value, String[] list)
    {
        List<String> result = new LinkedList<String>();
        
        for (String str : list)
            if (str.startsWith(value))
                result.add(str);
        
        return result;
    }
    
}