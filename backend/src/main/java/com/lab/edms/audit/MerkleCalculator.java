package com.lab.edms.audit;

import java.util.ArrayList;
import java.util.List;

import static com.lab.edms.audit.HashChainSerializer.sha256Hex;

/**
 * RFC 6962-style Merkle root over a list of hex SHA-256 strings.
 *
 * Empty input → SHA-256("EMPTY") so every day produces an anchor even on idle days.
 * Odd node at any level → last node is promoted (NOT duplicated — RFC 6962 §2.1).
 * Internal nodes: SHA-256(left_hex_utf8 || right_hex_utf8) — hex concat is human-debuggable.
 */
public final class MerkleCalculator {

    public static final String EMPTY_HASH          = sha256Hex("EMPTY");
    public static final String ANCHOR_GENESIS_HASH = sha256Hex("ANCHOR_GENESIS");

    private MerkleCalculator() {}

    public static String root(List<String> leaves) {
        if (leaves == null || leaves.isEmpty()) return EMPTY_HASH;
        List<String> level = new ArrayList<>(leaves);
        while (level.size() > 1) {
            List<String> next = new ArrayList<>((level.size() / 2) + 1);
            for (int i = 0; i < level.size(); i += 2) {
                if (i + 1 < level.size()) {
                    next.add(sha256Hex(level.get(i) + level.get(i + 1)));
                } else {
                    next.add(level.get(i));
                }
            }
            level = next;
        }
        return level.get(0);
    }
}
