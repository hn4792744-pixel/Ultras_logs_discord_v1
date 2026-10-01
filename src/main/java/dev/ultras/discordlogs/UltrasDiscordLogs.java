package dev.ultras.discordlogs;

import dev.ultras.discordlogs.api.UltrasApi;
import dev.ultras.discordlogs.command.UltrasCommand;
import dev.ultras.discordlogs.config.ConfigManager;
import dev.ultras.discordlogs.config.Settings;
import dev.ultras.discordlogs.config.ValidationReport;
import dev.ultras.discordlogs.discord.connection.DiscordConnectionManager;
import dev.ultras.discordlogs.discord.queue.DiscordLogQueue;
import dev.ultras.discordlogs.gui.GuiManager;
import dev.ultras.discordlogs.hooks.HookManager;
import dev.ultras.discordlogs.listener.ApiListener;
import dev.ultras.discordlogs.listener.ChatListener;
import dev.ultras.discordlogs.listener.CombatListener;
import dev.ultras.discordlogs.listener.CommandListener;
import dev.ultras.discordlogs.listener.CreativeListener;
import dev.ultras.discordlogs.listener.GameplayListener;
import dev.ultras.discordlogs.listener.GamemodeListener;
import dev.ultras.discordlogs.listener.MiscListeners;
import dev.ultras.discordlogs.listener.PlayerSessionListener;
import dev.ultras.discordlogs.listener.TeleportListener;
import dev.ultras.discordlogs.logs.BuiltinLogs;
import dev.ultras.discordlogs.logs.LogContext;
import dev.ultras.discordlogs.logs.LogRegistry;
import dev.ultras.discordlogs.logs.LogService;
import dev.ultras.discordlogs.message.MessageService;
import dev.ultras.discordlogs.service.CommandTracker;
import dev.ultras.discordlogs.service.ConfirmationService;
import dev.ultras.discordlogs.service.IpMismatchService;
import dev.ultras.discordlogs.service.PunishmentService;
import dev.ultras.discordlogs.service.SoundService;
import dev.ultras.discordlogs.service.StatsService;
import dev.ultras.discordlogs.service.TestService;
import dev.ultras.discordlogs.storage.CounterStore;
import dev.ultras.discordlogs.storage.PlayerDataStore;
import dev.ultras.discordlogs.storage.StorageManager;
import dev.ultras.discordlogs.storage.YamlStorageBackend;
import dev.ultras.discordlogs.util.Clock;
import dev.ultras.discordlogs.util.PluginLog;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

public final class UltrasDiscordLogs extends JavaPlugin {
    private PluginLog log;
    private ConfigManager config;
    private volatile Clock clock;
    private MessageService messages;
    private LogRegistry registry;
    private SoundService sounds;
    private StorageManager storage;
    private DiscordConnectionManager connections;
    private DiscordLogQueue queue;
    private HookManager hooks;
    private LogService logs;
    private CommandTracker commands;
    private PunishmentService punishments;
    private ConfirmationService confirmations;
    private IpMismatchService ipMismatch;
    private StatsService stats;
    private TestService tests;
    private GuiManager gui;
    private UltrasApi api;
    private ChatListener chatListener;
    private boolean enabledOk;

    @Override
    public void onEnable() {
        log = new PluginLog(getLogger());
        try {
            registry = new LogRegistry();
            BuiltinLogs.registerAll(registry);
            sounds = new SoundService(this);
            config = new ConfigManager(this);
            ValidationReport report = config.load();
            Settings s = config.settings();
            clock = new Clock(s.zone, s.dateFormat, s.timeFormat);
            messages = new MessageService(this);

            storage = new StorageManager(this, new YamlStorageBackend(getDataFolder().toPath()));
            storage.init();

            connections = new DiscordConnectionManager(log, s.requestTimeoutMs);
            queue = new DiscordLogQueue(log, tuning(s));
            connections.load(config.discord(), config.defaultConnectionId(), config.botConfig(), report);

            hooks = new HookManager(this);
            hooks.init();
            logs = new LogService(this);
            commands = new CommandTracker();
            punishments = new PunishmentService(this);
            confirmations = new ConfirmationService(this);
            ipMismatch = new IpMismatchService(this);
            stats = new StatsService(this);
            tests = new TestService(this);
            gui = new GuiManager(this);
            api = new UltrasApi(this);
            getServer().getServicesManager().register(UltrasApi.class, api, this, ServicePriority.Normal);

            registerListeners();
            PluginCommand cmd = getCommand("uc_discord_logs");
            if (cmd != null) { UltrasCommand c = new UltrasCommand(this); cmd.setExecutor(c); cmd.setTabCompleter(c); }

            Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> storage.saveDataAsync(false), 1200L, 1200L);
            stats.start();
            if (s.startupValidation) connections.validateAllAsync();
            printReport(report);
            enabledOk = true;
            log.info("ULTRAS_Discord_logs_v1 enabled: " + registry.all().size() + " logs, " + connections.all().size()
                    + " connections (webhook-first, bot optional: " + (connections.hasBotConnections() ? "in use" : "not used") + ").");
        } catch (Throwable t) {
            log.error("ULTRAS_Discord_logs_v1 failed to start; disabling itself so the server is not affected", t);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private static DiscordLogQueue.Tuning tuning(Settings s) {
        return new DiscordLogQueue.Tuning(s.queueCapacity, s.maxRetries, s.baseBackoffMs, s.maxBackoffMs, s.minIntervalMs);
    }

    private void safeRegister(String name, Supplier<Listener> factory) {
        try {
            getServer().getPluginManager().registerEvents(factory.get(), this);
        } catch (Throwable t) {
            log.warn("Listener '" + name + "' is not available on this server version and was skipped", t);
        }
    }

    private void registerListeners() {
        chatListener = new ChatListener(this);
        safeRegister("session", () -> new PlayerSessionListener(this));
        safeRegister("creative", () -> new CreativeListener(this));
        safeRegister("teleport", () -> new TeleportListener(this));
        safeRegister("combat", () -> new CombatListener(this));
        safeRegister("gamemode", () -> new GamemodeListener(this));
        safeRegister("chat", () -> chatListener);
        safeRegister("commands", () -> new CommandListener(this));
        safeRegister("misc", () -> new MiscListeners(this));
        safeRegister("gamerule", () -> new MiscListeners.GameRuleListener(this));
        safeRegister("gameplay", () -> new GameplayListener(this));
        safeRegister("api", () -> new ApiListener(this));
        safeRegister("gui", () -> gui);
    }

    private void printReport(ValidationReport r) {
        for (ValidationReport.Issue i : r.issues()) {
            if (i.level() == ValidationReport.Level.INFO) log.info(i.message()); else log.warn(i.message());
        }
    }

    /** Reloads all YAML safely. Returns the validation report; a broken file keeps the previous configuration. */
    public ValidationReport reload() {
        ValidationReport r = config.load();
        Settings s = config.settings();
        clock = new Clock(s.zone, s.dateFormat, s.timeFormat);
        connections.load(config.discord(), config.defaultConnectionId(), config.botConfig(), r);
        queue.tune(tuning(s));
        queue.retainLanes(connections.laneKeys());
        logs.invalidateCache();
        chatListener.reload();
        stats.start();
        if (s.startupValidation) connections.validateAllAsync();
        return r;
    }

    public void reloadQuiet() {
        try { reload(); } catch (Throwable t) { log.warn("Reload failed", t); }
    }

    public void reloadAndReport(CommandSender to) {
        try {
            ValidationReport r = reload();
            printReport(r);
            messages.send(to, "system.reloaded", "errors", String.valueOf(r.count(ValidationReport.Level.ERROR)),
                    "warnings", String.valueOf(r.count(ValidationReport.Level.WARN)));
            int shown = 0;
            for (ValidationReport.Issue i : r.issues()) {
                if (i.level() == ValidationReport.Level.INFO || shown++ >= 5) continue;
                messages.send(to, "system.reload-issue", "message", i.message());
            }
        } catch (Throwable t) {
            log.error("Reload failed", t);
            messages.send(to, "system.reload-failed");
        }
    }

    public void runMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else if (isEnabled()) Bukkit.getScheduler().runTask(this, r);
    }

    @Override
    public void onDisable() {
        if (!enabledOk) return;
        try {
            if (stats != null) stats.stop();
            Bukkit.getScheduler().cancelTasks(this);
            if (Bukkit.isStopping() && logs != null)
                logs.submit(LogContext.of("server-stop").put("action", "Server stopping").put("player", "Server"));
            if (logs != null) logs.shutdown();
            if (queue != null) queue.shutdown(config.settings().shutdownFlushSeconds);
            if (storage != null) storage.shutdown();
            if (connections != null) connections.shutdown();
            if (hooks != null) hooks.shutdown();
            getServer().getServicesManager().unregisterAll(this);
        } catch (Throwable t) {
            log.error("Error during shutdown", t);
        }
    }

    public PluginLog log() { return log; }
    public ConfigManager config() { return config; }
    public Settings settings() { return config.settings(); }
    public Clock clock() { return clock; }
    public MessageService messages() { return messages; }
    public LogRegistry registry() { return registry; }
    public SoundService sounds() { return sounds; }
    public StorageManager storage() { return storage; }
    public PlayerDataStore players() { return storage.players(); }
    public CounterStore counters() { return storage.counters(); }
    public DiscordConnectionManager connections() { return connections; }
    public DiscordLogQueue queue() { return queue; }
    public HookManager hooks() { return hooks; }
    public LogService logs() { return logs; }
    public CommandTracker commands() { return commands; }
    public PunishmentService punishments() { return punishments; }
    public ConfirmationService confirmations() { return confirmations; }
    public IpMismatchService ipMismatch() { return ipMismatch; }
    public StatsService stats() { return stats; }
    public TestService tests() { return tests; }
    public GuiManager gui() { return gui; }
    public UltrasApi api() { return api; }
}
