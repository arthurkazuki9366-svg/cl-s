package com.sharksmp.clans.model;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Modelo de dados de um cla. As alteracoes devem ser feitas via ClanManager. */
public final class Clan {

    private final String name;
    private volatile String tag;
    private volatile char tagColor;
    private final long createdAt;
    private volatile UUID leader;
    private final Map<UUID, Role> roles = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();

    public Clan(String name, String tag, char tagColor, long createdAt) {
        this.name = name;
        this.tag = tag;
        this.tagColor = tagColor;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public char getTagColor() {
        return tagColor;
    }

    public void setTagColor(char tagColor) {
        this.tagColor = tagColor;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public UUID getLeader() {
        return leader;
    }

    public Map<UUID, Role> getRoles() {
        return Collections.unmodifiableMap(roles);
    }

    public Role getRole(UUID id) {
        return roles.get(id);
    }

    public boolean isMember(UUID id) {
        return roles.containsKey(id);
    }

    public int size() {
        return roles.size();
    }

    public int countRole(Role role) {
        int n = 0;
        for (Role r : roles.values()) {
            if (r == role) {
                n++;
            }
        }
        return n;
    }

    public void addMember(UUID id, String playerName, Role role) {
        roles.put(id, role);
        names.put(id, playerName == null ? "?" : playerName);
        if (role == Role.LEADER) {
            leader = id;
        }
    }

    public void removeMember(UUID id) {
        roles.remove(id);
        names.remove(id);
        if (id.equals(leader)) {
            leader = null;
        }
    }

    public void setRole(UUID id, Role role) {
        if (roles.containsKey(id)) {
            roles.put(id, role);
        }
    }

    /** Transfere a lideranca; o lider antigo passa a ser sub-lider. */
    public void transferLeadership(UUID newLeader) {
        UUID old = leader;
        if (old != null && roles.containsKey(old)) {
            roles.put(old, Role.SUB_LEADER);
        }
        roles.put(newLeader, Role.LEADER);
        leader = newLeader;
    }

    /** Garante que exista exatamente um lider (usado ao carregar dados). */
    public void ensureLeader() {
        UUID chosen = null;
        for (Map.Entry<UUID, Role> e : roles.entrySet()) {
            if (e.getValue() == Role.LEADER) {
                chosen = e.getKey();
                break;
            }
        }
        if (chosen == null) {
            for (Map.Entry<UUID, Role> e : roles.entrySet()) {
                if (e.getValue() == Role.SUB_LEADER) {
                    chosen = e.getKey();
                    break;
                }
            }
        }
        if (chosen == null && !roles.isEmpty()) {
            chosen = roles.keySet().iterator().next();
        }
        if (chosen == null) {
            leader = null;
            return;
        }
        for (UUID id : new ArrayList<>(roles.keySet())) {
            if (id.equals(chosen)) {
                roles.put(id, Role.LEADER);
            } else if (roles.get(id) == Role.LEADER) {
                roles.put(id, Role.SUB_LEADER);
            }
        }
        leader = chosen;
    }

    public String getStoredName(UUID id) {
        return names.get(id);
    }

    public void setMemberName(UUID id, String playerName) {
        if (roles.containsKey(id) && playerName != null) {
            names.put(id, playerName);
        }
    }

    /** Nome do membro (usa o cache do servidor se o nome salvo for desconhecido). */
    public String getMemberName(UUID id) {
        String n = names.get(id);
        if (n == null || n.equals("?")) {
            String off = Bukkit.getOfflinePlayer(id).getName();
            if (off != null) {
                return off;
            }
        }
        return n == null ? "?" : n;
    }

    /** Procura um membro pelo nome (sem diferenciar maiusculas). */
    public UUID findMember(String playerName) {
        if (playerName == null) {
            return null;
        }
        for (UUID id : roles.keySet()) {
            if (getMemberName(id).equalsIgnoreCase(playerName)) {
                return id;
            }
        }
        return null;
    }

    /** Lider primeiro, depois sub-lideres, depois membros; ordem alfabetica dentro de cada grupo. */
    public List<UUID> sortedMembers() {
        List<UUID> list = new ArrayList<>(roles.keySet());
        list.sort((a, b) -> {
            int c = Integer.compare(roles.get(a).ordinal(), roles.get(b).ordinal());
            if (c != 0) {
                return c;
            }
            return getMemberName(a).compareToIgnoreCase(getMemberName(b));
        });
        return list;
    }
}
