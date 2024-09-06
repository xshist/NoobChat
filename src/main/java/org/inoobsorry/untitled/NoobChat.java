package org.inoobsorry.untitled;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Plugin(id = "noobchat", name = "NoobChat", version = "3.0", description = "Приватный плагин на личные сообщения для Velocity", authors = {"iNoobSorry"})
class NoobChatPlugin {

    private final ProxyServer server;
    private final Map<UUID, UUID> replyMap; // Хранит последнее полученное сообщение и отправителя

    @Inject
    public NoobChatPlugin(ProxyServer server, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.replyMap = new HashMap<>();
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        registerMessageCommand();
        registerReplyCommand();
    }

    private void registerMessageCommand() {
        CommandManager commandManager = server.getCommandManager();
        commandManager.register("msg", new MessageCommand(), "m");
    }

    private void registerReplyCommand() {
        CommandManager commandManager = server.getCommandManager();
        commandManager.register("reply", new ReplyCommand(server, replyMap), "r");
    }

    private void sendPrivateMessage(CommandSource sender, String targetPlayerName, String message) {
        Optional<Player> targetPlayerOpt = server.getPlayer(targetPlayerName);

        if (targetPlayerOpt.isPresent()) {
            Player targetPlayer = targetPlayerOpt.get();
            String senderName = sender instanceof Player ? ((Player) sender).getUsername() : "Console";

            // Проверка на отправку сообщения самому себе
            if (sender instanceof Player && ((Player) sender).getUniqueId().equals(targetPlayer.getUniqueId())) {
                sender.sendMessage(Component.text("§bЛС §7» §fВы не можете отправить сообщение самому себе."));
                return;
            }

            // Форматируем сообщение для целевого игрока
            String formattedMessageForTarget = "\uD83D\uDCAC §bЛС §8[§f" + senderName + " §7» §aЯ§8]§7 " + message;
            targetPlayer.sendMessage(Component.text(formattedMessageForTarget));

            // Форматируем сообщение для отправителя
            String formattedMessageForSender = "\uD83D\uDCAC §bЛС §8[§aЯ §7» §f" + targetPlayer.getUsername() + "§8]§7 " + message;
            if (sender instanceof Player && !sender.equals(targetPlayer)) {
                sender.sendMessage(Component.text(formattedMessageForSender));
            }

            // Сохраняем последнее полученное сообщение для команды /r (reply) и для отправителя, и для получателя
            if (sender instanceof Player && !sender.equals(targetPlayer)) {
                replyMap.put(targetPlayer.getUniqueId(), ((Player) sender).getUniqueId());
                replyMap.put(((Player) sender).getUniqueId(), targetPlayer.getUniqueId());
            }
        } else {
            sender.sendMessage(Component.text("§bЛС §7» §fИгрок не найден."));
        }
    }

    public class MessageCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (args.length < 2) {
                source.sendMessage(Component.text("§bЛС §7» §fИспользование: /msg <игрок> <сообщение>"));
                return;
            }

            String targetPlayerName = args[0];
            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            NoobChatPlugin.this.sendPrivateMessage(source, targetPlayerName, message);
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            if (invocation.arguments().length == 1) {
                String partialPlayerName = invocation.arguments()[0].toLowerCase();
                return server.getAllPlayers().stream()
                        .filter(player -> player.getUsername().toLowerCase().startsWith(partialPlayerName))
                        .map(Player::getUsername)
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    public class ReplyCommand implements SimpleCommand {

        private final ProxyServer server;
        private final Map<UUID, UUID> replyMap;

        public ReplyCommand(ProxyServer server, Map<UUID, UUID> replyMap) {
            this.server = server;
            this.replyMap = replyMap;
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!(source instanceof Player)) {
                source.sendMessage(Component.text("§bЛС §7» §fТолько игроки могут использовать эту команду."));
                return;
            }

            Player player = (Player) source;
            UUID lastMessageSender = replyMap.get(player.getUniqueId());

            if (lastMessageSender == null) {
                source.sendMessage(Component.text("§bЛС §7» §fВам некому отвечать."));
                return;
            }

            // Проверка на отправку сообщения самому себе через /r
            if (player.getUniqueId().equals(lastMessageSender)) {
                source.sendMessage(Component.text("§bЛС §7» §fВы не можете отправить сообщение самому себе."));
                return;
            }

            Optional<Player> targetPlayerOpt = server.getPlayer(lastMessageSender);

            if (!targetPlayerOpt.isPresent()) {
                source.sendMessage(Component.text("§bЛС §7» §fИгрока, которому вы отвечали, больше нет в сети."));
                return;
            }

            Player targetPlayer = targetPlayerOpt.get();
            String message = String.join(" ", args);

            NoobChatPlugin.this.sendPrivateMessage(player, targetPlayer.getUsername(), message);
        }
    }
}
