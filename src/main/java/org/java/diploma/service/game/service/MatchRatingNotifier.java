package org.java.diploma.service.game.service;

/**
 * Notifies auth when a ranked 1v1 match ends so player ratings can be adjusted (fire-and-forget after DB commit).
 */
public interface MatchRatingNotifier {

    void notifyMatchFinished(long winnerUserId, long loserUserId);
}
