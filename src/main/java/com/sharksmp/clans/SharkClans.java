package com.sharksmp.clans;

import com.sharksmp.clans.command.ClanCommand;
import com.sharksmp.clans.gui.ClanGui;
import com.sharksmp.clans.listener.ChatListener;
import com.sharksmp.clans.listener.ConnectionListener;
import com.sharksmp.clans.manager.ClanActions;
import com.sharksmp.clans.manager.ClanManager;
import com.sharksmp.clans.manager.InviteManager;
import com.sharksmp.clans.util.Msg;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin principal do SharkSMP: sistema de clas. */
public final class SharkClans extends JavaPlugin {

    private Msg msg;
    private ClanManager clanManager;
    private InviteManager inviteManager;
    private ClanActions actions;
    private ClanGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.msg = new Msg(this);
        this.clanManager = new ClanManager(this);
        this.clanManager.load();
        this.inviteManager = new InviteManager(this);
        this.actions = new ClanActions(this);
        this.gui = new ClanGui(this);

        PluginCommand command = getCommand("clan");
        if (command == null) {
            getLogger().severe("Comando /clan nao encontrado no plugin.yml! Desativando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ClanCommand executor = new ClanCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(gui, this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);

        getLogger().info("SharkClans ativado.");
    }

    @Override
    public void onDisable() {
        if (clanManager != null) {
            clanManager.saveNow();
        }
    }

    /** Recarrega config.yml e clans.yml. */
    public void reloadAll() {
        reloadConfig();
        clanManager.load();
    }

    public Msg getMsg() {
        return msg;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public InviteManager getInviteManager() {
        return inviteManager;
    }

    public ClanActions getActions() {
        return actions;
    }

    public ClanGui getGui() {
        return gui;
    }
}
