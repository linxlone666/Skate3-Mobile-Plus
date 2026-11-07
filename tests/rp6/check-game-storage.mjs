// Host regression for the production volume resolver. Requires Node and JDK 17.
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../', import.meta.url));
const source = path.join(root, 'android/app/src/main/java/chat/buku/skate3');
const output = mkdtempSync(path.join(tmpdir(), 'skate3-storage-'));
const files = {
    'android/content/SharedPreferences.java': `package android.content;
public class SharedPreferences {
    private String value;
    public String getString(String key, String fallback) { return value == null ? fallback : value; }
    public SharedPreferences edit() { return this; }
    public SharedPreferences putString(String key, String value) { this.value = value; return this; }
    public void apply() {}
}`,
    'android/content/Context.java': `package android.content;
import java.io.File;
import android.os.storage.StorageManager;
public class Context {
    public static final int MODE_PRIVATE = 0;
    public File[] directories;
    public File internal;
    public final StorageManager manager = new StorageManager();
    private final SharedPreferences preferences = new SharedPreferences();
    public File[] getExternalFilesDirs(String type) { return directories; }
    public File getFilesDir() { return internal; }
    public <T> T getSystemService(Class<T> type) { return type.cast(manager); }
    public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
}`,
    'android/os/Environment.java': `package android.os;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
public class Environment {
    public static final String MEDIA_MOUNTED = "mounted";
    public static final Set<File> removable = new HashSet<>();
    public static final Set<File> unmounted = new HashSet<>();
    public static boolean isExternalStorageRemovable(File file) { return removable.contains(file); }
    public static String getExternalStorageState(File file) {
        return unmounted.contains(file) ? "mounted_ro" : MEDIA_MOUNTED;
    }
}`,
    'android/os/storage/StorageVolume.java': `package android.os.storage;
import android.content.Context;
public class StorageVolume {
    private final String uuid;
    public StorageVolume(String uuid) { this.uuid = uuid; }
    public boolean isPrimary() { return false; }
    public String getUuid() { return uuid; }
    public String getDescription(Context context) { return "SD card"; }
}`,
    'android/os/storage/StorageManager.java': `package android.os.storage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
public class StorageManager {
    public final Map<File, StorageVolume> volumes = new HashMap<>();
    public StorageVolume getStorageVolume(File file) { return volumes.get(file); }
}`,
    'chat/buku/skate3/LauncherStrings.java': `package chat.buku.skate3;
import android.content.Context;
class LauncherStrings { static String text(Context context, String text) { return text; } }`,
    'chat/buku/skate3/TitleUpdateInstaller.java': `package chat.buku.skate3;
import java.nio.file.*;
class TitleUpdateInstaller {
    static boolean isInstalled(Path root) { return Files.isRegularFile(root.resolve("tu-ready")); }
}`,
    'chat/buku/skate3/GameStorageTest.java': `package chat.buku.skate3;
import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageVolume;
import java.io.File;
import java.nio.file.*;
public class GameStorageTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("storage-volumes");
        Context context = new Context();
        File device = temp.resolve("device").toFile();
        File card = temp.resolve("card").toFile();
        context.internal = temp.resolve("internal").toFile();
        context.directories = new File[]{device, card, null};
        Environment.removable.add(card);
        context.manager.volumes.put(card, new StorageVolume("card-uuid"));
        assert GameStorage.selected(context).directory.equals(device);
        assert GameStorage.usesDeviceStorage(context);
        assert GameStorage.available(context).size() == 2;
        GameStorage.available(context).get(1).select(context);
        assert !GameStorage.usesDeviceStorage(context);
        assert GameStorage.selected(context).directory.equals(card);
        assert GameStorage.launchDirectory(context) == null;
        Path game = card.toPath().resolve("game");
        Files.createDirectories(game.resolve("data/webkit"));
        Files.createFile(game.resolve("default.xex"));
        Files.createFile(game.resolve("data/webkit/EAWebkit.xex"));
        assert !GameStorage.isGameReady(game);
        Files.createFile(game.resolve("tu-ready"));
        assert GameStorage.launchDirectory(context).equals(game.toFile());
        Environment.unmounted.add(card);
        assert GameStorage.available(context).size() == 1;
        assert GameStorage.selected(context) == null;
        assert GameStorage.launchDirectory(context) == null;
        Environment.unmounted.clear();
        context.directories = new File[]{device, null, card};
        assert GameStorage.selected(context).directory.equals(card);
        context.directories = new File[]{device};
        assert GameStorage.selected(context) == null;
        GameStorage.available(context).get(0).select(context);
        assert GameStorage.selected(context).directory.equals(device);
        context.directories = new File[]{null};
        assert GameStorage.selected(context).directory.equals(context.internal);
        System.out.println("PASS: default, card selection, readiness, read-only/removal, reconnect, volume order, internal fallback");
    }
}`,
};
const sources = [path.join(source, 'GameStorage.java')];
for (const [name, content] of Object.entries(files)) {
    const destination = path.join(output, name);
    mkdirSync(path.dirname(destination), { recursive: true });
    writeFileSync(destination, content);
    sources.push(destination);
}
function run(command, args) {
    const executable = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', command) : command;
    const result = spawnSync(executable, args, { encoding: 'utf8' });
    assert.ifError(result.error);
    assert.equal(result.status, 0, result.stdout + result.stderr);
    process.stdout.write(result.stdout);
}
run('javac', ['-encoding', 'UTF-8', '-d', output, ...sources]);
run('java', ['-ea', '-cp', output, 'chat.buku.skate3.GameStorageTest']);
const launcher = readFileSync(path.join(source, 'LauncherActivity.java'), 'utf8');
const activity = readFileSync(path.join(source, 'Skate3Activity.java'), 'utf8');
const native = readFileSync(path.join(root, 'src/skate3_app_common.cpp'), 'utf8');
assert.match(launcher, /storageButton\.setEnabled\(enabled\)/);
assert.match(activity, /GameStorage\.launchDirectory\(this\)/);
assert.match(activity, /nativeSetenv\("SKATE3_GAME_DATA_DIR", gameDirectory\.getAbsolutePath\(\)\)/);
assert.ok(native.indexOf('std::getenv("SKATE3_GAME_DATA_DIR")') < native.indexOf('skate3::IsGameInstalled(legacy_root)'));
console.log('PASS: storage control and Java/native selected-path wiring');
