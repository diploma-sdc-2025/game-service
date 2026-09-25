package org.java.diploma.service.game.bot;

/**
 * Reserved player id for the server-controlled opponent. Same value as matchmaking-service.
 */
public final class BotPlayers {

    public static final long USER_ID = 1_000_001L;
    public static final String USERNAME = "Mira";

    private BotPlayers() {}

    public static boolean isBot(long userId) {
        return userId == USER_ID;
    }
}
