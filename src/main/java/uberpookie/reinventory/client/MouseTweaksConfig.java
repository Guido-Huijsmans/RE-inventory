package uberpookie.reinventory.client;

/**
 * Simple holder for Mouse Tweaks style options.
 * This is intentionally small and mutable so a config screen/file
 * can populate these values later.
 */
public final class MouseTweaksConfig {
    public boolean rmbTweak = true;
    public boolean lmbTweakWithItem = true;
    public boolean lmbTweakWithoutItem = true;
    public boolean wheelTweak = true;

    /**
     * Search order when moving with the wheel.
     * true = search from last slot to first (Mouse Tweaks default)
     * false = search from first to last.
     */
    public boolean wheelSearchFromLast = true;

    /**
     * Scroll direction behaviour:
     * 0 = default (scroll down moves out, up moves in)
     * 1 = inverted
     * 2 = inventory-position aware (scroll toward the other inventory).
     */
    public int wheelScrollDirectionMode = 0;

    // Stub hooks for future persistence
    public void loadFromDisk() {
        // TODO: add config file parsing
    }

    public void saveToDisk() {
        // TODO: add config file writing
    }
}
