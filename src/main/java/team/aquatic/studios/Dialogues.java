package team.aquatic.studios;

import org.bukkit.plugin.java.JavaPlugin;
import team.aquatic.studios.commands.Executor;
import team.aquatic.studios.tools.Files;

public final class Dialogues extends JavaPlugin {

    private static Dialogues instance;
    private Files dialoguesConfig;
    private Files playerDataConfig;

    @Override
    public void onEnable() {
        instance = this;

        dialoguesConfig = new Files(this, "dialogues.yml");
        playerDataConfig = new Files(this, "playerdata.yml");

        getCommand("dialogues").setExecutor(new Executor(this));
    }

    @Override
    public void onDisable() {
    }

    public static Dialogues getInstance() {
        return instance;
    }

    public Files getDialoguesConfig() {
        return dialoguesConfig;
    }

    public Files getPlayerDataConfig() {
        return playerDataConfig;
    }
}
