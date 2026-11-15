package com.sharksmp.clans.manager;

import com.sharksmp.clans.SharkClans;
import com.sharksmp.clans.model.Clan;
import com.sharksmp.clans.model.Role;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Guarda todos os clas, valida nomes/tags e persiste tudo em plugins/SharkClans/clans.yml. */
public final class ClanManager {

    private final SharkClans plugin;
    private final File file;
    private final Map<String, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, Clan> byPlayer = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();

    public ClanManager(SharkClans plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "clans.yml");
    }

    // ------------------------------------------------------------------ config

    public int maxMembers() {
        return Math.max(1, plugin.getConfig().getInt("clan.max-members", 10));
    }

    public int maxSubLeaders() {
        return plugin.getConfig().getInt("clan.max-sub-leaders", 3);
    }

    public int nameMin() {
        return Math.max(1, plugin.getConfig().getInt("clan.name.min-length", 3));
    }

    public int nameMax() {
        return Math.max(nameMin(), plugin.getConfig().getInt("clan.name.max-length", 16));
    }

    public int tagMin() {
        return Math.max(1, plugin.getConfig().getInt("clan.tag.min-length", 2));
    }

    public int tagMax() {
        return Math.max(tagMin(), plugin.getConfig().getInt("clan.tag.max-length", 5));
    }

    public char defaultColor() {
        String c = plugin.getConfig().getString("clan.tag.default-color", "b");
        char ch = (c == null || c.isEmpty()) ? 'b' : Character.toLowerCase(c.charAt(0));
        return isValidColor(ch) ? ch : 'b';
    }

    public static boolean isValidColor(char c) {
        return "0123456789abcdef".indexOf(Character.toLowerCase(c)) >= 0;
    }

    /** Texto da tag como aparece no chat, ex.: "&8[&bSHARK&8] &r". */
    public String tagPrefix(Clan clan) {
        String format = plugin.getConfig().getString("clan.tag.chat-format", "&8[{color}{tag}&8] &r");
        return format.replace("{color}", "&" + clan.getTagColor()).replace("{tag}", clan.getTag());
    }

    // ------------------------------------------------------------------ consulta

    public Clan getClan(String name) {
        if (name == null) {
            return null;
        }
        return clans.get(name.toLowerCase(Locale.ROOT));
    }

    public Clan getClanOf(UUID player) {
        return byPlayer.get(player);
    }

    public List<Clan> getClans() {
        List<Clan> list = new ArrayList<>(clans.values());
        list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    public boolean isValidName(String name) {
        return name != null
                && name.length() >= nameMin()
                && name.length() <= nameMax()
                && name.matches("[A-Za-z0-9_]+");
    }

    public boolean isValidTag(String tag) {
        return tag != null
                && tag.length() >= tagMin()
                && tag.length() <= tagMax()
                && tag.matches("[A-Za-z0-9]+");
    }

    public boolean isNameTaken(String name) {
        return clans.containsKey(name.toLowerCase(Locale.ROOT));
    }

    /** Verifica se a tag ja existe em outro cla (ignora maiusculas/minusculas). */
    public boolean isTagTaken(String tag, Clan ignore) {
        for (Clan c : clans.values()) {
            if (c != ignore && c.getTag().equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Gera uma tag unica a partir do nome do cla; retorna null se nao conseguir. */
    public String generateTag(String name) {
        int min = tagMin();
        int max = tagMax();
        StringBuilder base = new StringBuilder(name.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT));
        if (base.length() == 0) {
            base.append("CLA");
        }
        while (base.length() < min) {
            base.append('X');
        }
        String b = base.toString();
        for (int i = 0; i < 100; i++) {
            String suffix = i == 0 ? "" : String.valueOf(i);
            int keep = Math.max(1, max - suffix.length());
            String cand = (b.length() > keep ? b.substring(0, keep) : b) + suffix;
            if (cand.length() >= min && cand.length() <= max && !isTagTaken(cand, null)) {
                return cand;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ alteracoes

    public Clan createClan(String name, String tag, UUID leader, String leaderName) {
        Clan clan = new Clan(name, tag, defaultColor(), System.currentTimeMillis());
        clan.addMember(leader, leaderName, Role.LEADER);
        clans.put(name.toLowerCase(Locale.ROOT), clan);
        byPlayer.put(leader, clan);
        saveAsync();
        return clan;
    }

    public void deleteClan(Clan clan) {
        for (UUID id : new ArrayList<>(clan.getRoles().keySet())) {
            byPlayer.remove(id);
        }
        clans.remove(clan.getName().toLowerCase(Locale.ROOT));
        saveAsync();
    }

    public void addMember(Clan clan, UUID id, String playerName) {
        clan.addMember(id, playerName, Role.MEMBER);
        byPlayer.put(id, clan);
        saveAsync();
    }

    public void removeMember(Clan clan, UUID id) {
        clan.removeMember(id);
        byPlayer.remove(id);
        saveAsync();
    }

    public void setRole(Clan clan, UUID id, Role role) {
        clan.setRole(id, role);
        saveAsync();
    }

    public void transferLeadership(Clan clan, UUID newLeader) {
        clan.transferLeadership(newLeader);
        saveAsync();
    }

    public void setTag(Clan clan, String tag) {
        clan.setTag(tag);
        saveAsync();
    }

    public void setTagColor(Clan clan, char color) {
        clan.setTagColor(Character.toLowerCase(color));
        saveAsync();
    }

    public void updateMemberName(Clan clan, UUID id, String playerName) {
        clan.setMemberName(id, playerName);
        saveAsync();
    }

    // ------------------------------------------------------------------ persistencia

    public void load() {
        clans.clear();
        byPlayer.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("clans");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(key);
            if (c == null) {
                continue;
            }
            String name = c.getString("name", key);
            String tag = c.getString("tag", name);
            String colorText = c.getString("tag-color", "b");
            char color = (colorText == null || colorText.isEmpty()) ? 'b' : colorText.charAt(0);
            if (!isValidColor(color)) {
                color = 'b';
            }
            Clan clan = new Clan(name, tag, Character.toLowerCase(color), c.getLong("created", System.currentTimeMillis()));
            ConfigurationSection members = c.getConfigurationSection("members");
            if (members != null) {
                for (String uid : members.getKeys(false)) {
                    UUID id;
                    try {
                        id = UUID.fromString(uid);
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    if (byPlayer.containsKey(id)) {
                        continue;
                    }
                    Role role;
                    try {
                        role = Role.valueOf(members.getString(uid + ".role", "MEMBER").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        role = Role.MEMBER;
                    }
                    clan.addMember(id, members.getString(uid + ".name", "?"), role);
                }
            }
            if (clan.size() == 0) {
                continue;
            }
            clan.ensureLeader();
            clans.put(name.toLowerCase(Locale.ROOT), clan);
            for (UUID id : clan.getRoles().keySet()) {
                byPlayer.put(id, clan);
            }
        }
        plugin.getLogger().info("Carregados " + clans.size() + " clas.");
    }

    private YamlConfiguration buildYaml() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String base = "clans." + c.getName();
            yml.set(base + ".name", c.getName());
            yml.set(base + ".tag", c.getTag());
            yml.set(base + ".tag-color", String.valueOf(c.getTagColor()));
            yml.set(base + ".created", c.getCreatedAt());
            for (Map.Entry<UUID, Role> e : c.getRoles().entrySet()) {
                String p = base + ".members." + e.getKey();
                yml.set(p + ".role", e.getValue().name());
                String n = c.getStoredName(e.getKey());
                yml.set(p + ".name", n == null ? "?" : n);
            }
        }
        return yml;
    }

    /** Salva em segundo plano (o YAML e montado na thread principal; so a escrita em disco e assincrona). */
    public void saveAsync() {
        final String data = buildYaml().saveToString();
        if (!plugin.isEnabled()) {
            write(data);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(data));
    }

    /** Salva imediatamente (usado ao desligar o plugin). */
    public void saveNow() {
        write(buildYaml().saveToString());
    }

    private void write(String data) {
        synchronized (ioLock) {
            try {
                File dir = file.getParentFile();
                if (dir != null && !dir.exists()) {
                    dir.mkdirs();
                }
                Path target = file.toPath();
                Path tmp = target.resolveSibling("clans.yml.tmp");
                Files.write(tmp, data.getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Nao foi possivel salvar clans.yml", ex);
            }
        }
    }
}
