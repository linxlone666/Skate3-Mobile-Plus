package chat.buku.skate3;

import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Resolves a saved volume against Android's current app-specific directories. */
final class GameStorage {
    private static final String PREFERENCES = "game-storage";
    private static final String VOLUME = "volume";
    private static final String PRIMARY = "primary";

    final String id;
    final String label;
    final File directory;

    private GameStorage(String id, String label, File directory) {
        this.id = id;
        this.label = label;
        this.directory = directory;
    }

    static List<GameStorage> available(Context context) {
        List<GameStorage> locations = new ArrayList<>();
        File[] directories = context.getExternalFilesDirs(null);
        File primary = directories.length > 0 ? directories[0] : null;
        if (primary == null) primary = context.getFilesDir();
        locations.add(new GameStorage(PRIMARY,
            LauncherStrings.text(context, "Device storage"), primary));
        StorageManager manager = context.getSystemService(StorageManager.class);
        for (File directory : directories) {
            if (directory == null || !Environment.isExternalStorageRemovable(directory) ||
                !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(directory))) {
                continue;
            }
            StorageVolume volume = manager.getStorageVolume(directory);
            // UUIDs survive restarts and changes in the order of mounted volumes.
            if (volume != null && !volume.isPrimary() && volume.getUuid() != null) {
                locations.add(new GameStorage(volume.getUuid(),
                    volume.getDescription(context), directory));
            }
        }
        return locations;
    }

    static GameStorage selected(Context context) {
        String id = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(VOLUME, PRIMARY);
        for (GameStorage location : available(context)) {
            if (location.id.equals(id)) return location;
        }
        // Preserve a missing card's selection so setup cannot silently use the device.
        return null;
    }

    void select(Context context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(VOLUME, id).apply();
    }

    static boolean usesDeviceStorage(Context context) {
        return PRIMARY.equals(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(VOLUME, PRIMARY));
    }

    static boolean isGameReady(Path root) {
        return Files.isRegularFile(root.resolve("default.xex")) &&
            Files.isRegularFile(root.resolve("data/webkit/EAWebkit.xex")) &&
            TitleUpdateInstaller.isInstalled(root);
    }

    static File launchDirectory(Context context) {
        GameStorage location = selected(context);
        if (location == null) return null;
        File game = new File(location.directory, "game");
        if (isGameReady(game.toPath())) return game;
        File legacy = new File("/storage/emulated/0/skate3");
        return usesDeviceStorage(context) && isGameReady(legacy.toPath()) ? legacy : null;
    }
}
