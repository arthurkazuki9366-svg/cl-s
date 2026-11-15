package com.sharksmp.clans.command;

import com.sharksmp.clans.SharkClans;
import com.sharksmp.clans.manager.InviteManager;
import com.sharksmp.clans.model.Clan;
import com.sharksmp.clans.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Executor e tab-completer do comando /clan. */
public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList(
            "criar", "convidar", "aceitar", "recusar", "sair", "expulsar", "promover", "rebaixar",
            "lider", "deletar", "info", "listar", "chat", "ajuda");
    private static final List<String> ADMIN_SUBS = Arrays.asList("deletar", "expulsar", "tag", "lider", "recarregar");

    private final SharkClans plugin;

    public ClanCommand(SharkClans plugin) {
        this.plugin = plugin;
    }

    private void usage(CommandSender s, String usage) {
        plugin.getMsg().send(s, "usage", "usage", usage);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharkclans.use")) {
            plugin.getMsg().send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player) {
                plugin.getGui().openMain((Player) sender);
            } else {
                showHelp(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Subcomandos que funcionam tambem no console
        switch (sub) {
            case "ajuda":
            case "help":
                showHelp(sender);
                return true;
            case "listar":
            case "list": {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException ex) {
                        page = 1;
                    }
                }
                plugin.getActions().showList(sender, page);
                return true;
            }
            case "admin":
                return admin(sender, args);
            default:
                break;
        }

        if (!(sender instanceof Player)) {
            if (sub.equals("info") && args.length > 1) {
                Clan clan = plugin.getClanManager().getClan(args[1]);
                if (clan == null) {
                    plugin.getMsg().send(sender, "clan-not-found", "clan", args[1]);
                } else {
                    plugin.getActions().showInfo(sender, clan);
                }
                return true;
            }
            plugin.getMsg().send(sender, "only-players");
            return true;
        }
        final Player p = (Player) sender;

        switch (sub) {
            case "criar":
            case "create":
                if (args.length < 2) {
                    usage(p, "/clan criar <nome>");
                } else {
                    plugin.getActions().create(p, args[1]);
                }
                return true;

            case "convidar":
            case "invite":
                if (args.length < 2) {
                    usage(p, "/clan convidar <jogador>");
                } else {
                    plugin.getActions().invite(p, args[1]);
                }
                return true;

            case "aceitar":
            case "accept":
                plugin.getActions().accept(p, args.length > 1 ? args[1] : null);
                return true;

            case "recusar":
            case "decline":
                plugin.getActions().decline(p, args.length > 1 ? args[1] : null);
                return true;

            case "sair":
            case "leave":
                plugin.getActions().leave(p);
                return true;

            case "expulsar":
            case "kick":
                if (args.length < 2) {
                    usage(p, "/clan expulsar <jogador>");
                } else {
                    plugin.getActions().kick(p, args[1]);
                }
                return true;

            case "promover":
            case "promote":
                if (args.length < 2) {
                    usage(p, "/clan promover <jogador>");
                } else {
                    plugin.getActions().promote(p, args[1]);
                }
                return true;

            case "rebaixar":
            case "demote":
                if (args.length < 2) {
                    usage(p, "/clan rebaixar <jogador>");
                } else {
                    plugin.getActions().demote(p, args[1]);
                }
                return true;

            case "lider":
            case "leader":
                if (args.length < 2) {
                    usage(p, "/clan lider <jogador>");
                } else {
                    plugin.getActions().transferLeader(p, args[1]);
                }
                return true;

            case "deletar":
            case "delete":
                plugin.getActions().delete(p);
                return true;

            case "info": {
                Clan clan;
                if (args.length > 1) {
                    clan = plugin.getClanManager().getClan(args[1]);
                    if (clan == null) {
                        plugin.getMsg().send(p, "clan-not-found", "clan", args[1]);
                        return true;
                    }
                } else {
                    clan = plugin.getClanManager().getClanOf(p.getUniqueId());
                    if (clan == null) {
                        plugin.getMsg().send(p, "no-clan");
                        return true;
                    }
                }
                plugin.getActions().showInfo(p, clan);
                return true;
            }

            case "chat": {
                Clan clan = plugin.getClanManager().getClanOf(p.getUniqueId());
                if (clan == null) {
                    plugin.getMsg().send(p, "no-clan");
                    return true;
                }
                if (args.length > 1) {
                    // /clan chat <mensagem> : envia uma unica mensagem ao cla
                    String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    plugin.getActions().sendClanChat(p, clan, Component.text(text));
                } else {
                    plugin.getActions().toggleChat(p);
                }
                return true;
            }

            default:
                plugin.getMsg().send(p, "unknown-subcommand");
                return true;
        }
    }

    private void showHelp(CommandSender s) {
        for (String line : plugin.getMsg().rawList("help")) {
            s.sendMessage(Text.color(line));
        }
        if (s.hasPermission("sharkclans.admin")) {
            for (String line : plugin.getMsg().rawList("help-admin")) {
                s.sendMessage(Text.color(line));
            }
        }
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sharkclans.admin")) {
            plugin.getMsg().send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            usage(sender, "/clan admin <deletar|expulsar|tag|lider|recarregar>");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "deletar":
            case "delete":
                if (args.length < 3) {
                    usage(sender, "/clan admin deletar <clã>");
                } else {
                    plugin.getActions().adminDelete(sender, args[2]);
                }
                return true;
            case "expulsar":
            case "kick":
                if (args.length < 3) {
                    usage(sender, "/clan admin expulsar <jogador>");
                } else {
                    plugin.getActions().adminKick(sender, args[2]);
                }
                return true;
            case "tag":
                if (args.length < 4) {
                    usage(sender, "/clan admin tag <clã> <tag>");
                } else {
                    plugin.getActions().adminSetTag(sender, args[2], args[3]);
                }
                return true;
            case "lider":
            case "leader":
                if (args.length < 4) {
                    usage(sender, "/clan admin lider <clã> <jogador>");
                } else {
                    plugin.getActions().adminSetLeader(sender, args[2], args[3]);
                }
                return true;
            case "recarregar":
            case "reload":
                plugin.reloadAll();
                plugin.getMsg().send(sender, "reloaded");
                return true;
            default:
                usage(sender, "/clan admin <deletar|expulsar|tag|lider|recarregar>");
                return true;
        }
    }

    // ------------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharkclans.use")) {
            return new ArrayList<>();
        }
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(SUBS);
            if (sender.hasPermission("sharkclans.admin")) {
                subs.add("admin");
            }
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Player p = sender instanceof Player ? (Player) sender : null;
        Clan mine = p == null ? null : plugin.getClanManager().getClanOf(p.getUniqueId());

        if (args.length == 2) {
            switch (sub) {
                case "convidar":
                case "invite": {
                    List<String> names = new ArrayList<>();
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        if ((p == null || !o.getUniqueId().equals(p.getUniqueId()))
                                && plugin.getClanManager().getClanOf(o.getUniqueId()) == null) {
                            names.add(o.getName());
                        }
                    }
                    return filter(names, args[1]);
                }
                case "expulsar":
                case "kick":
                case "promover":
                case "promote":
                case "rebaixar":
                case "demote":
                case "lider":
                case "leader": {
                    List<String> names = new ArrayList<>();
                    if (mine != null) {
                        for (UUID id : mine.getRoles().keySet()) {
                            if (p != null && id.equals(p.getUniqueId())) {
                                continue;
                            }
                            names.add(mine.getMemberName(id));
                        }
                    }
                    return filter(names, args[1]);
                }
                case "aceitar":
                case "accept":
                case "recusar":
                case "decline": {
                    List<String> names = new ArrayList<>();
                    if (p != null) {
                        for (InviteManager.Invite i : plugin.getInviteManager().get(p.getUniqueId())) {
                            names.add(i.clanName);
                        }
                    }
                    return filter(names, args[1]);
                }
                case "info":
                    return filter(clanNames(), args[1]);
                case "admin":
                    if (sender.hasPermission("sharkclans.admin")) {
                        return filter(new ArrayList<>(ADMIN_SUBS), args[1]);
                    }
                    return new ArrayList<>();
                default:
                    return new ArrayList<>();
            }
        }

        if (sub.equals("admin") && sender.hasPermission("sharkclans.admin")) {
            String asub = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 3) {
                switch (asub) {
                    case "deletar":
                    case "delete":
                    case "tag":
                    case "lider":
                    case "leader":
                        return filter(clanNames(), args[2]);
                    case "expulsar":
                    case "kick": {
                        List<String> names = new ArrayList<>();
                        for (Clan c : plugin.getClanManager().getClans()) {
                            for (UUID id : c.getRoles().keySet()) {
                                names.add(c.getMemberName(id));
                            }
                        }
                        return filter(names, args[2]);
                    }
                    default:
                        return new ArrayList<>();
                }
            }
            if (args.length == 4 && (asub.equals("lider") || asub.equals("leader"))) {
                Clan c = plugin.getClanManager().getClan(args[2]);
                List<String> names = new ArrayList<>();
                if (c != null) {
                    for (UUID id : c.getRoles().keySet()) {
                        names.add(c.getMemberName(id));
                    }
                }
                return filter(names, args[3]);
            }
        }
        return new ArrayList<>();
    }

    private List<String> clanNames() {
        List<String> names = new ArrayList<>();
        for (Clan c : plugin.getClanManager().getClans()) {
            names.add(c.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(o);
            }
        }
        return out;
    }
}
