package com.abyss.sigils.sigils;

/**
 * A sigil is either MINOR (fits in small sockets) or MAJOR (fits in big sockets).
 * Some stats are exclusive to MAJOR; minors only ever get common stats.
 */
public enum SigilRank { MINOR, MAJOR, GRAND }
