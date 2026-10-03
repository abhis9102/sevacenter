package app.sevacenter.user;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A user's access to one module (ADR 0021). FULL means "whatever the role allows" and is never
 * stored; VIEW and NONE narrow it. Nothing here can grant more than the role.
 */
public enum ModuleAccess {
    FULL, VIEW, NONE;

    /** Parses the stored form ('DEVOTEES:VIEW,DONATIONS:NONE'); null or blank = no limits. */
    public static Map<StaffModule, ModuleAccess> parse(String stored) {
        Map<StaffModule, ModuleAccess> limits = new EnumMap<>(StaffModule.class);
        if (stored == null || stored.isBlank()) {
            return limits;
        }
        for (String part : stored.split(",")) {
            String[] kv = part.split(":");
            limits.put(StaffModule.valueOf(kv[0]), ModuleAccess.valueOf(kv[1]));
        }
        return limits;
    }

    /** The canonical stored form: FULL entries dropped, modules in enum order; null when empty. */
    public static String format(Map<StaffModule, ModuleAccess> limits) {
        Map<StaffModule, ModuleAccess> ordered = new EnumMap<>(StaffModule.class);
        ordered.putAll(limits);
        String s = ordered.entrySet().stream()
                .filter(e -> e.getValue() != FULL)
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
        return s.isEmpty() ? null : s;
    }
}
