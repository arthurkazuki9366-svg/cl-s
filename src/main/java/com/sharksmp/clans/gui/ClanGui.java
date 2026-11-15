package com.sharksmp.clans.gui;

import com.sharksmp.clans.SharkClans;
import com.sharksmp.clans.manager.ClanActions;
import com.sharksmp.clans.manager.ClanManager;
import com.sharksmp.clans.manager.InviteManager;
import com.sharksmp.clans.model.Clan;
import com.sharksmp.clans.model.Role;
import com.sharksmp.clans.util.ItemBuilder;
import com.sharksmp.clans.util.Msg;
import com.sharksmp.clans.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Painel GUI do sistema de clas. Cada botao executa uma acao real de ClanActions. */
public final class ClanGui implements Listener {

    @FunctionalInterface
    public interface ClickAction {
        void onClick(Player player, ClickType type);
    }

    /** Holder usado para identificar os inventarios deste plugin e guardar as acoes dos botoes. */
    public static final class Menu implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, ClickAction> actions = new HashMap<>();

        Menu(String title, int rows) {
            this.inventory = Bukkit.createInventory(this, rows * 9, Text.color(title));
        }

        void set(int slot, ItemStack item) {
            inventory.setItem(slot, item);
        }

        void set(int slot, ItemStack item, ClickAction action) {
            inventory.setItem(slot, item);
            if (action != null) {
                actions.put(slot, action);
            }
        }

        ClickAction action(int slot) {
            return actions.get(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private enum PromptType {
        CREATE,
        TAG
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault());

    /** Slots da area de conteudo em menus de 6 linhas (28 por pagina). */
    private static final int[] GRID = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** Cores de tag: codigo, material do icone, nome. */
    private static final Object[][] COLORS = {
            {'0', Material.BLACK_WOOL, "Preto"},
            {'1', Material.BLUE_WOOL, "Azul escuro"},
            {'2', Material.GREEN_WOOL, "Verde escuro"},
            {'3', Material.CYAN_WOOL, "Ciano escuro"},
            {'4', Material.RED_WOOL, "Vermelho escuro"},
            {'5', Material.PURPLE_WOOL, "Roxo"},
            {'6', Material.ORANGE_WOOL, "Dourado"},
            {'7', Material.LIGHT_GRAY_WOOL, "Cinza"},
            {'8', Material.GRAY_WOOL, "Cinza escuro"},
            {'9', Material.BLUE_CONCRETE, "Azul"},
            {'a', Material.LIME_WOOL, "Verde"},
            {'b', Material.LIGHT_BLUE_WOOL, "Ciano"},
            {'c', Material.RED_CONCRETE, "Vermelho"},
            {'d', Material.MAGENTA_WOOL, "Rosa"},
            {'e', Material.YELLOW_WOOL, "Amarelo"},
            {'f', Material.WHITE_WOOL, "Branco"}
    };

    private final SharkClans plugin;
    private final Map<UUID, PromptType> prompts = new ConcurrentHashMap<>();

    public ClanGui(SharkClans plugin) {
        this.plugin = plugin;
    }

    private ClanManager cm() {
        return plugin.getClanManager();
    }

    private ClanActions actions() {
        return plugin.getActions();
    }

    private Msg msg() {
        return plugin.getMsg();
    }

    // ================================================================== eventos

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }
        final Player player = (Player) e.getWhoClicked();
        Menu menu = (Menu) e.getInventory().getHolder();
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getInventory()) {
            return;
        }
        final ClickAction action = menu.action(e.getSlot());
        if (action == null) {
            return;
        }
        final ClickType type = e.getClick();
        // Executa no proximo tick para poder abrir/fechar inventarios com seguranca.
        Bukkit.getScheduler().runTask(plugin, () -> action.onClick(player, type));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) {
            e.setCancelled(true);
        }
    }

    // ================================================================== prompts (digitar no chat)

    public boolean hasPrompt(UUID id) {
        return prompts.containsKey(id);
    }

    public void cancelPrompt(UUID id) {
        prompts.remove(id);
    }

    private void startPrompt(Player p, PromptType type) {
        if (type == PromptType.TAG) {
            Clan clan = cm().getClanOf(p.getUniqueId());
            if (clan == null || clan.getRole(p.getUniqueId()) != Role.LEADER) {
                msg().send(p, "only-leader");
                return;
            }
        } else if (cm().getClanOf(p.getUniqueId()) != null) {
            msg().send(p, "already-in-clan");
            return;
        }
        p.closeInventory();
        prompts.put(p.getUniqueId(), type);
        if (type == PromptType.CREATE) {
            msg().send(p, "prompt-create", "min", cm().nameMin(), "max", cm().nameMax());
        } else {
            msg().send(p, "prompt-tag", "min", cm().tagMin(), "max", cm().tagMax());
        }
    }

    /** Chamado na thread principal com o texto digitado pelo jogador. */
    public void handlePrompt(Player p, String text) {
        PromptType type = prompts.remove(p.getUniqueId());
        if (type == null) {
            return;
        }
        String input = text == null ? "" : text.trim();
        if (input.equalsIgnoreCase("cancelar") || input.equalsIgnoreCase("cancel")) {
            msg().send(p, "prompt-cancelled");
            openMain(p);
            return;
        }
        if (type == PromptType.CREATE) {
            actions().create(p, input);
            openMain(p);
        } else {
            actions().setTag(p, input);
            openTag(p);
        }
    }

    // ================================================================== helpers de layout

    private static ItemStack pane(Material m) {
        return new ItemBuilder(m).name(" ").build();
    }

    /** Moldura ciano com fundo preto. */
    private static void decorate(Menu menu, int rows) {
        ItemStack edge = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack inner = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < rows * 9; i++) {
            int row = i / 9;
            int col = i % 9;
            boolean border = row == 0 || row == rows - 1 || col == 0 || col == 8;
            menu.set(i, border ? edge : inner);
        }
    }

    private static int pages(int total) {
        return Math.max(1, (total + GRID.length - 1) / GRID.length);
    }

    private static ItemStack backItem() {
        return new ItemBuilder(Material.BARRIER).name("&c&l« Voltar").lore("&7Clique para voltar.").build();
    }

    private void addNav(Menu m, int page, int pages, ClickAction prev, ClickAction next, ClickAction back) {
        if (page > 0) {
            m.set(45, new ItemBuilder(Material.ARROW).name("&e« Página anterior").build(), prev);
        }
        if (page < pages - 1) {
            m.set(53, new ItemBuilder(Material.ARROW).name("&ePróxima página »").build(), next);
        }
        m.set(48, new ItemBuilder(Material.PAPER).name("&7Página &f" + (page + 1) + "&7/&f" + pages).build());
        m.set(49, backItem(), back);
    }

    private void open(Player p, Menu m) {
        p.openInventory(m.getInventory());
    }

    private String tagPreview(Clan clan) {
        return cm().tagPrefix(clan).trim();
    }

    // ================================================================== menu principal

    public void openMain(Player p) {
        Clan clan = cm().getClanOf(p.getUniqueId());
        if (clan == null) {
            openNoClan(p);
        } else {
            openClanPanel(p, clan);
        }
    }

    private void openNoClan(Player p) {
        Menu m = new Menu("&8Clãs &8» &bPainel", 4);
        decorate(m, 4);
        int invites = plugin.getInviteManager().get(p.getUniqueId()).size();

        m.set(4, new ItemBuilder(Material.BEACON).name("&b&lSharkSMP Clãs")
                .lore("&7Você ainda não faz parte de um clã.", "&7Crie o seu ou aceite um convite!").build());
        m.set(11, new ItemBuilder(Material.EMERALD).name("&a&lCriar clã")
                .lore("&7Clique e digite o nome do", "&7seu clã no chat.").build(),
                (pl, t) -> startPrompt(pl, PromptType.CREATE));
        m.set(13, new ItemBuilder(Material.PAPER).name("&e&lConvites recebidos")
                .lore("&7Pendentes: &f" + invites, "", "&eClique para ver.").build(),
                (pl, t) -> openInvites(pl));
        m.set(15, new ItemBuilder(Material.COMPASS).name("&b&lLista de clãs")
                .lore("&7Veja todos os clãs do servidor.").build(),
                (pl, t) -> openClanList(pl, 0));
        m.set(31, new ItemBuilder(Material.BARRIER).name("&c&lFechar").build(), (pl, t) -> pl.closeInventory());
        open(p, m);
    }

    private ItemStack locked(String name, String reason) {
        return new ItemBuilder(Material.GRAY_DYE).name("&8&l" + name).lore("&c" + reason).build();
    }

    private void openClanPanel(Player p, Clan clan) {
        final Role role = clan.getRole(p.getUniqueId());
        boolean leader = role == Role.LEADER;
        boolean canInvite = role != Role.MEMBER;
        int invites = plugin.getInviteManager().get(p.getUniqueId()).size();

        Menu m = new Menu("&8Clã &8» &b" + clan.getName(), 5);
        decorate(m, 5);

        UUID leaderId = clan.getLeader();
        m.set(4, new ItemBuilder(Material.BEACON).name("&b&l" + clan.getName()).lore(
                "&7Tag: " + tagPreview(clan),
                "&7Líder: &f" + (leaderId == null ? "?" : clan.getMemberName(leaderId)),
                "&7Membros: &f" + clan.size() + "&7/&f" + cm().maxMembers(),
                "&7Criado em: &f" + DATE.format(Instant.ofEpochMilli(clan.getCreatedAt())),
                "&7Seu cargo: " + Msg.roleColor(role) + msg().roleName(role)).build());

        m.set(10, new ItemBuilder(Material.PLAYER_HEAD).owner(p).name("&a&lMembros")
                .lore("&7Veja todos os integrantes", "&7e seus cargos.", "", "&eClique para abrir.").build(),
                (pl, t) -> openMembers(pl, 0, false));

        if (canInvite) {
            m.set(11, new ItemBuilder(Material.EMERALD).name("&2&lConvidar jogador")
                    .lore("&7Convide jogadores online", "&7que não têm clã.", "", "&eClique para escolher.").build(),
                    (pl, t) -> openInvitePlayers(pl, 0));
        } else {
            m.set(11, locked("Convidar jogador", "Apenas líder e sub-líderes."),
                    (pl, t) -> msg().send(pl, "only-leader-or-sub"));
        }

        m.set(12, new ItemBuilder(Material.PAPER).name("&e&lConvites recebidos")
                .lore("&7Pendentes: &f" + invites, "", "&eClique para ver.").build(),
                (pl, t) -> openInvites(pl));

        if (leader) {
            m.set(13, new ItemBuilder(Material.GOLDEN_HELMET).name("&6&lGerenciar cargos")
                    .lore("&7Promova, rebaixe, expulse", "&7ou transfira a liderança.", "", "&eClique para abrir.").build(),
                    (pl, t) -> openMembers(pl, 0, true));
            m.set(14, new ItemBuilder(Material.NAME_TAG).name("&d&lConfigurar tag")
                    .lore("&7Tag atual: " + tagPreview(clan), "", "&eClique para editar.").build(),
                    (pl, t) -> openTag(pl));
        } else {
            m.set(13, locked("Gerenciar cargos", "Apenas o líder."), (pl, t) -> msg().send(pl, "only-leader"));
            m.set(14, locked("Configurar tag", "Apenas o líder."), (pl, t) -> msg().send(pl, "only-leader"));
        }

        boolean chat = actions().isClanChat(p.getUniqueId());
        m.set(15, new ItemBuilder(chat ? Material.WRITABLE_BOOK : Material.BOOK)
                .name("&9&lChat do clã: " + (chat ? "&aATIVADO" : "&cDESATIVADO"))
                .lore("&7Quando ativado, tudo que você", "&7digitar vai só para o seu clã.", "", "&eClique para alternar.").build(),
                (pl, t) -> {
                    actions().toggleChat(pl);
                    openMain(pl);
                });

        m.set(16, new ItemBuilder(Material.COMPASS).name("&b&lLista de clãs")
                .lore("&7Veja todos os clãs do servidor.").build(),
                (pl, t) -> openClanList(pl, 0));

        // Linha inferior
        if (leader) {
            m.set(38, new ItemBuilder(Material.OAK_DOOR).name("&c&lSair do clã")
                    .lore("&7O líder não pode sair.", "&7Transfira a liderança ou exclua o clã.").build(),
                    (pl, t) -> msg().send(pl, "leader-cannot-leave"));
        } else {
            m.set(38, new ItemBuilder(Material.OAK_DOOR).name("&c&lSair do clã")
                    .lore("&7Deixe o clã &f" + clan.getName() + "&7.", "", "&eClique para sair.").build(),
                    (pl, t) -> openConfirm(pl, "&8Sair do clã",
                            Arrays.asList("&7Tem certeza que deseja", "&7sair do clã &f" + clan.getName() + "&7?"),
                            (p2, t2) -> {
                                actions().leave(p2);
                                p2.closeInventory();
                            },
                            (p2, t2) -> openMain(p2)));
        }
        m.set(40, new ItemBuilder(Material.BARRIER).name("&c&lFechar").build(), (pl, t) -> pl.closeInventory());
        if (leader) {
            m.set(42, new ItemBuilder(Material.TNT).name("&4&lExcluir clã")
                    .lore("&7Exclui o clã permanentemente", "&7e remove todos os membros.", "", "&cClique para excluir.").build(),
                    (pl, t) -> openConfirm(pl, "&4Excluir clã",
                            Arrays.asList("&cEsta ação é permanente!", "&7Excluir o clã &f" + clan.getName() + "&7?"),
                            (p2, t2) -> {
                                actions().delete(p2);
                                p2.closeInventory();
                            },
                            (p2, t2) -> openMain(p2)));
        }
        open(p, m);
    }

    // ================================================================== confirmacao

    private void openConfirm(Player p, String title, List<String> description, ClickAction onConfirm, ClickAction onCancel) {
        Menu m = new Menu(title, 3);
        decorate(m, 3);
        m.set(13, new ItemBuilder(Material.PAPER).name("&e&lConfirmação").lore(description).build());
        m.set(11, new ItemBuilder(Material.LIME_CONCRETE).name("&a&lConfirmar").lore("&7Clique para confirmar.").build(), onConfirm);
        m.set(15, new ItemBuilder(Material.RED_CONCRETE).name("&c&lCancelar").lore("&7Clique para cancelar.").build(), onCancel);
        open(p, m);
    }

    // ================================================================== membros / cargos

    public void openMembers(Player p, int page, boolean rolesMode) {
        Clan clan = cm().getClanOf(p.getUniqueId());
        if (clan == null) {
            msg().send(p, "no-clan");
            return;
        }
        final Role mine = clan.getRole(p.getUniqueId());
        if (rolesMode && mine != Role.LEADER) {
            msg().send(p, "only-leader");
            return;
        }
        List<UUID> list = clan.sortedMembers();
        if (rolesMode) {
            list.remove(p.getUniqueId());
        }
        int pages = pages(list.size());
        final int pg = Math.max(0, Math.min(page, pages - 1));

        Menu m = new Menu((rolesMode ? "&8Cargos" : "&8Membros") + " &8» &b" + clan.getName(), 6);
        decorate(m, 6);
        m.set(4, new ItemBuilder(Material.BEACON).name("&b&l" + clan.getName())
                .lore("&7Membros: &f" + clan.size() + "&7/&f" + cm().maxMembers(),
                        "&7Sub-líderes: &f" + clan.countRole(Role.SUB_LEADER)).build());

        int start = pg * GRID.length;
        for (int i = 0; i < GRID.length && start + i < list.size(); i++) {
            final UUID id = list.get(start + i);
            Role role = clan.getRole(id);
            if (role == null) {
                continue;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            boolean online = op.isOnline();
            boolean manage = actions().canManage(mine, role, id.equals(p.getUniqueId()));
            List<String> lore = new ArrayList<>();
            lore.add("&7Cargo: " + Msg.roleColor(role) + msg().roleName(role));
            lore.add("&7Status: " + (online ? "&aOnline" : "&cOffline"));
            if (manage) {
                lore.add("");
                lore.add("&eClique para gerenciar.");
            }
            ItemStack head = new ItemBuilder(Material.PLAYER_HEAD).owner(op)
                    .name(Msg.roleColor(role) + "&l" + clan.getMemberName(id)).lore(lore).build();
            ClickAction manageAction = manage ? ((pl, t) -> openMemberAction(pl, id, rolesMode)) : null;
            m.set(GRID[i], head, manageAction);
        }
        addNav(m, pg, pages,
                (pl, t) -> openMembers(pl, pg - 1, rolesMode),
                (pl, t) -> openMembers(pl, pg + 1, rolesMode),
                (pl, t) -> openMain(pl));
        open(p, m);
    }

    private void openMemberAction(Player p, UUID target, boolean rolesMode) {
        Clan clan = cm().getClanOf(p.getUniqueId());
        if (clan == null) {
            msg().send(p, "no-clan");
            return;
        }
        final Role mine = clan.getRole(p.getUniqueId());
        final Role theirs = clan.getRole(target);
        if (theirs == null) {
            msg().send(p, "target-not-member", "player", target.toString());
            openMembers(p, 0, rolesMode);
            return;
        }
        final boolean self = target.equals(p.getUniqueId());
        final boolean leader = mine == Role.LEADER;
        final String name = clan.getMemberName(target);

        Menu m = new Menu("&8Gerenciar &8» &b" + name, 3);
        decorate(m, 3);
        m.set(4, new ItemBuilder(Material.PLAYER_HEAD).owner(Bukkit.getOfflinePlayer(target))
                .name(Msg.roleColor(theirs) + "&l" + name)
                .lore("&7Cargo: " + Msg.roleColor(theirs) + msg().roleName(theirs)).build());

        if (leader && !self && theirs == Role.MEMBER) {
            m.set(10, new ItemBuilder(Material.LIME_DYE).name("&a&lPromover a sub-líder")
                    .lore("&7Sub-líderes podem convidar", "&7e expulsar membros.").build(),
                    (pl, t) -> {
                        actions().promote(pl, target);
                        openMemberAction(pl, target, rolesMode);
                    });
        }
        if (leader && !self && theirs == Role.SUB_LEADER) {
            m.set(12, new ItemBuilder(Material.ORANGE_DYE).name("&6&lRebaixar a membro")
                    .lore("&7Remove o cargo de sub-líder.").build(),
                    (pl, t) -> {
                        actions().demote(pl, target);
                        openMemberAction(pl, target, rolesMode);
                    });
        }
        if (leader && !self) {
            m.set(14, new ItemBuilder(Material.NETHER_STAR).name("&e&lTransferir liderança")
                    .lore("&7Torna &f" + name + " &7o novo líder.", "&7Você passa a ser sub-líder.").build(),
                    (pl, t) -> openConfirm(pl, "&8Transferir liderança",
                            Arrays.asList("&7Transferir a liderança para", "&f" + name + "&7?"),
                            (p2, t2) -> {
                                actions().transferLeader(p2, target);
                                openMain(p2);
                            },
                            (p2, t2) -> openMemberAction(p2, target, rolesMode)));
        }
        if (actions().canKick(mine, theirs, self)) {
            m.set(16, new ItemBuilder(Material.RED_DYE).name("&c&lExpulsar do clã")
                    .lore("&7Remove &f" + name + " &7do clã.").build(),
                    (pl, t) -> openConfirm(pl, "&4Expulsar membro",
                            Arrays.asList("&7Expulsar &f" + name + " &7do clã?"),
                            (p2, t2) -> {
                                actions().kick(p2, target);
                                openMembers(p2, 0, rolesMode);
                            },
                            (p2, t2) -> openMemberAction(p2, target, rolesMode)));
        }
        m.set(22, backItem(), (pl, t) -> openMembers(pl, 0, rolesMode));
        open(p, m);
    }

    // ================================================================== convidar jogadores

    private void openInvitePlayers(Player p, int page) {
        Clan clan = cm().getClanOf(p.getUniqueId());
        if (clan == null) {
            msg().send(p, "no-clan");
            return;
        }
        if (clan.getRole(p.getUniqueId()) == Role.MEMBER) {
            msg().send(p, "only-leader-or-sub");
            return;
        }
        List<Player> candidates = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(p.getUniqueId()) && cm().getClanOf(other.getUniqueId()) == null) {
                candidates.add(other);
            }
        }
        candidates.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        int pages = pages(candidates.size());
        final int pg = Math.max(0, Math.min(page, pages - 1));
        Menu m = new Menu("&8Convidar &8» &b" + clan.getName(), 6);
        decorate(m, 6);
        m.set(4, new ItemBuilder(Material.EMERALD).name("&2&lConvidar jogador")
                .lore("&7Jogadores online sem clã: &f" + candidates.size(),
                        "&7Vagas no clã: &f" + Math.max(0, cm().maxMembers() - clan.size())).build());

        int start = pg * GRID.length;
        for (int i = 0; i < GRID.length && start + i < candidates.size(); i++) {
            final Player target = candidates.get(start + i);
            m.set(GRID[i], new ItemBuilder(Material.PLAYER_HEAD).owner(target)
                    .name("&a&l" + target.getName()).lore("&7Clique para convidar.").build(),
                    (pl, t) -> {
                        actions().invite(pl, target);
                        openInvitePlayers(pl, pg);
                    });
        }
        addNav(m, pg, pages,
                (pl, t) -> openInvitePlayers(pl, pg - 1),
                (pl, t) -> openInvitePlayers(pl, pg + 1),
                (pl, t) -> openMain(pl));
        open(p, m);
    }

    // ================================================================== convites recebidos

    private void openInvites(Player p) {
        List<InviteManager.Invite> list = plugin.getInviteManager().get(p.getUniqueId());
        Menu m = new Menu("&8Convites recebidos", 5);
        decorate(m, 5);
        m.set(4, new ItemBuilder(Material.PAPER).name("&e&lConvites recebidos")
                .lore("&7Pendentes: &f" + list.size(), "", "&aClique esquerdo: &7aceitar", "&cClique direito: &7recusar").build());
        int slots = Math.min(list.size(), GRID.length);
        for (int i = 0; i < slots; i++) {
            final InviteManager.Invite inv = list.get(i);
            Clan c = cm().getClan(inv.clanName);
            if (c == null) {
                continue;
            }
            long secs = Math.max(0, (inv.expiresAt - System.currentTimeMillis()) / 1000L);
            m.set(GRID[i], new ItemBuilder(Material.BOOK).name("&b&l" + c.getName()).lore(
                    "&7Tag: " + tagPreview(c),
                    "&7Convidado por: &f" + inv.inviterName,
                    "&7Membros: &f" + c.size() + "&7/&f" + cm().maxMembers(),
                    "&7Expira em: &f" + secs + "s",
                    "",
                    "&aClique esquerdo: aceitar",
                    "&cClique direito: recusar").build(),
                    (pl, type) -> {
                        if (type.isRightClick()) {
                            actions().decline(pl, inv.clanName);
                            openInvites(pl);
                        } else {
                            actions().accept(pl, inv.clanName);
                            openMain(pl);
                        }
                    });
        }
        m.set(40, backItem(), (pl, t) -> openMain(pl));
        open(p, m);
    }

    // ================================================================== lista de cla

    private void openClanList(Player p, int page) {
        List<Clan> all = cm().getClans();
        all.sort((a, b) -> {
            int c = Integer.compare(b.size(), a.size());
            return c != 0 ? c : a.getName().compareToIgnoreCase(b.getName());
        });
        int pages = pages(all.size());
        final int pg = Math.max(0, Math.min(page, pages - 1));
        Clan mine = cm().getClanOf(p.getUniqueId());

        Menu m = new Menu("&8Lista de clãs", 6);
        decorate(m, 6);
        m.set(4, new ItemBuilder(Material.COMPASS).name("&b&lClãs do servidor")
                .lore("&7Total: &f" + all.size()).build());
        int start = pg * GRID.length;
        for (int i = 0; i < GRID.length && start + i < all.size(); i++) {
            Clan c = all.get(start + i);
            UUID leader = c.getLeader();
            List<String> lore = new ArrayList<>();
            lore.add("&7Tag: " + tagPreview(c));
            lore.add("&7Líder: &f" + (leader == null ? "?" : c.getMemberName(leader)));
            lore.add("&7Membros: &f" + c.size() + "&7/&f" + cm().maxMembers());
            lore.add("&7Criado em: &f" + DATE.format(Instant.ofEpochMilli(c.getCreatedAt())));
            if (c == mine) {
                lore.add("");
                lore.add("&aEste é o seu clã.");
            }
            m.set(GRID[i], new ItemBuilder(Material.BOOK).name("&b&l" + c.getName()).lore(lore).build());
        }
        addNav(m, pg, pages,
                (pl, t) -> openClanList(pl, pg - 1),
                (pl, t) -> openClanList(pl, pg + 1),
                (pl, t) -> openMain(pl));
        open(p, m);
    }

    // ================================================================== tag

    private void openTag(Player p) {
        Clan clan = cm().getClanOf(p.getUniqueId());
        if (clan == null) {
            msg().send(p, "no-clan");
            return;
        }
        if (clan.getRole(p.getUniqueId()) != Role.LEADER) {
            msg().send(p, "only-leader");
            return;
        }
        Menu m = new Menu("&8Tag do clã", 4);
        decorate(m, 4);
        m.set(4, new ItemBuilder(Material.NAME_TAG).name("&d&lTag atual")
                .lore("&7Prévia: " + tagPreview(clan),
                        "&7Tamanho: &f" + cm().tagMin() + "&7 a &f" + cm().tagMax() + " &7caracteres",
                        "",
                        "&eClique para alterar o texto",
                        "&eda tag (digite no chat).").build(),
                (pl, t) -> startPrompt(pl, PromptType.TAG));

        int[] slots = {9 + 1, 9 + 2, 9 + 3, 9 + 4, 9 + 5, 9 + 6, 9 + 7, 9 + 8,
                18 + 1, 18 + 2, 18 + 3, 18 + 4, 18 + 5, 18 + 6, 18 + 7, 18 + 8};
        for (int i = 0; i < COLORS.length; i++) {
            final char code = (Character) COLORS[i][0];
            Material mat = (Material) COLORS[i][1];
            String colorName = (String) COLORS[i][2];
            boolean current = clan.getTagColor() == code;
            m.set(slots[i], new ItemBuilder(mat).name("&" + code + "&l" + colorName)
                    .lore("&7Prévia: &" + code + clan.getTag(), "", current ? "&aCor atual." : "&eClique para usar esta cor.").build(),
                    (pl, t) -> {
                        actions().setTagColor(pl, code);
                        openTag(pl);
                    });
        }
        m.set(31, backItem(), (pl, t) -> openMain(pl));
        open(p, m);
    }
}
