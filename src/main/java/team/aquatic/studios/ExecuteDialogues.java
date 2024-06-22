package team.aquatic.studios;

import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExecuteDialogues implements CommandExecutor {

    private final Dialogues plugin;

    public ExecuteDialogues(Dialogues plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            mostrarMensajeBienvenida(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                manejarComandoReload(sender);
                break;
            case "deleteall":
                manejarComandoDeleteAll(sender);
                break;
            default:
                manejarComandoDialogo(sender, args[0]);
                break;
        }

        return true;
    }

    private void mostrarMensajeBienvenida(CommandSender sender) {
        String[] mensajes = {
                "&r",
                "    &#FF65ECAcademy-Bukkit &fv1.0 creado por Sxntido",
                "     &fTrabajo realizado por &#00A8FFAquatic Studios",
                "&r"
        };
        for (String mensaje : mensajes) {
            sender.sendMessage(Utils.Color(Utils.translateHexColorCodes(mensaje)));
        }
    }

    private void manejarComandoReload(CommandSender sender) {
        if (sender.hasPermission("dialogues.reload")) {
            plugin.reloadConfig();
            plugin.getDialoguesConfig().reloadConfig();
            plugin.getPlayerDataConfig().reloadConfig();
            sender.sendMessage(Utils.Color("&aConfiguraciones recargadas."));
        } else {
            sender.sendMessage(Utils.Color("&cNo tienes permiso para usar este comando."));
        }
    }

    private void manejarComandoDeleteAll(CommandSender sender) {
        if (sender.hasPermission("dialogues.deleteall")) {
            plugin.getPlayerDataConfig().getConfig().set("progreso", null);
            plugin.getPlayerDataConfig().getConfig().set("jugadores", null);
            plugin.getPlayerDataConfig().saveConfig();
            sender.sendMessage(Utils.Color("&cTodos los datos de los jugadores han sido eliminados."));
        } else {
            sender.sendMessage(Utils.Color("&cNo tienes permiso para usar este comando."));
        }
    }

    private void manejarComandoDialogo(CommandSender sender, String dialogoName) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Utils.Color("&cEste comando solo puede ser ejecutado por un jugador."));
            return;
        }

        Player player = (Player) sender;
        FileConfiguration dialoguesConfig = plugin.getDialoguesConfig().getConfig();

        if (!dialoguesConfig.contains("dialogues." + dialogoName)) {
            player.sendMessage(Utils.Color("&cEl diálogo especificado no existe."));
            return;
        }

        ConfigurationSection dependSection = dialoguesConfig.getConfigurationSection("dialogues." + dialogoName + ".depend");
        if (dependSection != null) {
            String requiredDialogue = dependSection.getString("dialogue");
            if (requiredDialogue != null && !jugadorHaCompletadoDialogo(player, requiredDialogue)) {
                player.sendMessage(Utils.Color(dependSection.getString("message")));
                String dependSound = dependSection.getString("sound");
                if (dependSound != null) {
                    player.playSound(player.getLocation(), Sound.valueOf(dependSound), 1.0f, 1.0f);
                }
                return;
            }
        }

        int pasoActual = obtenerPasoActual(player, dialogoName);
        ConfigurationSection dialogoSection = dialoguesConfig.getConfigurationSection("dialogues." + dialogoName);

        List<String> keys = new ArrayList<>(dialogoSection.getKeys(false));
        Collections.sort(keys);
        keys.sort(String::compareTo);

        if (pasoActual >= keys.size()) {
            player.sendMessage(Utils.Color("&cUps, ya has completado este misión."));
            return;
        }

        String key = keys.get(pasoActual);
        List<String> acciones = dialogoSection.getStringList(key);
        for (String accion : acciones) {
            ejecutarAccion(player, accion);
        }

        registrarProgresoDialogo(player, dialogoName, pasoActual + 1);

        if (pasoActual + 1 >= keys.size()) {
            registrarDialogoCompletado(player, dialogoName);
        }
    }

    private void ejecutarAccion(Player player, String accion) {
        String[] partes = accion.split(" ", 2);
        if (partes.length < 2) {
            plugin.getLogger().warning("Accion no valida: " + accion);
            return;
        }

        String tipo = partes[0];
        String argumento = partes[1];

        switch (tipo.toUpperCase()) {
            case "[MESSAGE]":
                String mensajeSinColores = argumento.replace("%player_name%", player.getName());
                String mensajeColoreado = Utils.translateHexColorCodes(Utils.Color(mensajeSinColores));
                String mensajeFinal = Utils.CenterMessage(mensajeColoreado);
                player.sendMessage(mensajeFinal);
                break;
            case "[SOUND]":
                try {
                    Sound sonido = Sound.valueOf(argumento);
                    player.playSound(player.getLocation(), sonido, 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Sonido no valido: " + argumento);
                }
                break;
            case "[EFFECT]":
                String[] efectoPartes = argumento.split(";");
                if (efectoPartes.length != 3) {
                    plugin.getLogger().warning("Efecto no valido: " + argumento);
                    break;
                }
                try {
                    PotionEffectType efecto = PotionEffectType.getByName(efectoPartes[0]);
                    int duracion = Integer.parseInt(efectoPartes[1]) * 20;
                    int amplificador = Integer.parseInt(efectoPartes[2]);
                    player.addPotionEffect(new PotionEffect(efecto, duracion, amplificador));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Efecto no valido: " + efectoPartes[0]);
                }
                break;
            case "[COMMAND]":
                String comando = argumento.replace("%player_name%", player.getName());
                player.performCommand(comando);
                break;
            case "[CONSOLE]":
                String comandoConsola = argumento.replace("%player_name%", player.getName());
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), comandoConsola);
                break;
            default:
                plugin.getLogger().warning("Accion no reconocida: " + tipo);
                break;
        }
    }

    private int obtenerPasoActual(Player player, String dialogoName) {
        FileConfiguration dataPlayer = plugin.getPlayerDataConfig().getConfig();
        return dataPlayer.getInt("progreso." + player.getUniqueId() + "." + dialogoName, 0);
    }

    private void registrarProgresoDialogo(Player player, String dialogoName, int paso) {
        FileConfiguration dataPlayer = plugin.getPlayerDataConfig().getConfig();
        dataPlayer.set("progreso." + player.getUniqueId() + "." + dialogoName, paso);
        plugin.getPlayerDataConfig().saveConfig();
    }

    private boolean jugadorHaCompletadoDialogo(Player player, String dialogoName) {
        FileConfiguration dataPlayer = plugin.getPlayerDataConfig().getConfig();
        List<String> jugadoresCompletados = dataPlayer.getStringList("jugadores." + dialogoName);
        return jugadoresCompletados.contains(player.getUniqueId().toString());
    }

    private void registrarDialogoCompletado(Player player, String dialogoName) {
        FileConfiguration dataPlayer = plugin.getPlayerDataConfig().getConfig();
        List<String> jugadoresCompletados = dataPlayer.getStringList("jugadores." + dialogoName);
        jugadoresCompletados.add(player.getUniqueId().toString());
        dataPlayer.set("jugadores." + dialogoName, jugadoresCompletados);
        plugin.getPlayerDataConfig().saveConfig();
    }
}
