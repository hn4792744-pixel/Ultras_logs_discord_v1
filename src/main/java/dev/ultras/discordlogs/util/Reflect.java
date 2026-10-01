package dev.ultras.discordlogs.util;

import java.lang.reflect.Method;

/** Failure-tolerant reflection helper used for optional plugin hooks (no compile-time dependency). */
public final class Reflect {
    private Reflect() {}

    public static Object call(Object target, String method, Object... args) {
        if (target == null) return null;
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!m.getName().equals(method) || m.getParameterCount() != args.length) continue;
                try { m.setAccessible(true); } catch (Throwable ignored) { /* public method may still work */ }
                return m.invoke(target, args);
            }
        } catch (Throwable ignored) {
            // optional data: treat as unavailable
        }
        return null;
    }

    public static Object callStatic(String className, String method, Object... args) {
        try {
            Class<?> c = Class.forName(className);
            for (Method m : c.getMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == args.length) return m.invoke(null, args);
            }
        } catch (Throwable ignored) {
            // unavailable
        }
        return null;
    }

    public static String nameOf(Object o) {
        if (o == null) return "Unknown";
        for (String n : new String[]{"getName", "getKey", "key", "name"}) {
            Object v = call(o, n);
            if (v != null) return String.valueOf(v);
        }
        return String.valueOf(o);
    }

    public static boolean classExists(String name) {
        try { Class.forName(name); return true; } catch (Throwable t) { return false; }
    }
}
