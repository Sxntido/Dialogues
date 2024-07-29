package team.aquatic.studios.commands;

import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import team.aquatic.studios.Dialogues;
import team.aquatic.studios.tools.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Executor implements CommandExecutor {

    private final Dialogues plugin;

    public Executor(Dialogues plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showWelcomeMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReloadCommand(sender);
                break;
            case "deleteall":
                handleDeleteAllCommand(sender);
                break;
            default:
                handleDialogueCommand(sender, args[0]);
                break;
        }

        return true;
    }

    private void showWelcomeMessage(CommandSender sender) {
        String[] messages = {
                "&r",
                "    &#FF65ECAcademy-Bukkit &fv1.0 created by Sxntido",
                "     &fWork done by &#00A8FFAquatic Studios",
                "&r"
        };
        for (String message : messages) {
            sender.sendMessage(Utils.Color(Utils.translateHexColorCodes(message)));
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        if (sender.hasPermission("dialogues.reload")) {
            plugin.reloadConfig();
            plugin.getDialoguesConfig().reloadConfig();
            plugin.getPlayerDataConfig().reloadConfig();
            sender.sendMessage(Utils.Color("&aConfigurations reloaded."));
        } else {
            sender.sendMessage(Utils.Color("&cYou do not have permission to use this command."));
        }
    }

    private void handleDeleteAllCommand(CommandSender sender) {
        if (sender.hasPermission("dialogues.deleteall")) {
            plugin.getPlayerDataConfig().getConfig().set("progress", null);
            plugin.getPlayerDataConfig().getConfig().set("players", null);
            plugin.getPlayerDataConfig().saveConfig();
            sender.sendMessage(Utils.Color("&cAll player data has been deleted."));
        } else {
            sender.sendMessage(Utils.Color("&cYou do not have permission to use this command."));
        }
    }

    private void handleDialogueCommand(CommandSender sender, String dialogueName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Utils.Color("&cThis command can only be executed by a player."));
            return;
        }

        Player player = (Player) sender;
        FileConfiguration dialoguesConfig = plugin.getDialoguesConfig().getConfig();

        if (!dialoguesConfig.contains("dialogues." + dialogueName)) {
            player.sendMessage(Utils.Color("&cThe specified dialogue does not exist."));
            return;
        }

        ConfigurationSection dependSection = dialoguesConfig.getConfigurationSection("dialogues." + dialogueName + ".depend");
        if (dependSection != null) {
            String requiredDialogue = dependSection.getString("dialogue");
            if (requiredDialogue != null && !hasPlayerCompletedDialogue(player, requiredDialogue)) {
                player.sendMessage(Utils.Color(dependSection.getString("message")));
                String dependSound = dependSection.getString("sound");
                if (dependSound != null) {
                    player.playSound(player.getLocation(), Sound.valueOf(dependSound), 1.0f, 1.0f);
                }
                return;
            }
        }

        int currentStep = getCurrentStep(player, dialogueName);
        ConfigurationSection dialogueSection = dialoguesConfig.getConfigurationSection("dialogues." + dialogueName);

        List<String> keys = new ArrayList<>(dialogueSection.getKeys(false));
        Collections.sort(keys);
        keys.sort(String::compareTo);

        if (currentStep >= keys.size()) {
            player.sendMessage(Utils.Color("&cOops, you have already completed this mission."));
            return;
        }

        String key = keys.get(currentStep);
        List<String> actions = dialogueSection.getStringList(key);
        for (String action : actions) {
            executeAction(player, action);
        }

        recordDialogueProgress(player, dialogueName, currentStep + 1);

        if (currentStep + 1 >= keys.size()) {
            recordDialogueCompleted(player, dialogueName);
        }
    }

    private void executeAction(Player player, String action) {
        String[] parts = action.split(" ", 2);
        if (parts.length < 2) {
            plugin.getLogger().warning("Invalid action: " + action);
            return;
        }

        String type = parts[0];
        String argument = parts[1];

        switch (type.toUpperCase()) {
            case "[MESSAGE]":
                String messageWithoutColors = argument.replace("%player_name%", player.getName());
                String coloredMessage = Utils.translateHexColorCodes(Utils.Color(messageWithoutColors));
                String finalMessage = Utils.CenterMessage(coloredMessage);
                player.sendMessage(finalMessage);
                break;
            case "[SOUND]":
                try {
                    Sound sound = Sound.valueOf(argument);
                    player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound: " + argument);
                }
                break;
            case "[EFFECT]":
                String[] effectParts = argument.split(";");
                if (effectParts.length != 3) {
                    plugin.getLogger().warning("Invalid effect: " + argument);
                    break;
                }
                try {
                    PotionEffectType effect = PotionEffectType.getByName(effectParts[0]);
                    int duration = Integer.parseInt(effectParts[1]) * 20;
                    int amplifier = Integer.parseInt(effectParts[2]);
                    player.addPotionEffect(new PotionEffect(effect, duration, amplifier));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid effect: " + effectParts[0]);
                }
                break;
            case "[COMMAND]":
                String command = argument.replace("%player_name%", player.getName());
                player.performCommand(command);
                break;
            case "[CONSOLE]":
                String consoleCommand = argument.replace("%player_name%", player.getName());
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), consoleCommand);
                break;
            default:
                plugin.getLogger().warning("Unrecognized action: " + type);
                break;
        }
    }

    private int getCurrentStep(Player player, String dialogueName) {
        FileConfiguration dataPlayer = plugin.getPlayerDataConfig().getConfig();
        return dataPlayer.getInt("progress." + player.getUniqueId() + "." + dialogueName, 0);
    }

    private void recordDialogueProgress(Player player, String dialogueName, int step) {
        FileConfiguration dataPlayer = plugin.getPlayerDataConfig().getConfig();
        dataPlayer.set("progress." + player.getUniqueId() + "." + dialogueName, step);
        plugin.getPlayerDataConfig().saveConfig();
    }

    private boolean hasPlayerCompletedDialogue(Player player, String dialogueName) {
        FileConfiguration dataPlayer = plugin.getPlayerDataConfig().getConfig();
        List<String> completedPlayers = dataPlayer.getStringList("players." + dialogueName);
        return completedPlayers.contains(player.getUniqueId().toString());
    }

    private void recordDialogueCompleted(Player player, String dialogueName) {
        FileConfiguration dataPlayer = plugin.getPlayerDataConfig().getConfig();
        List<String> completedPlayers = dataPlayer.getStringList("players." + dialogueName);
        completedPlayers.add(player.getUniqueId().toString());
        dataPlayer.set("players." + dialogueName, completedPlayers);
        plugin.getPlayerDataConfig().saveConfig();
    }
}
