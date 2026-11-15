package com.sharksmp.clans.util;

import com.sharksmp.clans.SharkClans;
import com.sharksmp.clans.model.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Acesso as mensagens do config.yml (secao "messages"). Sempre le o config atual (suporta /reload). */
public final class Msg {

    private final SharkClans plugin;

    public Msg(SharkClans plugin) {
        this.plugin = plugin;
    }

    /** Substitui {chave} por valor; os argumentos sao pares chave, valor. */
    public static String replace(String text, Object... kv) {
        String result = text;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            result = result.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return result;
    }

    public String prefix() {
        return plugin.getConfig().getString("prefix", "");
    }

    public String raw(String key, Object... kv) {
        String text = plugin.getConfig().getString("messages." + key);
        if (text == null) {
            text = "&cMensagem ausente no config.yml: messages." + key;
        }
        return replace(text, kv);
    }

    public List<String> rawList(String key, Object... kv) {
        List<String> out = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("messages." + key)) {
            out.add(replace(line, kv));
        }
        return out;
    }

    public Component component(String key, Object... kv) {
        return Text.color(raw(key, kv));
    }

    /** Envia a mensagem com o prefixo do plugin. */
    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(Text.color(prefix() + raw(key, kv)));
    }

    /** Envia a mensagem sem prefixo. */
    public void sendRaw(CommandSender to, String key, Object... kv) {
        to.sendMessage(Text.color(raw(key, kv)));
    }

    /** Envia para todos os jogadores online (com prefixo). */
    public void broadcast(String key, Object... kv) {
        Component c = Text.color(prefix() + raw(key, kv));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(c);
        }
        Bukkit.getConsoleSender().sendMessage(c);
    }

    public String roleName(Role role) {
        switch (role) {
            case LEADER:
                return plugin.getConfig().getString("roles.leader", "Líder");
            case SUB_LEADER:
                return plugin.getConfig().getString("roles.sub-leader", "Sub-líder");
            default:
                return plugin.getConfig().getString("roles.member", "Membro");
        }
    }

    public static String roleColor(Role role) {
        switch (role) {
            case LEADER:
                return "&6";
            case SUB_LEADER:
                return "&b";
            default:
                return "&7";
        }
    }
}
