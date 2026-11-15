package com.sharksmp.clans.listener;

import com.sharksmp.clans.SharkClans;
import com.sharksmp.clans.model.Clan;
import com.sharksmp.clans.util.Text;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Chat com tag do cla, chat exclusivo do cla e entrada de texto da GUI. */
public final class ChatListener implements Listener {

    private final SharkClans plugin;

    public ChatListener(SharkClans plugin) {
        this.plugin = plugin;
    }

    /** Captura o texto digitado quando a GUI pediu um nome/tag (nao aparece no chat publico). */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPrompt(AsyncChatEvent event) {
        final Player player = event.getPlayer();
        if (!plugin.getGui().hasPrompt(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        final String text = Text.plain(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getGui().handlePrompt(player, text));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final Clan clan = plugin.getClanManager().getClanOf(player.getUniqueId());
        if (clan == null) {
            return;
        }

        // Chat exclusivo do cla
        if (plugin.getActions().isClanChat(player.getUniqueId())) {
            event.setCancelled(true);
            plugin.getActions().sendClanChat(player, clan, event.message());
            return;
        }

        // Tag antes do nome no chat publico
        if (!plugin.getConfig().getBoolean("chat.show-tag", true)) {
            return;
        }
        final String tagPrefix = plugin.getClanManager().tagPrefix(clan);
        final String separator = plugin.getConfig().getString("chat.separator", "&7: &f");
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) -> {
            Component out = Text.color(tagPrefix);
            out = out.append(sourceDisplayName);
            out = out.append(Text.color(separator));
            out = out.append(message);
            return out;
        }));
    }
}
