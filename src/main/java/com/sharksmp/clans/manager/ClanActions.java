package com.sharksmp.clans.manager;

import com.sharksmp.clans.SharkClans;
import com.sharksmp.clans.model.Clan;
import com.sharksmp.clans.model.Role;
import com.sharksmp.clans.util.Msg;
import com.sharksmp.clans.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Todas as regras e verificacoes de seguranca do sistema de clas.
 * Usado tanto pelos comandos quanto pela GUI, para que as regras sejam sempre as mesmas.
 */
public final class ClanActions {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    private final SharkClans plugin;
    private final Set<UUID> clanChat = ConcurrentHashMap.newKeySet();

    public ClanActions(SharkClans plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    private InviteManager im() {
        return plugin.getInviteManager();
    }

    private Msg msg() {
        return plugin.getMsg();
    }

    // ------------------------------------------------------------------ permissoes

    public boolean canKick(Role actor, Role target, boolean self) {
        if (self || actor == null || target == null) {
            return false;
        }
        if (target == Role.LEADER) {
            return false;
        }
        if (actor == Role.LEADER) {
            return true;
        }
        return actor == Role.SUB_LEADER
                && target == Role.MEMBER
                && plugin.getConfig().getBoolean("clan.sub-leader-can-kick", true);
    }

    /** True se o ator tem alguma acao de gerenciamento disponivel sobre o alvo. */
    public boolean canManage(Role actor, Role target, boolean self) {
        if (self || actor == null || target == null) {
            return false;
        }
        return actor == Role.LEADER || canKick(actor, target, false);
    }

    // ------------------------------------------------------------------ criar / deletar

    public boolean create(Player p, String rawName) {
        if (!p.hasPermission("sharkclans.create")) {
            msg().send(p, "no-permission");
            return false;
        }
        if (cm().getClanOf(p.getUniqueId()) != null) {
            msg().send(p, "already-in-clan");
            return false;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (!cm().isValidName(name)) {
            msg().send(p, "invalid-name", "min", cm().nameMin(), "max", cm().nameMax());
            return false;
        }
        if (cm().isNameTaken(name)) {
            msg().send(p, "name-taken", "clan", name);
            return false;
        }
        String tag = cm().generateTag(name);
        if (tag == null) {
            msg().send(p, "tag-generate-failed");
            return false;
        }
        Clan clan = cm().createClan(name, tag, p.getUniqueId(), p.getName());
        im().clear(p.getUniqueId());
        msg().send(p, "clan-created", "clan", clan.getName(), "tag", clan.getTag());
        msg().broadcast("clan-created-broadcast", "player", p.getName(), "clan", clan.getName());
        return true;
    }

    public boolean delete(Player p) {
        Clan clan = cm().getClanOf(p.getUniqueId());
        if (clan == null) {
            msg().send(p, "no-clan");
            return false;
        }
        if (clan.getRole(p.getUniqueId()) != Role.LEADER) {
            msg().send(p, "only-leader");
            return false;
        }
        doDelete(clan);
        msg().send(p, "clan-deleted", "clan", clan.getName());
        return true;
    }

    private void doDelete(Clan clan) {
        for (UUID id : clan.getRoles().keySet()) {
            clanChat.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                msg().send(online, "clan-deleted-notify", "clan", clan.getName());
            }
        }
        im().clearClan(clan.getName());
        cm().deleteClan(clan);
    }

    // ------------------------------------------------------------------ convites

    public boolean invite(Player actor, String targetName) {
        Player target = targetName == null ? null : Bukkit.getPlayerExact(targetName);
        if (target == null) {
            msg().send(actor, "player-not-found", "player", targetName == null ? "" : targetName);
            return false;
        }
        return invite(actor, target);
    }

    public boolean invite(Player actor, Player target) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        if (clan.getRole(actor.getUniqueId()) == Role.MEMBER) {
            msg().send(actor, "only-leader-or-sub");
            return false;
        }
        if (target == null || !target.isOnline()) {
            msg().send(actor, "player-not-found", "player", target == null ? "" : target.getName());
            return false;
        }
        if (target.getUniqueId().equals(actor.getUniqueId())) {
            msg().send(actor, "cannot-invite-self");
            return false;
        }
        if (cm().getClanOf(target.getUniqueId()) != null) {
            msg().send(actor, "target-already-in-clan", "player", target.getName());
            return false;
        }
        if (clan.size() >= cm().maxMembers()) {
            msg().send(actor, "clan-full", "max", cm().maxMembers());
            return false;
        }
        if (!im().add(target.getUniqueId(), clan, actor)) {
            msg().send(actor, "already-invited", "player", target.getName());
            return false;
        }
        msg().send(actor, "invite-sent", "player", target.getName());
        sendInviteNotice(target, clan, actor);
        return true;
    }

    private void sendInviteNotice(Player target, Clan clan, Player inviter) {
        Msg m = msg();
        target.sendMessage(Text.color(m.prefix() + m.raw("invite-received",
                "player", inviter.getName(), "clan", clan.getName(), "seconds", im().expireSeconds())));
        Component accept = Text.color(m.raw("invite-accept-button"))
                .clickEvent(ClickEvent.runCommand("/clan aceitar " + clan.getName()))
                .hoverEvent(HoverEvent.showText(Text.color(m.raw("invite-accept-hover"))));
        Component decline = Text.color(m.raw("invite-decline-button"))
                .clickEvent(ClickEvent.runCommand("/clan recusar " + clan.getName()))
                .hoverEvent(HoverEvent.showText(Text.color(m.raw("invite-decline-hover"))));
        target.sendMessage(Text.color(m.prefix()).append(accept).append(Component.text(" ")).append(decline));
    }

    public boolean accept(Player p, String clanName) {
        if (cm().getClanOf(p.getUniqueId()) != null) {
            msg().send(p, "already-in-clan");
            return false;
        }
        List<InviteManager.Invite> list = im().get(p.getUniqueId());
        if (list.isEmpty()) {
            msg().send(p, "no-invites");
            return false;
        }
        InviteManager.Invite invite = null;
        if (clanName == null || clanName.isEmpty()) {
            invite = list.get(list.size() - 1);
        } else {
            for (InviteManager.Invite i : list) {
                if (i.clanName.equalsIgnoreCase(clanName)) {
                    invite = i;
                    break;
                }
            }
        }
        if (invite == null) {
            msg().send(p, "invite-not-found", "clan", clanName);
            return false;
        }
        Clan clan = cm().getClan(invite.clanName);
        if (clan == null) {
            im().remove(p.getUniqueId(), invite.clanName);
            msg().send(p, "invite-expired");
            return false;
        }
        if (clan.size() >= cm().maxMembers()) {
            im().remove(p.getUniqueId(), invite.clanName);
            msg().send(p, "clan-full", "max", cm().maxMembers());
            return false;
        }
        cm().addMember(clan, p.getUniqueId(), p.getName());
        im().clear(p.getUniqueId());
        msg().send(p, "invite-accepted", "clan", clan.getName());
        notifyClan(clan, p.getUniqueId(), "member-joined", "player", p.getName());
        return true;
    }

    public boolean decline(Player p, String clanName) {
        List<InviteManager.Invite> list = im().get(p.getUniqueId());
        if (list.isEmpty()) {
            msg().send(p, "no-invites");
            return false;
        }
        InviteManager.Invite invite = null;
        if (clanName == null || clanName.isEmpty()) {
            invite = list.get(list.size() - 1);
        } else {
            for (InviteManager.Invite i : list) {
                if (i.clanName.equalsIgnoreCase(clanName)) {
                    invite = i;
                    break;
                }
            }
        }
        if (invite == null) {
            msg().send(p, "invite-not-found", "clan", clanName);
            return false;
        }
        im().remove(p.getUniqueId(), invite.clanName);
        msg().send(p, "invite-declined", "clan", invite.clanName);
        Player inviter = Bukkit.getPlayer(invite.inviter);
        if (inviter != null) {
            msg().send(inviter, "invite-declined-notify", "player", p.getName());
        }
        return true;
    }

    // ------------------------------------------------------------------ sair / expulsar

    public boolean leave(Player p) {
        Clan clan = cm().getClanOf(p.getUniqueId());
        if (clan == null) {
            msg().send(p, "no-clan");
            return false;
        }
        if (clan.getRole(p.getUniqueId()) == Role.LEADER) {
            msg().send(p, "leader-cannot-leave");
            return false;
        }
        cm().removeMember(clan, p.getUniqueId());
        clanChat.remove(p.getUniqueId());
        msg().send(p, "left-clan", "clan", clan.getName());
        notifyClan(clan, null, "member-left", "player", p.getName());
        return true;
    }

    public boolean kick(Player actor, String targetName) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        UUID target = clan.findMember(targetName);
        if (target == null) {
            msg().send(actor, "target-not-member", "player", targetName);
            return false;
        }
        return kick(actor, target);
    }

    public boolean kick(Player actor, UUID target) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        Role mine = clan.getRole(actor.getUniqueId());
        if (mine == Role.MEMBER) {
            msg().send(actor, "only-leader-or-sub");
            return false;
        }
        Role theirs = clan.getRole(target);
        if (theirs == null) {
            msg().send(actor, "target-not-member", "player", target.toString());
            return false;
        }
        String targetName = clan.getMemberName(target);
        if (target.equals(actor.getUniqueId())) {
            msg().send(actor, "cannot-kick-self");
            return false;
        }
        if (theirs == Role.LEADER) {
            msg().send(actor, "cannot-kick-leader");
            return false;
        }
        if (!canKick(mine, theirs, false)) {
            msg().send(actor, "cannot-kick-higher");
            return false;
        }
        removeAndNotify(clan, target, targetName);
        msg().send(actor, "member-kicked", "player", targetName);
        return true;
    }

    private void removeAndNotify(Clan clan, UUID target, String targetName) {
        cm().removeMember(clan, target);
        clanChat.remove(target);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            msg().send(online, "you-were-kicked", "clan", clan.getName());
        }
        notifyClan(clan, null, "member-kicked-notify", "player", targetName);
    }

    // ------------------------------------------------------------------ cargos

    public boolean promote(Player actor, String targetName) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        UUID target = clan.findMember(targetName);
        if (target == null) {
            msg().send(actor, "target-not-member", "player", targetName);
            return false;
        }
        return promote(actor, target);
    }

    public boolean promote(Player actor, UUID target) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        if (clan.getRole(actor.getUniqueId()) != Role.LEADER) {
            msg().send(actor, "only-leader");
            return false;
        }
        Role theirs = clan.getRole(target);
        if (theirs == null) {
            msg().send(actor, "target-not-member", "player", target.toString());
            return false;
        }
        String name = clan.getMemberName(target);
        if (theirs == Role.LEADER) {
            msg().send(actor, "cannot-promote-leader");
            return false;
        }
        if (theirs == Role.SUB_LEADER) {
            msg().send(actor, "already-sub", "player", name);
            return false;
        }
        int max = cm().maxSubLeaders();
        if (max >= 0 && clan.countRole(Role.SUB_LEADER) >= max) {
            msg().send(actor, "max-sub-leaders", "max", max);
            return false;
        }
        cm().setRole(clan, target, Role.SUB_LEADER);
        msg().send(actor, "member-promoted", "player", name);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            msg().send(online, "you-were-promoted", "clan", clan.getName());
        }
        notifyClan(clan, target, "member-promoted-notify", "player", name);
        return true;
    }

    public boolean demote(Player actor, String targetName) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        UUID target = clan.findMember(targetName);
        if (target == null) {
            msg().send(actor, "target-not-member", "player", targetName);
            return false;
        }
        return demote(actor, target);
    }

    public boolean demote(Player actor, UUID target) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        if (clan.getRole(actor.getUniqueId()) != Role.LEADER) {
            msg().send(actor, "only-leader");
            return false;
        }
        Role theirs = clan.getRole(target);
        if (theirs == null) {
            msg().send(actor, "target-not-member", "player", target.toString());
            return false;
        }
        String name = clan.getMemberName(target);
        if (theirs != Role.SUB_LEADER) {
            msg().send(actor, "not-sub", "player", name);
            return false;
        }
        cm().setRole(clan, target, Role.MEMBER);
        msg().send(actor, "member-demoted", "player", name);
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            msg().send(online, "you-were-demoted", "clan", clan.getName());
        }
        notifyClan(clan, target, "member-demoted-notify", "player", name);
        return true;
    }

    public boolean transferLeader(Player actor, String targetName) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        UUID target = clan.findMember(targetName);
        if (target == null) {
            msg().send(actor, "target-not-member", "player", targetName);
            return false;
        }
        return transferLeader(actor, target);
    }

    public boolean transferLeader(Player actor, UUID target) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        if (clan.getRole(actor.getUniqueId()) != Role.LEADER) {
            msg().send(actor, "only-leader");
            return false;
        }
        if (!clan.isMember(target)) {
            msg().send(actor, "target-not-member", "player", target.toString());
            return false;
        }
        if (target.equals(actor.getUniqueId())) {
            msg().send(actor, "already-leader");
            return false;
        }
        String name = clan.getMemberName(target);
        cm().transferLeadership(clan, target);
        msg().send(actor, "leadership-transferred", "player", name);
        notifyClan(clan, null, "leadership-notify", "player", name, "old", actor.getName());
        return true;
    }

    // ------------------------------------------------------------------ tag

    public boolean setTag(Player actor, String rawTag) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        if (clan.getRole(actor.getUniqueId()) != Role.LEADER) {
            msg().send(actor, "only-leader");
            return false;
        }
        return applyTag(actor, clan, rawTag);
    }

    private boolean applyTag(CommandSender who, Clan clan, String rawTag) {
        String tag = rawTag == null ? "" : rawTag.trim();
        if (!cm().isValidTag(tag)) {
            msg().send(who, "invalid-tag", "min", cm().tagMin(), "max", cm().tagMax());
            return false;
        }
        if (cm().isTagTaken(tag, clan)) {
            msg().send(who, "tag-taken", "tag", tag);
            return false;
        }
        cm().setTag(clan, tag);
        msg().send(who, "tag-changed", "tag", tag);
        return true;
    }

    public boolean setTagColor(Player actor, char color) {
        Clan clan = cm().getClanOf(actor.getUniqueId());
        if (clan == null) {
            msg().send(actor, "no-clan");
            return false;
        }
        if (clan.getRole(actor.getUniqueId()) != Role.LEADER) {
            msg().send(actor, "only-leader");
            return false;
        }
        if (!ClanManager.isValidColor(color)) {
            return false;
        }
        cm().setTagColor(clan, color);
        msg().send(actor, "tag-color-changed");
        return true;
    }

    // ------------------------------------------------------------------ chat do cla

    public boolean isClanChat(UUID id) {
        return clanChat.contains(id);
    }

    public void removeChatState(UUID id) {
        clanChat.remove(id);
    }

    public boolean toggleChat(Player p) {
        Clan clan = cm().getClanOf(p.getUniqueId());
        if (clan == null) {
            msg().send(p, "no-clan");
            return false;
        }
        if (clanChat.remove(p.getUniqueId())) {
            msg().send(p, "chat-disabled");
        } else {
            clanChat.add(p.getUniqueId());
            msg().send(p, "chat-enabled");
        }
        return true;
    }

    public void sendClanChat(Player from, Clan clan, Component message) {
        Role role = clan.getRole(from.getUniqueId());
        String prefix = msg().raw("clan-chat-prefix",
                "player", from.getName(),
                "clan", clan.getName(),
                "tag", clan.getTag(),
                "role", role == null ? "" : msg().roleName(role),
                "rolecolor", role == null ? "&7" : Msg.roleColor(role));
        Component full = Text.color(prefix).append(message);
        for (UUID id : clan.getRoles().keySet()) {
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                online.sendMessage(full);
            }
        }
        if (plugin.getConfig().getBoolean("chat.log-clan-chat", true)) {
            Bukkit.getConsoleSender().sendMessage(full);
        }
    }

    // ------------------------------------------------------------------ informacoes

    public void showInfo(CommandSender to, Clan clan) {
        Msg m = msg();
        UUID leader = clan.getLeader();
        m.sendRaw(to, "info-header", "clan", clan.getName());
        m.sendRaw(to, "info-tag", "tag", cm().tagPrefix(clan));
        m.sendRaw(to, "info-leader", "leader", leader == null ? "?" : clan.getMemberName(leader));
        m.sendRaw(to, "info-size", "size", clan.size(), "max", cm().maxMembers());
        m.sendRaw(to, "info-created", "date", DATE.format(java.time.Instant.ofEpochMilli(clan.getCreatedAt())));
        m.sendRaw(to, "info-members-title");
        for (UUID id : clan.sortedMembers()) {
            Role role = clan.getRole(id);
            boolean online = Bukkit.getPlayer(id) != null;
            m.sendRaw(to, "info-member",
                    "color", Msg.roleColor(role),
                    "player", clan.getMemberName(id),
                    "role", m.roleName(role),
                    "status", online ? m.raw("status-online") : m.raw("status-offline"));
        }
    }

    public void showList(CommandSender to, int page) {
        List<Clan> all = cm().getClans();
        if (all.isEmpty()) {
            msg().send(to, "list-empty");
            return;
        }
        int perPage = 10;
        int pages = (all.size() + perPage - 1) / perPage;
        int p = Math.max(1, Math.min(page, pages));
        msg().sendRaw(to, "list-header", "page", p, "pages", pages, "total", all.size());
        for (int i = (p - 1) * perPage; i < Math.min(all.size(), p * perPage); i++) {
            Clan c = all.get(i);
            UUID leader = c.getLeader();
            msg().sendRaw(to, "list-line",
                    "clan", c.getName(),
                    "tag", cm().tagPrefix(c),
                    "leader", leader == null ? "?" : c.getMemberName(leader),
                    "size", c.size(),
                    "max", cm().maxMembers());
        }
        if (p < pages) {
            msg().sendRaw(to, "list-next", "page", p + 1);
        }
    }

    // ------------------------------------------------------------------ admin

    public boolean adminDelete(CommandSender s, String clanName) {
        Clan clan = cm().getClan(clanName);
        if (clan == null) {
            msg().send(s, "clan-not-found", "clan", clanName);
            return false;
        }
        doDelete(clan);
        msg().send(s, "admin-deleted", "clan", clan.getName());
        return true;
    }

    public boolean adminKick(CommandSender s, String playerName) {
        for (Clan clan : cm().getClans()) {
            UUID id = clan.findMember(playerName);
            if (id == null) {
                continue;
            }
            if (clan.getRole(id) == Role.LEADER) {
                msg().send(s, "cannot-kick-leader");
                return false;
            }
            String name = clan.getMemberName(id);
            removeAndNotify(clan, id, name);
            msg().send(s, "admin-kicked", "player", name, "clan", clan.getName());
            return true;
        }
        msg().send(s, "admin-player-no-clan", "player", playerName);
        return false;
    }

    public boolean adminSetTag(CommandSender s, String clanName, String tag) {
        Clan clan = cm().getClan(clanName);
        if (clan == null) {
            msg().send(s, "clan-not-found", "clan", clanName);
            return false;
        }
        return applyTag(s, clan, tag);
    }

    public boolean adminSetLeader(CommandSender s, String clanName, String playerName) {
        Clan clan = cm().getClan(clanName);
        if (clan == null) {
            msg().send(s, "clan-not-found", "clan", clanName);
            return false;
        }
        UUID id = clan.findMember(playerName);
        if (id == null) {
            msg().send(s, "target-not-member", "player", playerName);
            return false;
        }
        if (id.equals(clan.getLeader())) {
            msg().send(s, "already-leader");
            return false;
        }
        String name = clan.getMemberName(id);
        cm().transferLeadership(clan, id);
        msg().send(s, "leadership-transferred", "player", name);
        notifyClan(clan, null, "leadership-notify", "player", name, "old", "Admin");
        return true;
    }

    // ------------------------------------------------------------------ util

    /** Envia uma mensagem (com prefixo) a todos os membros online, exceto o jogador "except". */
    public void notifyClan(Clan clan, UUID except, String key, Object... kv) {
        for (Map.Entry<UUID, Role> e : clan.getRoles().entrySet()) {
            if (e.getKey().equals(except)) {
                continue;
            }
            Player online = Bukkit.getPlayer(e.getKey());
            if (online != null) {
                msg().send(online, key, kv);
            }
        }
    }
}
