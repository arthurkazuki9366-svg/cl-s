package com.sharksmp.clans.manager;

import com.sharksmp.clans.SharkClans;
import com.sharksmp.clans.model.Clan;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Convites pendentes (em memoria, com expiracao configuravel). */
public final class InviteManager {

    public static final class Invite {
        public final String clanName;
        public final UUID inviter;
        public final String inviterName;
        public final long expiresAt;

        Invite(String clanName, UUID inviter, String inviterName, long expiresAt) {
            this.clanName = clanName;
            this.inviter = inviter;
            this.inviterName = inviterName;
            this.expiresAt = expiresAt;
        }
    }

    private final SharkClans plugin;
    private final Map<UUID, List<Invite>> invites = new ConcurrentHashMap<>();

    public InviteManager(SharkClans plugin) {
        this.plugin = plugin;
    }

    public int expireSeconds() {
        return Math.max(5, plugin.getConfig().getInt("clan.invite-expire-seconds", 60));
    }

    /** Retorna false se o jogador ja possui um convite pendente deste cla. */
    public boolean add(UUID target, Clan clan, Player inviter) {
        List<Invite> list = invites.computeIfAbsent(target, k -> new CopyOnWriteArrayList<>());
        purge(list);
        for (Invite i : list) {
            if (i.clanName.equalsIgnoreCase(clan.getName())) {
                return false;
            }
        }
        long expires = System.currentTimeMillis() + expireSeconds() * 1000L;
        list.add(new Invite(clan.getName(), inviter.getUniqueId(), inviter.getName(), expires));
        return true;
    }

    /** Convites validos (nao expirados e de clas que ainda existem), do mais antigo ao mais novo. */
    public List<Invite> get(UUID target) {
        List<Invite> list = invites.get(target);
        if (list == null) {
            return new ArrayList<>();
        }
        purge(list);
        return new ArrayList<>(list);
    }

    public Invite find(UUID target, String clanName) {
        for (Invite i : get(target)) {
            if (i.clanName.equalsIgnoreCase(clanName)) {
                return i;
            }
        }
        return null;
    }

    public void remove(UUID target, String clanName) {
        List<Invite> list = invites.get(target);
        if (list != null) {
            list.removeIf(i -> i.clanName.equalsIgnoreCase(clanName));
        }
    }

    public void clear(UUID target) {
        invites.remove(target);
    }

    public void clearClan(String clanName) {
        for (List<Invite> list : invites.values()) {
            list.removeIf(i -> i.clanName.equalsIgnoreCase(clanName));
        }
    }

    private void purge(List<Invite> list) {
        long now = System.currentTimeMillis();
        ClanManager cm = plugin.getClanManager();
        list.removeIf(i -> i.expiresAt < now || cm.getClan(i.clanName) == null);
    }
}
