package com.sharksmp.clans.listener;

import com.sharksmp.clans.SharkClans;
import com.sharksmp.clans.model.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** Mantem nomes atualizados e limpa estados temporarios. */
public final class ConnectionListener implements Listener {

    private final SharkClans plugin;

    public ConnectionListener(SharkClans plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        Clan clan = plugin.getClanManager().getClanOf(id);
        if (clan != null && !p.getName().equals(clan.getStoredName(id))) {
            plugin.getClanManager().updateMemberName(clan, id, p.getName());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.getInviteManager().clear(id);
        plugin.getActions().removeChatState(id);
        plugin.getGui().cancelPrompt(id);
    }
}
