package com.lab.edms.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.lab.edms.audit.HashChainSerializer.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;

class MerkleCalculatorTest {

    @Test
    void emptyList_returnsEmptyConstant() {
        assertThat(MerkleCalculator.root(List.of())).isEqualTo(sha256Hex("EMPTY"));
    }

    @Test
    void singleLeaf_returnsItself() {
        String leaf = "a".repeat(64);
        assertThat(MerkleCalculator.root(List.of(leaf))).isEqualTo(leaf);
    }

    @Test
    void twoLeaves_concatHexUtf8Sha256() {
        String l1 = "a".repeat(64);
        String l2 = "b".repeat(64);
        assertThat(MerkleCalculator.root(List.of(l1, l2))).isEqualTo(sha256Hex(l1 + l2));
    }

    /** 3건: 홀수 promote (RFC 6962) — duplication 아님 */
    @Test
    void threeLeaves_promoteOddNode() {
        String l1 = "a".repeat(64);
        String l2 = "b".repeat(64);
        String l3 = "c".repeat(64);
        // level 1: [sha(l1+l2), l3] ← l3 promote
        // level 2: sha(sha(l1+l2) + l3)
        String expected = sha256Hex(sha256Hex(l1 + l2) + l3);
        assertThat(MerkleCalculator.root(List.of(l1, l2, l3))).isEqualTo(expected);
    }

    @Test
    void fourLeaves_balancedTree() {
        String l1 = "a".repeat(64);
        String l2 = "b".repeat(64);
        String l3 = "c".repeat(64);
        String l4 = "d".repeat(64);
        String expected = sha256Hex(sha256Hex(l1 + l2) + sha256Hex(l3 + l4));
        assertThat(MerkleCalculator.root(List.of(l1, l2, l3, l4))).isEqualTo(expected);
    }

    @Test
    void deterministic() {
        List<String> leaves = List.of("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64));
        assertThat(MerkleCalculator.root(leaves)).isEqualTo(MerkleCalculator.root(leaves));
    }
}
